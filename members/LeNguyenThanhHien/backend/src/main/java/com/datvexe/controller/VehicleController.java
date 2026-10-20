package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.VehicleInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.DomainException;
import com.datvexe.service.VehicleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.cfg.MutableCoercionConfig;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {
    "/api/loai-xe", "/api/loai-xe/*",
    "/api/xe", "/api/xe/*"
})
public class VehicleController extends HttpServlet {
    private VehicleService service;
    private final ObjectMapper json = new ObjectMapper();

    // Cấu hình JSON và từ chối dữ liệu sai kiểu trước khi chuyển vào DTO.
    public VehicleController() {
        json.registerModule(new JavaTimeModule());
        json.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        json.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        json.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

        MutableCoercionConfig textCoercion = json.coercionConfigFor(LogicalType.Textual);
        textCoercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        textCoercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        textCoercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @Override
    public void init() {
        service = new VehicleService(Jpa.factory());
    }

    // Đọc JSON thành DTO được yêu cầu và báo lỗi nếu nội dung gửi lên bị thiếu.
    private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
        T value = json.readValue(request.getInputStream(), type);

        if (value == null) {
            throw new DomainException(400, "Thiếu nội dung yêu cầu");
        }

        return value;
    }

    // Phân loại yêu cầu về loại xe, xe và ghế để gọi đúng chức năng rồi trả kết quả JSON.
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        try {
            String kind = request.getServletPath().substring("/api/".length());
            String path = request.getPathInfo();
            String[] segments;

            if (path == null || path.equals("/")) {
                segments = new String[0];
            } else {
                segments = path.substring(1).split("/");
            }

            String method = request.getMethod();
            Object result;
            response.setStatus(200);

            if (kind.equals("xe") && segments.length == 1
                    && segments[0].equals("pham-vi") && method.equals("GET")) {
                result = service.operators();
            } else if (kind.equals("xe") && segments.length == 2
                    && segments[1].equals("so-do-ghe") && method.equals("PUT")) {
                VehicleInputs.SoDoGhe input = body(request, VehicleInputs.SoDoGhe.class);
                result = service.saveLayout(segments[0], input);
            } else if (kind.equals("xe") && segments.length == 3
                    && segments[1].equals("dieu-kien") && method.equals("GET")) {
                result = service.vehicleEligibility(segments[0], segments[2]);
            } else if (kind.equals("xe") && segments.length >= 2 && segments[1].equals("ghe")) {
                result = handleSeats(request, response, method, segments);
            } else if (segments.length <= 1) {
                String id = null;

                if (segments.length == 1) {
                    id = segments[0];
                }

                result = handleResource(request, response, method, kind, id);
            } else {
                throw new DomainException(404, "API không tồn tại");
            }

            if (result != null) {
                json.writeValue(response.getOutputStream(), result);
            }
        } catch (DomainException e) {
            ApiErrors.write(response, e);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            error(response, 400, "Dữ liệu yêu cầu không hợp lệ");
        } catch (jakarta.persistence.PersistenceException e) {
            getServletContext().log("Vehicle database failure", e);

            boolean constraint = false;
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                    constraint = true;
                }
            }

            if (constraint) {
                error(response, 409, "Dữ liệu vi phạm ràng buộc database");
            } else {
                error(response, 500, "Không thể xử lý dữ liệu lúc này");
            }
        }
    }

    private void error(HttpServletResponse response, int status, String message) throws IOException {
        ApiErrors.write(response, new DomainException(status, message));
    }

    // Xử lý yêu cầu xem, tạo, sửa, xóa hoặc sinh danh sách ghế của một xe.
    private Object handleSeats(HttpServletRequest request, HttpServletResponse response,
            String method, String[] segments) throws IOException {
        String vehicleId = segments[0];

        if (segments.length == 2 && method.equals("GET")) {
            return service.seats(vehicleId, false);
        }

        if (segments.length == 2 && method.equals("POST")) {
            VehicleInputs.Ghe input = body(request, VehicleInputs.Ghe.class);
            Object result = service.createSeat(vehicleId, input);
            response.setStatus(201);
            return result;
        }

        if (segments.length == 3 && segments[2].equals("sinh") && method.equals("POST")) {
            Object result = service.seats(vehicleId, true);
            response.setStatus(201);
            return result;
        }

        if (segments.length == 3 && method.equals("GET")) {
            return service.getSeat(vehicleId, segments[2]);
        }

        if (segments.length == 3 && method.equals("DELETE")) {
            service.deleteSeat(vehicleId, segments[2]);
            response.setStatus(204);
            return null;
        }

        if (segments.length == 3 && method.equals("PUT")) {
            VehicleInputs.Ghe input = body(request, VehicleInputs.Ghe.class);
            return service.updateSeat(vehicleId, segments[2], input);
        }

        throw new DomainException(404, "API không tồn tại");
    }

    // Xử lý yêu cầu lọc danh sách, xem chi tiết, thêm, sửa hoặc xóa loại xe và xe.
    private Object handleResource(HttpServletRequest request, HttpServletResponse response,
            String method, String kind, String id) throws IOException {
        if (method.equals("GET")) {
            if (id != null) {
                return service.get(kind, id);
            }

            String search = request.getParameter("q");
            String type = request.getParameter("maLoaiXe");
            String owner = request.getParameter("maNhaXe");
            String state = request.getParameter("trangThai");
            String tripId = request.getParameter("maChuyen");

            return service.filtered(kind, search, type, owner, state, tripId);
        }

        boolean creating = method.equals("POST") && id == null;
        boolean updating = method.equals("PUT") && id != null;

        if (creating || updating) {
            Object result;

            if (kind.equals("loai-xe")) {
                VehicleInputs.LoaiXe input = body(request, VehicleInputs.LoaiXe.class);
                result = service.saveType(id, input);
            } else if (kind.equals("xe")) {
                VehicleInputs.Xe input = body(request, VehicleInputs.Xe.class);
                result = service.saveVehicle(id, input);
            } else {
                throw new DomainException(404, "API không tồn tại");
            }

            if (creating) {
                response.setStatus(201);
            }

            return result;
        }

        if (method.equals("DELETE") && id != null) {
            service.delete(kind, id);
            response.setStatus(204);
            return null;
        }

        throw new DomainException(405, "Phương thức không được hỗ trợ");
    }
}