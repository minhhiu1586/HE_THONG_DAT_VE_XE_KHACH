package com.datvexe.controller;

import com.datvexe.domain.States.TrangThaiThanhToan;
import com.datvexe.dto.ThanhToanDto;
import com.datvexe.payment.VNPayClient;
import com.datvexe.payment.VNPayConfig;
import com.datvexe.security.AccountException;
import com.datvexe.service.DomainException;
import com.datvexe.service.ThanhToanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @Override
    public void init() {
        gateway = new VNPayClient(VNPayConfig.fromEnvironment());
        service = new ThanhToanService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] path = loaiPath(request);
        if ("/callback/vnpay".equals(request.getPathInfo())) {
            receiveIpn(request, response);
            return;
        }
        if ("/return/vnpay".equals(request.getPathInfo())) {
            var verified = gateway.verifyCallback(parameters(request));
            // ReturnURL chỉ trả thông tin, không cập nhật trạng thái giao dịch.
            writeJson(response, new ThanhToanDto.ReturnResponse(verified.reference(), true,
                    verified.responseCode(), verified.transactionStatus(),
                    "Đã nhận kết quả trả về. Gọi API xác minh để đọc kết quả thanh toán chính thức."));
            return;
        }
        if (path.length == 2) {
            response.setHeader("Allow", "POST, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }
        if (!request.getParameterMap().isEmpty()) {
            throw new AccountException(400, "VALIDATION_ERROR", "API không nhận query.");
        }
        writeJson(response, service.get(path[0]));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] path = loaiPath(request);
        if (path.length != 2 || !"xac-minh".equals(path[1])) {
            response.setHeader("Allow", "GET, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }
        if (!request.getParameterMap().isEmpty()) {
            throw new AccountException(400, "VALIDATION_ERROR", "API không nhận query.");
        }
        readBody(request);
        writeJson(response, service.verify(path[0], request.getRemoteAddr()));
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) {
        String[] path = loaiPath(request);
        response.setHeader("Allow", path.length == 2 && "xac-minh".equals(path[1])
                ? "POST, OPTIONS" : "GET, OPTIONS");
        throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        String[] path = loaiPath(request);
        response.setHeader("Allow", path.length == 2 && "xac-minh".equals(path[1])
                ? "POST, OPTIONS" : "GET, OPTIONS");
        response.setStatus(204);
    }

    private void readBody(HttpServletRequest request) throws IOException {
        JsonNode body;
        try {
            body = json.readTree(request.getInputStream());
        } catch (JsonProcessingException error) {
            throw new AccountException(400, "VALIDATION_ERROR", "JSON không hợp lệ.");
        }
        if (body != null && (!body.isObject() || !body.isEmpty())) {
            throw new AccountException(
                    400, "VALIDATION_ERROR", "API xác minh chỉ nhận body rỗng hoặc {}.");
        }
    }

    private void receiveIpn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var result = service.receive(gateway.verifyCallback(parameters(request)));
            if (result.payment().trangThai() == TrangThaiThanhToan.CHO_XU_LY) {
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
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length != 1) {
                throw new AccountException(400, "VALIDATION_ERROR", "Tham số cổng thanh toán không được lặp.");
            }
            result.put(entry.getKey(), values[0]);
        }
        return result;
    }

    private String[] loaiPath(HttpServletRequest request) {
        String info = request.getPathInfo();
        if ("/return/vnpay".equals(info) || "/callback/vnpay".equals(info)) {
            return info.substring(1).split("/");
        }
        if (info != null && !info.isEmpty()) {
            String[] parts = info.substring(1).split("/", -1);
            if (parts[0].matches("[A-Za-z0-9_-]{1,80}")) {
                if (parts.length == 1) {
                    return parts;
                }
                if (parts.length == 2 && "xac-minh".equals(parts[1])) {
                    return parts;
                }
            }
        }
        throw new DomainException(404, "API không tồn tại.");
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), body);
    }

}
