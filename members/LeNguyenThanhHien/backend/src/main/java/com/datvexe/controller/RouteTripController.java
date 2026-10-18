package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiBenXe;
import com.datvexe.domain.States.TrangThaiChuyen;
import com.datvexe.domain.States.TrangThaiTuyenXe;
import com.datvexe.dto.Inputs;
import com.datvexe.security.ApiErrors;
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
    private final ObjectMapper json =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private RouteTripService service;

    @Override
    public void init() {
        service = new RouteTripService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        try {
            String[] segments =
                    (request.getPathInfo() == null ? "" : request.getPathInfo())
                            .replaceFirst("^/", "")
                            .split("/");
            String method = request.getMethod();
            Object result;
            if (segments[0].equals("tinh-thanh")) {
                result = handleCities(request, response, segments, method);
            } else if (segments[0].equals("ben-xe")) {
                result = handleStations(request, response, segments, method);
            } else if (segments[0].equals("tuyen-xe")) {
                result = handleRoutes(request, response, segments, method);
            } else if (segments[0].equals("chuyen-xe")) {
                result = handleTrips(request, response, segments, method);
            } else {
                throw new DomainException(404, "API không tồn tại");
            }
            if (result != null) {
                json.writeValue(response.getOutputStream(), result);
            }
        } catch (DomainException e) {
            ApiErrors.write(response, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | java.time.format.DateTimeParseException
                | IllegalArgumentException e) {
            error(response, 400, "Dữ liệu yêu cầu không hợp lệ");
        } catch (jakarta.persistence.PersistenceException e) {
            getServletContext().log("Database failure on " + request.getRequestURI(), e);
            boolean constraint = false;
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                    constraint = true;
                }
            }
            error(
                    response,
                    constraint ? 409 : 500,
                    constraint
                            ? "Dữ liệu xung đột với ràng buộc cơ sở dữ liệu"
                            : "Không thể xử lý dữ liệu lúc này");
        }
    }

    private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
        T value = json.readValue(request.getInputStream(), type);
        if (value == null) {
            throw new DomainException(400, "Nội dung yêu cầu là bắt buộc");
        }
        return value;
    }

    private static <E extends Enum<E>> E enumQuery(String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException(400, "Giá trị bộ lọc không hợp lệ");
        }
    }

    private void error(HttpServletResponse response, int code, String message) throws IOException {
        ApiErrors.write(response, new DomainException(code, message));
    }

    private Object handleStations(
            HttpServletRequest request,
            HttpServletResponse response,
            String[] segments,
            String method)
            throws IOException {
        Object result;
        int status = 200;

        if (segments.length == 1 && method.equals("GET")) {
            result =
                    service.listBenXe(
                            request.getParameter("maTinhThanh"),
                            request.getParameter("q"),
                            enumQuery(request.getParameter("trangThai"), TrangThaiBenXe.class));
        } else if (segments.length == 1 && method.equals("POST")) {
            result = service.createBenXe(body(request, Inputs.BenXe.class));
            status = 201;
        } else if (segments.length == 2 && method.equals("GET")) {
            result = service.getBenXe(segments[1]);
        } else if (segments.length == 2 && method.equals("PUT")) {
            result = service.updateBenXe(segments[1], body(request, Inputs.BenXe.class));
        } else if (segments.length == 2 && method.equals("DELETE")) {
            service.deleteBenXe(segments[1]);
            result = null;
            status = 204;
        } else throw new DomainException(404, "API không tồn tại");

        response.setStatus(status);
        return result;
    }

    private Object handleRoutes(
            HttpServletRequest request,
            HttpServletResponse response,
            String[] segments,
            String method)
            throws IOException {
        Object result;
        int status = 200;

        if (segments.length == 2 && segments[1].equals("cong-khai") && method.equals("GET")) {
            result = service.listTuyenXe(null, null, null, TrangThaiTuyenXe.DANG_KHAI_THAC);
        } else if (segments.length == 1 && method.equals("GET")) {
            result =
                    service.listTuyenXe(
                            request.getParameter("tramKhoiHanh"),
                            request.getParameter("tramDen"),
                            request.getParameter("maNhaXe"),
                            enumQuery(request.getParameter("trangThai"), TrangThaiTuyenXe.class));
        } else if (segments.length == 1 && method.equals("POST")) {
            result = service.createTuyenXe(body(request, Inputs.TuyenXe.class));
            status = 201;
        } else if (segments.length == 2 && method.equals("GET")) {
            result = service.getTuyenXe(segments[1]);
        } else if (segments.length == 2 && method.equals("PUT")) {
            result = service.updateTuyenXe(segments[1], body(request, Inputs.TuyenXe.class));
        } else if (segments.length == 2 && method.equals("DELETE")) {
            service.deleteTuyenXe(segments[1]);
            result = null;
            status = 204;
        } else if (segments.length == 3
                && segments[2].equals("tram-dau-cuoi")
                && method.equals("PUT")) {
            result = service.setTramDauCuoi(segments[1], body(request, Inputs.TramDauCuoi.class));
        } else if (segments.length == 3
                && segments[2].equals("trang-thai")
                && method.equals("PUT")) {
            result =
                    service.setTrangThaiTuyenXe(
                            segments[1], body(request, Inputs.TrangThaiTuyen.class).trangThai());
        } else if (segments.length == 3
                && segments[2].equals("diem-dung")
                && method.equals("POST")) {
            result = service.addDiemDung(segments[1], body(request, Inputs.DiemDung.class));
            status = 201;
        } else if (segments.length == 4
                && segments[2].equals("diem-dung")
                && segments[3].equals("sap-xep")
                && method.equals("PUT")) {
            result =
                    service.reorderDiemDung(
                            segments[1], body(request, Inputs.SapXepDiemDung.class));
        } else if (segments.length == 4
                && segments[2].equals("diem-dung")
                && method.equals("PUT")) {
            result =
                    service.updateDiemDung(
                            segments[1], segments[3], body(request, Inputs.CapNhatDiemDung.class));
        } else if (segments.length == 4
                && segments[2].equals("diem-dung")
                && method.equals("DELETE")) {
            service.deleteDiemDung(segments[1], segments[3]);
            result = null;
            status = 204;
        } else throw new DomainException(404, "API không tồn tại");

        response.setStatus(status);
        return result;
    }

    private Object handleTrips(
            HttpServletRequest request,
            HttpServletResponse response,
            String[] segments,
            String method)
            throws IOException {
        Object result;
        int status = 200;

        if (segments.length == 1 && method.equals("GET")) {
            String date = request.getParameter("date");
            result =
                    service.searchChuyenXe(
                            request.getParameter("from"),
                            request.getParameter("to"),
                            date == null ? null : LocalDate.parse(date));
        } else if (segments.length == 1 && method.equals("POST")) {
            result = service.createChuyenXe(body(request, Inputs.ChuyenXe.class));
            status = 201;
        } else if (segments.length == 2 && method.equals("GET")) {
            result = service.getChuyenXe(segments[1]);
        } else if (segments.length == 2 && method.equals("PUT")) {
            result = service.updateChuyenXe(segments[1], body(request, Inputs.CapNhatChuyen.class));
        } else if (segments.length == 3 && segments[2].equals("ghe") && method.equals("GET")) {
            result = new VehicleService(Jpa.factory()).tripSeats(segments[1]);
        } else if (segments.length == 3
                && segments[2].equals("trang-thai")
                && method.equals("GET")) {
            result = Map.of("trangThai", service.getChuyenXe(segments[1]).get("trangThai"));
        } else if (segments.length == 3 && segments[2].equals("xe") && method.equals("PUT")) {
            result = service.assignVehicle(segments[1], body(request, Inputs.GanXe.class).maXe());
        } else if (segments.length == 3
                && segments[2].equals("hanh-khach")
                && method.equals("GET")) {
            result = new TripPassengerService(Jpa.factory()).list(segments[1]);
        } else if (segments.length == 3 && method.equals("POST")) {
            TrangThaiChuyen target =
                    switch (segments[2]) {
                        case "huy" -> TrangThaiChuyen.DA_HUY;
                        case "bat-dau" -> TrangThaiChuyen.DANG_CHAY;
                        case "hoan-thanh" -> TrangThaiChuyen.HOAN_TAT;
                        default -> throw new DomainException(404, "API không tồn tại");
                    };
            if (target == TrangThaiChuyen.DA_HUY) {
                throw new DomainException(
                        400,
                        "API hủy chuyến yêu cầu lý do; dùng"
                                + " /api/quan-ly/chuyen-xe/{maChuyen}/huy.");
            }
            result = service.transition(segments[1], target);
        } else throw new DomainException(404, "API không tồn tại");

        response.setStatus(status);
        return result;
    }

    private Object handleCities(
            HttpServletRequest request,
            HttpServletResponse response,
            String[] segments,
            String method)
            throws IOException {
        Object result;
        int status = 200;
        if (segments.length == 1 && segments[0].equals("tinh-thanh")) {
            if (method.equals("GET")) {
                result = service.listTinhThanh(request.getParameter("q"));
            } else if (method.equals("POST")) {
                result = service.createTinhThanh(body(request, Inputs.TinhThanh.class));
                status = 201;
            } else throw new DomainException(405, "Phương thức không được hỗ trợ");
        } else if (segments[0].equals("tinh-thanh") && segments.length == 2) {
            if (method.equals("GET")) {
                result = service.getTinhThanh(segments[1]);
            } else if (method.equals("PUT")) {
                result =
                        service.updateTinhThanh(
                                segments[1], body(request, Inputs.CapNhatTinhThanh.class));
            } else if (method.equals("DELETE")) {
                service.deleteTinhThanh(segments[1]);
                result = null;
                status = 204;
            } else throw new DomainException(405, "Phương thức không được hỗ trợ");
        } else throw new DomainException(404, "API không tồn tại");
        response.setStatus(status);
        return result;
    }
}
