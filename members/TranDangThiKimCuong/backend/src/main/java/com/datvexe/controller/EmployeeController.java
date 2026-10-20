package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.VaiTroChuyen;
import com.datvexe.dto.EmployeeInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.DomainException;
import com.datvexe.service.EmployeeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.hibernate.exception.ConstraintViolationException;

@WebServlet(urlPatterns = {"/api/nhan-vien", "/api/nhan-vien/*"})
public class EmployeeController extends HttpServlet {
    private EmployeeService service;
    private final ObjectMapper json = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

    public EmployeeController() {
        json.coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @Override
    public void init() {
        service = new EmployeeService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        try {
            String[] parts = pathParts(request.getPathInfo());
            switch (request.getMethod()) {
                case "GET" -> write(response, 200, readEmployee(request, parts));
                case "POST" -> {
                    requirePathLength(parts, 0);
                    write(response, 201, service.save(null, readBody(request)));
                }
                case "PUT" -> {
                    requirePathLength(parts, 1);
                    write(response, 200, service.save(parts[0], readBody(request)));
                }
                case "DELETE" -> {
                    requirePathLength(parts, 1);
                    service.delete(parts[0]);
                    response.setStatus(204);
                }
                default -> throw notFound();
            }
        } catch (DomainException error) {
            ApiErrors.write(response, error);
        } catch (JsonProcessingException | IllegalArgumentException error) {
            ApiErrors.write(response, new DomainException(400, "Dữ liệu yêu cầu không hợp lệ."));
        } catch (PersistenceException error) {
            getServletContext().log("Employee database failure", error);
            boolean constraint = isConstraintViolation(error);
            ApiErrors.write(response, new DomainException(constraint ? 409 : 500, constraint
                ? "Dữ liệu xung đột; vui lòng kiểm tra lại hồ sơ."
                : "Không thể xử lý dữ liệu lúc này."));
        }
    }

    private Object readEmployee(HttpServletRequest request, String[] parts) {
        if (parts.length == 0) {
            return service.list(request.getParameter("q"), request.getParameter("maNhaXe"),
                request.getParameter("loaiNhanVien"), request.getParameter("trangThai"));
        }
        if (parts.length == 1) {
            return service.get(parts[0]);
        }
        if (parts.length == 2) {
            if (parts[1].equals("phan-cong")) {
                return service.assignments(parts[0]);
            }
            if (parts[1].equals("dieu-kien")) {
                String role = request.getParameter("vaiTro");
                return service.eligibility(parts[0], request.getParameter("maChuyen"),
                    role == null ? null : VaiTroChuyen.valueOf(role));
            }
        }
        throw notFound();
    }

    private EmployeeInputs.Employee readBody(HttpServletRequest request) throws IOException {
        return json.readValue(request.getInputStream(), EmployeeInputs.Employee.class);
    }

    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        if (body != null) {
            json.writeValue(response.getOutputStream(), body);
        }
    }

    private static String[] pathParts(String path) {
        return path == null || path.equals("/") ? new String[0] : path.substring(1).split("/");
    }

    private static void requirePathLength(String[] parts, int expected) {
        if (parts.length != expected) {
            throw notFound();
        }
    }

    private static DomainException notFound() {
        return new DomainException(404, "API không tồn tại.");
    }

    private static boolean isConstraintViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }
}
