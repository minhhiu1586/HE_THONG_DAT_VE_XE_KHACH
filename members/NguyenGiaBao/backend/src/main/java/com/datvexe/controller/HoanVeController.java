package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.service.DomainException;
import com.datvexe.service.HoanVeService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;

import java.io.IOException;

@WebServlet("/api/hoan-tien/*")
public final class HoanVeController extends HttpServlet {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private HoanVeService service;

    @Override
    public void init() {
        service = new HoanVeService(Jpa.factory());
    }

    // GET /api/hoan-tien/{maHoanTien}
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String[] path = loaiPath(request);

        if (path.length != 1) {
            response.setHeader("Allow", "POST, OPTIONS");
            throw new DomainException(405, "Đường dẫn này không hỗ trợ GET.");
        }

        var result = service.get(path[0]);
        writeJson(response, 200, result);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String[] path = loaiPath(request);

        if (path.length != 2) {
            response.setHeader("Allow", "GET, HEAD, OPTIONS");
            throw new DomainException(405, "Đường dẫn này không hỗ trợ POST.");
        }

        readBody(request);

        // POST /api/hoan-tien/{maHoanTien}/xac-minh
        if ("xac-minh".equals(path[1])) {
            var result = service.verify(path[0], request.getRemoteAddr());
            writeJson(response, 200, result);
            return;
        }

        // POST /api/hoan-tien/{maHoanTien}/thu-lai
        var result = service.retry(
                path[0], request.getHeader("Idempotency-Key"), request.getRemoteAddr());
        writeJson(response, result.status(), result.body());
    }

    @Override
    protected void doOptions(
            HttpServletRequest request,
            HttpServletResponse response) {

        String[] path = loaiPath(request);
        String allow = "GET, HEAD, OPTIONS";

        if (path.length == 2) {
            allow = "POST, OPTIONS";
        }

        response.setHeader("Allow", allow);
        response.setStatus(204);
    }

    // helper
    private void readBody(HttpServletRequest request) throws IOException {
        JsonNode body;

        try {
            body = json.readTree(request.getInputStream());
        } catch (JsonProcessingException e) {
            throw new DomainException(400, "JSON không hợp lệ.");
        }

        if (body == null || !body.isObject() || !body.isEmpty()) {
            throw new DomainException(400, "API chỉ nhận JSON object rỗng {}.");
        }
    }

    private String[] loaiPath(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null || !path.matches("/[A-Za-z0-9_-]{1,40}(/(xac-minh|thu-lai))?")) {
            throw new DomainException(404, "API không tồn tại.");
        }

        return path.substring(1).split("/");
    }

    private void writeJson(
            HttpServletResponse response,
            int status,
            Object result) throws IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setStatus(status);

        json.writeValue(response.getOutputStream(), result);
    }
}
