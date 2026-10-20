package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.AssignmentInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.AssignmentService;
import com.datvexe.service.DomainException;
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

@WebServlet(urlPatterns = {"/api/phan-cong", "/api/phan-cong/*"})
public class AssignmentController extends HttpServlet {
    private AssignmentService service;
    private final ObjectMapper json = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

    public AssignmentController() {
        json.coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @Override
    public void init() {
        service = new AssignmentService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        try {
            String path = request.getPathInfo();
            String[] parts = path == null || path.equals("/")
                ? new String[0] : path.substring(1).split("/");
            if (parts.length > 1) {
                throw new DomainException(404, "API không tồn tại");
            }
            String id = parts.length == 0 ? null : parts[0];
            switch (request.getMethod()) {
                case "GET" -> write(response, 200, id == null
                    ? service.filtered(request.getParameter("q")) : service.get(id));
                case "POST" -> {
                    require(id == null);
                    write(response, 201, service.save(null, readBody(request)));
                }
                case "PUT" -> {
                    require(id != null);
                    write(response, 200, service.save(id, readBody(request)));
                }
                case "DELETE" -> {
                    require(id != null);
                    service.delete(id);
                    response.setStatus(204);
                }
                default -> throw new DomainException(405, "Phương thức không được hỗ trợ");
            }
        } catch (DomainException error) {
            ApiErrors.write(response, error);
        } catch (JsonProcessingException | IllegalArgumentException error) {
            ApiErrors.write(response, new DomainException(400, "Dữ liệu yêu cầu không hợp lệ"));
        } catch (PersistenceException error) {
            getServletContext().log("Assignment database failure", error);
            boolean constraint = isConstraintViolation(error);
            ApiErrors.write(response, new DomainException(constraint ? 409 : 500, constraint
                ? "Dữ liệu vi phạm ràng buộc database" : "Không thể xử lý dữ liệu lúc này"));
        }
    }

    private AssignmentInputs.PhanCong readBody(HttpServletRequest request) throws IOException {
        var input = json.readValue(request.getInputStream(), AssignmentInputs.PhanCong.class);
        if (input == null) {
            throw new DomainException(400, "Thiếu nội dung yêu cầu");
        }
        return input;
    }

    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        if (body != null) {
            json.writeValue(response.getOutputStream(), body);
        }
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new DomainException(405, "Phương thức không được hỗ trợ");
        }
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
