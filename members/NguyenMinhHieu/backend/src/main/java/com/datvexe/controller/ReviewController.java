package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.ReviewInputs;
import com.datvexe.security.*;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebServlet(urlPatterns = {"/api/danh-gia", "/api/danh-gia/*", "/api/quan-ly/danh-gia"})
public final class ReviewController extends HttpServlet {
    private ReviewService reviews;
    private final ObjectMapper json =
            new ObjectMapper().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

    public ReviewController() {
        json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
        json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
                .setCoercion(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
                        com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }

    @Override
    public void init() {
        reviews = new ReviewService(Jpa.factory());
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
            String[] p =
                    path == null || path.equals("/") ? new String[0] : path.substring(1).split("/");
            String method = req.getMethod();
            Object result;
            int status = 200;
            if (req.getServletPath().equals("/api/quan-ly/danh-gia") && method.equals("GET"))
                result =
                        reviews.management(
                                req.getParameter("maChuyen"),
                                req.getParameter("trangThai"),
                                number(req.getParameter("page"), 1),
                                number(req.getParameter("size"), 20));
            else if (p.length == 1 && p[0].equals("cong-khai") && method.equals("GET"))
                result =
                        reviews.publicReviews(
                                req.getParameter("maChuyen"),
                                number(req.getParameter("page"), 1),
                                number(req.getParameter("size"), 10));
            else if (p.length == 1 && p[0].equals("dieu-kien") && method.equals("GET"))
                result = reviews.eligibility(req.getParameter("maChuyen"));
            else if (p.length == 0 && method.equals("POST")) {
                result =
                        reviews.create(
                                json.readValue(req.getInputStream(), ReviewInputs.Create.class));
                status = 201;
            } else if (p.length == 1 && method.equals("PUT"))
                result =
                        reviews.edit(
                                p[0],
                                json.readValue(req.getInputStream(), ReviewInputs.Edit.class));
            else if (p.length == 1 && method.equals("DELETE")) result = reviews.delete(p[0]);
            else if (p.length == 2 && p[1].equals("lich-su") && method.equals("GET"))
                result = reviews.history(p[0]);
            else if (p.length == 2
                    && (p[1].equals("duyet") || p[1].equals("an"))
                    && method.equals("POST")) {
                var input = json.readValue(req.getInputStream(), ReviewInputs.Moderation.class);
                result =
                        reviews.moderate(
                                p[0], p[1].equals("duyet"), input == null ? null : input.lyDo());
            } else throw new DomainException(404, "API không tồn tại.");
            res.setStatus(status);
            json.writeValue(res.getOutputStream(), result);
        } catch (DomainException e) {
            ApiErrors.write(res, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            ApiErrors.write(
                    res,
                    new AccountException(
                            400, "VALIDATION_ERROR", "Dữ liệu đánh giá không hợp lệ."));
        } catch (PersistenceException e) {
            getServletContext().log("Review database error", e);
            ApiErrors.write(res, new DomainException(500, "Không thể xử lý đánh giá lúc này."));
        }
    }
}
