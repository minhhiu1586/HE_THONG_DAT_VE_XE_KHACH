package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiChuyen;
import com.datvexe.dto.Inputs;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

@WebServlet("/api/*")
public class RouteTripController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private RouteTripService service;

    @Override public void init() { service = new RouteTripService(Jpa.factory()); }

    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        if ("http://localhost:3000".equals(request.getHeader("Origin"))) {
            response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        }
        if ("OPTIONS".equals(request.getMethod())) { response.setStatus(204); return; }
        try {
            String[] p = (request.getPathInfo() == null ? "" : request.getPathInfo()).replaceFirst("^/", "").split("/");
            String method = request.getMethod();
            Object result;
            int status = 200;
            if (p.length == 1 && p[0].equals("tinh-thanh")) {
                if (method.equals("GET")) result = service.listTinhThanh();
                else if (method.equals("POST")) { result = service.createTinhThanh(body(request, Inputs.TinhThanh.class)); status = 201; }
                else throw new DomainException(405, "Phương thức không được hỗ trợ");
            } else if (p[0].equals("ben-xe")) {
                if (p.length == 1 && method.equals("GET")) result = service.listBenXe();
                else if (p.length == 1 && method.equals("POST")) { result = service.createBenXe(body(request, Inputs.BenXe.class)); status = 201; }
                else if (p.length == 2 && method.equals("GET")) result = service.getBenXe(p[1]);
                else if (p.length == 2 && method.equals("PUT")) result = service.updateBenXe(p[1], body(request, Inputs.BenXe.class));
                else if (p.length == 2 && method.equals("DELETE")) { service.deleteBenXe(p[1]); result = null; status = 204; }
                else throw new DomainException(404, "API không tồn tại");
            } else if (p[0].equals("tuyen-xe")) {
                if (p.length == 1 && method.equals("GET")) result = service.listTuyenXe();
                else if (p.length == 1 && method.equals("POST")) { result = service.createTuyenXe(body(request, Inputs.TuyenXe.class)); status = 201; }
                else if (p.length == 2 && method.equals("GET")) result = service.getTuyenXe(p[1]);
                else if (p.length == 2 && method.equals("PUT")) result = service.updateTuyenXe(p[1], body(request, Inputs.TuyenXe.class));
                else if (p.length == 2 && method.equals("DELETE")) { service.deleteTuyenXe(p[1]); result = null; status = 204; }
                else if (p.length == 3 && p[2].equals("tram-dau-cuoi") && method.equals("PUT"))
                    result = service.setTramDauCuoi(p[1], body(request, Inputs.TramDauCuoi.class));
                else if (p.length == 3 && p[2].equals("diem-dung") && method.equals("POST")) {
                    result = service.addDiemDung(p[1], body(request, Inputs.DiemDung.class)); status = 201;
                } else if (p.length == 4 && p[2].equals("diem-dung") && p[3].equals("sap-xep") && method.equals("PUT"))
                    result = service.reorderDiemDung(p[1], body(request, Inputs.SapXepDiemDung.class));
                else if (p.length == 4 && p[2].equals("diem-dung") && method.equals("DELETE")) {
                    service.deleteDiemDung(p[1], p[3]); result = null; status = 204;
                } else throw new DomainException(404, "API không tồn tại");
            } else if (p[0].equals("chuyen-xe")) {
                if (p.length == 1 && method.equals("GET")) {
                    String date = request.getParameter("date");
                    result = service.searchChuyenXe(request.getParameter("from"), request.getParameter("to"),
                        date == null ? null : LocalDate.parse(date));
                } else if (p.length == 1 && method.equals("POST")) {
                    result = service.createChuyenXe(body(request, Inputs.ChuyenXe.class)); status = 201;
                } else if (p.length == 2 && method.equals("GET")) result = service.getChuyenXe(p[1]);
                else if (p.length == 2 && method.equals("PUT")) result = service.updateChuyenXe(p[1], body(request, Inputs.CapNhatChuyen.class));
                else if (p.length == 3 && p[2].equals("trang-thai") && method.equals("GET"))
                    result = Map.of("trangThai", service.getChuyenXe(p[1]).get("trangThai"));
                else if (p.length == 3 && p[2].equals("xe") && method.equals("PUT"))
                    result = service.assignVehicle(p[1], body(request, Inputs.GanXe.class).maXe());
                else if (p.length == 3 && method.equals("POST")) {
                    TrangThaiChuyen target = switch (p[2]) {
                        case "huy" -> TrangThaiChuyen.DA_HUY;
                        case "bat-dau" -> TrangThaiChuyen.DANG_CHAY;
                        case "hoan-thanh" -> TrangThaiChuyen.HOAN_TAT;
                        default -> throw new DomainException(404, "API không tồn tại");
                    };
                    result = service.transition(p[1], target);
                } else throw new DomainException(404, "API không tồn tại");
            } else throw new DomainException(404, "API không tồn tại");
            response.setStatus(status);
            if (result != null) json.writeValue(response.getOutputStream(), result);
        } catch (DomainException e) {
            error(response, e.status(), e.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | java.time.format.DateTimeParseException | IllegalArgumentException e) {
            error(response, 400, "Dữ liệu yêu cầu không hợp lệ: " + e.getMessage());
        } catch (jakarta.persistence.PersistenceException e) {
            getServletContext().log("Database failure on " + request.getRequestURI(), e);
            boolean constraint = false;
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException) constraint = true;
            }
            error(response, constraint ? 409 : 500, constraint
                ? "Dữ liệu xung đột với ràng buộc cơ sở dữ liệu" : "Không thể xử lý dữ liệu lúc này");
        }
    }
    private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
        T value = json.readValue(request.getInputStream(), type);
        if (value == null) throw new DomainException(400, "Nội dung yêu cầu là bắt buộc");
        return value;
    }
    private void error(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        json.writeValue(response.getOutputStream(), Map.of("error", message));
    }
}
