package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.payment.VNPayClient;
import com.datvexe.payment.VNPayConfig;
import com.datvexe.security.AccountException;
import com.datvexe.service.DomainException;
import com.datvexe.service.ThanhToanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP handling only; gateway verification and issuance rules live outside the servlet. */
@WebServlet("/api/thanh-toan/*")
public class ThanhToanController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private ThanhToanService service;
    private VNPayClient gateway;

    @Override public void init() {
        gateway = new VNPayClient(VNPayConfig.fromEnvironment());
        service = new ThanhToanService(Jpa.factory());
    }

    @Override protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Route route = route(request);
        String allow = route.kind == Kind.VERIFY ? "POST, OPTIONS" : "GET, OPTIONS";
        if (request.getMethod().equals("OPTIONS")) {
            response.setHeader("Allow", allow);
            response.setStatus(204);
            return;
        }
        if (!(route.kind == Kind.VERIFY ? request.getMethod().equals("POST") : request.getMethod().equals("GET"))) {
            response.setHeader("Allow", allow);
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }
        if (route.kind == Kind.IPN) {
            receiveIpn(request, response);
            return;
        }
        if (route.kind == Kind.RETURN) {
            var verified = gateway.verifyCallback(parameters(request));
            // A browser redirect is never the authority for a database transition.
            writeJson(response, Map.of("maGiaoDich", verified.reference(), "chuKyHopLe", true,
                    "responseCode", verified.responseCode(), "transactionStatus", verified.transactionStatus(),
                    "message", "Đã nhận kết quả trả về. Gọi API xác minh để đọc kết quả thanh toán chính thức."));
            return;
        }
        if (!request.getParameterMap().isEmpty()) throw AccountException.invalid("query", "API không nhận query.");
        if (route.kind == Kind.VERIFY) {
            com.fasterxml.jackson.databind.JsonNode body;
            try { body = json.readTree(request.getInputStream()); }
            catch (JsonProcessingException error) { throw AccountException.invalid("body", "JSON không hợp lệ."); }
            if (body != null && (!body.isObject() || !body.isEmpty()))
                throw AccountException.invalid("body", "API xác minh chỉ nhận body rỗng hoặc {}.");
            writeJson(response, service.verify(route.id, request.getRemoteAddr()));
        } else {
            writeJson(response, service.get(route.id));
        }
    }

    private void receiveIpn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var result = service.receive(gateway.verifyCallback(parameters(request)));
            if (result.payment().trangThai() == com.datvexe.domain.States.TrangThaiThanhToan.CHO_XU_LY) {
                writeJson(response, Map.of("RspCode", "99", "Message", "Payment result is pending"));
                return;
            }
            writeJson(response, Map.of("RspCode", result.duplicate() ? "02" : "00",
                    "Message", result.duplicate() ? "Order already confirmed" : "Confirm Success"));
        } catch (AccountException error) {
            String code = switch (error.code()) {
                case "INVALID_SIGNATURE", "INVALID_MERCHANT", "VALIDATION_ERROR" -> "97";
                case "RESOURCE_NOT_FOUND" -> "01";
                case "PAYMENT_AMOUNT_MISMATCH" -> "04";
                default -> "99";
            };
            writeJson(response, Map.of("RspCode", code, "Message", "Cannot confirm payment"));
        } catch (RuntimeException error) {
            request.getServletContext().log("VNPay IPN could not be committed.", error);
            writeJson(response, Map.of("RspCode", "99", "Message", "Temporary error"));
        }
    }

    private Map<String, String> parameters(HttpServletRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values == null || values.length != 1)
                throw AccountException.invalid("query", "Tham số cổng thanh toán không được lặp.");
            result.put(name, values[0]);
        });
        return result;
    }

    private Route route(HttpServletRequest request) {
        String info = request.getPathInfo();
        if ("/return/vnpay".equals(info)) return new Route(Kind.RETURN, null);
        if ("/callback/vnpay".equals(info)) return new Route(Kind.IPN, null);
        String[] parts = info == null ? new String[0] : info.substring(1).split("/", -1);
        if (parts.length >= 1 && parts[0].matches("[A-Za-z0-9_-]{1,80}")) {
            if (parts.length == 1) return new Route(Kind.DETAIL, parts[0]);
            if (parts.length == 2 && parts[1].equals("xac-minh")) return new Route(Kind.VERIFY, parts[0]);
        }
        throw new DomainException(404, "API không tồn tại.");
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), body);
    }

    private enum Kind { DETAIL, VERIFY, RETURN, IPN }
    private record Route(Kind kind, String id) {}
}
