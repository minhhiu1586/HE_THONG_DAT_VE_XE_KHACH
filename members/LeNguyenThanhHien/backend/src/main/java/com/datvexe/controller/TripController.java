package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiChuyen;
import com.datvexe.dto.TripInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.time.LocalDate;

@WebServlet(urlPatterns = {"/api/quan-ly/chuyen-xe", "/api/quan-ly/chuyen-xe/*"})
public class TripController extends HttpServlet {
    private TripService service;
    private final ObjectMapper json =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

    // Không tự đổi số hoặc boolean thành chuỗi để tránh chấp nhận dữ liệu request sai kiểu.
    public TripController() {
        json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }

    @Override
    public void init() {
        service = new TripService(Jpa.factory());
    }

    private TripInputs.Draft body(HttpServletRequest request) throws IOException {
        TripInputs.Draft value = json.readValue(request.getInputStream(), TripInputs.Draft.class);
        if (value == null) {
            throw new DomainException(400, "Thiếu nội dung yêu cầu.");
        }
        return value;
    }

    private static LocalDate date(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static int number(String value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json; charset=UTF-8");
        try {
            String path = req.getPathInfo();
            String[] segments =
                    path == null || path.equals("/") ? new String[0] : path.substring(1).split("/");
            String method = req.getMethod();
            Object result;
            res.setStatus(200);
            if (segments.length == 0) {
                result = handleCollection(req, res, method);
            } else if (segments.length == 1) {
                result = handleTrip(req, res, method, segments[0]);
            } else if (segments.length == 2) {
                result = handleAction(req, method, segments[0], segments[1]);
            } else {
                throw new DomainException(404, "API không tồn tại.");
            }
            if (result != null) {
                json.writeValue(res.getOutputStream(), result);
            }
        } catch (DomainException error) {
            ApiErrors.write(res, error);
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException
                | java.time.format.DateTimeParseException error) {
            ApiErrors.write(res, new DomainException(400, "Dữ liệu yêu cầu không hợp lệ."));
        } catch (jakarta.persistence.PersistenceException error) {
            getServletContext().log("Trip database error", error);
            boolean constraint = false;
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                    constraint = true;
                }
            }
            ApiErrors.write(
                    res,
                    new DomainException(
                            constraint ? 409 : 500,
                            constraint
                                    ? "Dữ liệu chuyến bị trùng hoặc vi phạm ràng buộc."
                                    : "Không thể xử lý chuyến lúc này."));
        }
    }

    private Object handleCollection(HttpServletRequest req, HttpServletResponse res, String method)
            throws IOException {
        if (method.equals("GET")) {
            String stateParameter = req.getParameter("trangThai");
            TrangThaiChuyen state = null;
            if (stateParameter != null && !stateParameter.isBlank()) {
                state = TrangThaiChuyen.valueOf(stateParameter);
            }
            return service.list(
                    req.getParameter("maNhaXe"),
                    req.getParameter("maTuyen"),
                    req.getParameter("maXe"),
                    date(req.getParameter("tuNgay")),
                    date(req.getParameter("denNgay")),
                    state,
                    number(req.getParameter("page"), 1),
                    number(req.getParameter("size"), 20));
        }
        if (method.equals("POST")) {
            TripInputs.Draft input = body(req);
            Object result = service.create(input);
            res.setStatus(201);
            return result;
        }
        throw new DomainException(404, "API không tồn tại.");
    }

    private Object handleTrip(
            HttpServletRequest req, HttpServletResponse res, String method, String tripId)
            throws IOException {
        if (method.equals("GET")) {
            return service.get(tripId);
        }
        if (method.equals("PUT")) {
            TripInputs.Draft input = body(req);
            return service.update(tripId, input);
        }
        if (method.equals("DELETE")) {
            service.delete(tripId);
            res.setStatus(204);
            return null;
        }
        throw new DomainException(404, "API không tồn tại.");
    }

    private Object handleAction(HttpServletRequest req, String method, String tripId, String action)
            throws IOException {
        if (method.equals("GET")) {
            if (action.equals("dieu-kien-mo-ban")) {
                return service.eligibility(tripId);
            }
            if (action.equals("vong-doi")) {
                return service.lifecycleReport(tripId);
            }
        }
        if (method.equals("POST")) {
            if (action.equals("xu-ly-hoan")) {
                return service.retryCancellationWork(tripId);
            }
            if (action.equals("mo-ban")) {
                return service.open(tripId);
            }
            if (action.equals("bat-dau")) {
                return service.transition(tripId, TrangThaiChuyen.DANG_CHAY, null);
            }
            if (action.equals("hoan-thanh")) {
                return service.transition(tripId, TrangThaiChuyen.HOAN_TAT, null);
            }
            if (action.equals("huy")) {
                TripInputs.Cancellation input =
                        json.readValue(req.getInputStream(), TripInputs.Cancellation.class);
                if (input == null) {
                    throw new DomainException(400, "Thiếu lý do hủy chuyến.");
                }
                return service.cancel(tripId, input.lyDo());
            }
        }
        throw new DomainException(404, "API không tồn tại.");
    }
}
