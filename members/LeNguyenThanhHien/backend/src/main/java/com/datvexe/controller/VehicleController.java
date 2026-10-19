package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.VehicleInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.cfg.MutableCoercionConfig;
import com.fasterxml.jackson.databind.type.LogicalType;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebServlet(
        urlPatterns = {
            "/api/loai-xe",
            "/api/loai-xe/*",
            "/api/xe",
            "/api/xe/*",
            "/api/phan-cong",
            "/api/phan-cong/*"
        })
public class VehicleController extends HttpServlet {
    private VehicleService service;
    private final ObjectMapper json = new ObjectMapper();

    // Từ chối giá trị số/boolean ở trường chuỗi, giữ việc kiểm tra kiểu dữ liệu đầu vào chặt chẽ.
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

    private <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        T value = json.readValue(req.getInputStream(), type);
        if (value == null) {
            throw new DomainException(400, "Thiếu nội dung yêu cầu");
        }
        return value;
    }

    // Phân loại API loại xe, xe, ghế và phân công để gọi đúng chức năng và trả kết quả JSON.
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json; charset=UTF-8");
        try {
            String kind = req.getServletPath().substring("/api/".length());
            String path = req.getPathInfo();
            String[] segments;
            if (path == null || path.equals("/")) {
                segments = new String[0];
            } else {
                segments = path.substring(1).split("/");
            }
            String method = req.getMethod();
            Object result;
            res.setStatus(200);
            if (kind.equals("xe") && segments.length == 1 && segments[0].equals("pham-vi") && method.equals("GET")) {
                result = service.operators();
            } else if (kind.equals("xe") && segments.length == 2 && segments[1].equals("so-do-ghe") && method.equals("PUT")) {
                VehicleInputs.SoDoGhe input = body(req, VehicleInputs.SoDoGhe.class);
                result = service.saveLayout(segments[0], input);
            } else if (kind.equals("xe") && segments.length == 3 && segments[1].equals("dieu-kien") && method.equals("GET")) {
                result = service.vehicleEligibility(segments[0], segments[2]);
            } else if (kind.equals("xe") && segments.length >= 2 && segments[1].equals("ghe")) {
                result = handleSeats(req, res, method, segments);
            } else if (segments.length <= 1) {
                String id;
                if (segments.length == 0) {
                    id = null;
                } else {
                    id = segments[0];
                }
                result = handleResource(req, res, method, kind, id);
            } else {
                throw new DomainException(404, "API không tồn tại");
            }
            if (result != null) {
                json.writeValue(res.getOutputStream(), result);
            }
        } catch (DomainException e) {
            ApiErrors.write(res, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            error(res, 400, "Dữ liệu yêu cầu không hợp lệ");
        } catch (jakarta.persistence.PersistenceException e) {
            getServletContext().log("Vehicle database failure", e);
            boolean constraint = false;
            for (Throwable c = e; c != null; c = c.getCause()) {
                if (c instanceof org.hibernate.exception.ConstraintViolationException) {
                    constraint = true;
                }
            }
            if (constraint) {
                error(res, 409, "Dữ liệu vi phạm ràng buộc database");
            } else {
                error(res, 500, "Không thể xử lý dữ liệu lúc này");
            }
        }
    }

    private void error(HttpServletResponse res, int status, String message) throws IOException {
        ApiErrors.write(res, new DomainException(status, message));
    }

    // Xử lý yêu cầu xem, tạo, sửa, xóa ghế hoặc lưu toàn bộ sơ đồ ghế của xe.
    private Object handleSeats(HttpServletRequest req, HttpServletResponse res, String method, String[] segments) throws IOException {
        String vehicleId = segments[0];
        if (segments.length == 2) {
            if (method.equals("GET")) {
                return service.seats(vehicleId, false);
            }
            if (method.equals("POST")) {
                VehicleInputs.Ghe input = body(req, VehicleInputs.Ghe.class);
                Object result = service.createSeat(vehicleId, input);
                res.setStatus(201);
                return result;
            }
        }
        if (segments.length == 3) {
            String seatId = segments[2];
            if (seatId.equals("sinh") && method.equals("POST")) {
                Object result = service.seats(vehicleId, true);
                res.setStatus(201);
                return result;
            }
            if (method.equals("GET")) {
                return service.getSeat(vehicleId, seatId);
            }
            if (method.equals("PUT")) {
                VehicleInputs.Ghe input = body(req, VehicleInputs.Ghe.class);
                return service.updateSeat(vehicleId, seatId, input);
            }
            if (method.equals("DELETE")) {
                service.deleteSeat(vehicleId, seatId);
                res.setStatus(204);
                return null;
            }
        }
        throw new DomainException(404, "API không tồn tại");
    }

    // Xử lý yêu cầu xem, thêm, sửa hoặc xóa loại xe, xe và phân công nhân viên.
    private Object handleResource(HttpServletRequest req, HttpServletResponse res, String method, String kind, String id) throws IOException {
        if (method.equals("GET")) {
            if (id != null) {
                return service.get(kind, id);
            }
            return service.filtered(kind, req.getParameter("q"), req.getParameter("maLoaiXe"), req.getParameter("maNhaXe"),
                req.getParameter("trangThai"), req.getParameter("maChuyen"));
        }
        boolean creating = method.equals("POST") && id == null;
        boolean updating = method.equals("PUT") && id != null;
        if (creating || updating) {
            Object result;
            if (kind.equals("loai-xe")) {
                result = service.saveType(id, body(req, VehicleInputs.LoaiXe.class));
            } else if (kind.equals("xe")) {
                result = service.saveVehicle(id, body(req, VehicleInputs.Xe.class));
            } else if (kind.equals("phan-cong")) {
                result = service.saveAssignment(id, body(req, VehicleInputs.PhanCong.class));
            } else {
                throw new DomainException(404, "API không tồn tại");
            }
            if (creating) {
                res.setStatus(201);
            }
            return result;
        }
        if (method.equals("DELETE") && id != null) {
            service.delete(kind, id);
            res.setStatus(204);
            return null;
        }
        throw new DomainException(405, "Phương thức không được hỗ trợ");
    }
}
