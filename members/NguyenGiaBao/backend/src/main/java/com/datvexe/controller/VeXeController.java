package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.HoanVeDto;
import com.datvexe.service.DomainException;
import com.datvexe.service.HoanVeService;
import com.datvexe.service.VeXeService;

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

@WebServlet("/api/ve-xe/*")
public class VeXeController extends HttpServlet {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private VeXeService service;
    private HoanVeService refunds;

    @Override
    public void init() {
        service = new VeXeService();
        refunds = new HoanVeService(Jpa.factory());
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String[] path = loaiPath(request);
        String maVe = path[0];

        // GET /api/ve-xe/{maVe}
        if (path.length == 1) {
            var result = service.chiTiet(maVe);
            writeJson(response, 200, result);
            return;
        }

        // GET /api/ve-xe/{maVe}/chinh-sach-hoan
        if ("chinh-sach-hoan".equals(path[1])) {
            var result = refunds.policy(maVe);
            writeJson(response, 200, result);
            return;
        }

        // GET /api/ve-xe/{maVe}/hoan-tien?page=1&size=20
        String pageText = request.getParameter("page");
        String sizeText = request.getParameter("size");
        String sort = request.getParameter("sort");
        int page = 1;
        int size = 20;

        try {
            if (pageText != null && !pageText.trim().isEmpty()) {
                page = Integer.parseInt(pageText);
            }
            if (sizeText != null && !sizeText.trim().isEmpty()) {
                size = Integer.parseInt(sizeText);
            }
        } catch (NumberFormatException e) {
            throw new DomainException(400, "Tham số phân trang sai định dạng.");
        }

        var result = refunds.history(maVe, page, size, sort);
        writeJson(response, 200, result);
    }

    // POST /api/ve-xe/{maVe}/hoan-tien
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String[] path = loaiPath(request);

        if (path.length != 2 || !"hoan-tien".equals(path[1])) {
            response.setHeader("Allow", "GET, HEAD, OPTIONS");
            throw new DomainException(405, "Đường dẫn này không hỗ trợ POST.");
        }

        HoanVeDto.SingleRequest input = readBody(request);
        var result = refunds.createTicket(
                path[0], input, request.getHeader("Idempotency-Key"), request.getRemoteAddr());

        JsonNode body = json.valueToTree(result.body());
        String maHoanTien = body.path("maHoanTien").asText();
        response.setHeader(
                "Location",
                request.getContextPath() + "/api/hoan-tien/" + maHoanTien
        );

        writeJson(response, result.status(), result.body());
    }

    @Override
    protected void doOptions(
            HttpServletRequest request,
            HttpServletResponse response) {

        String[] path = loaiPath(request);
        String allow = "GET, HEAD, OPTIONS";

        if (path.length == 2 && "hoan-tien".equals(path[1])) {
            allow = "GET, HEAD, POST, OPTIONS";
        }

        response.setHeader("Allow", allow);
        response.setStatus(204);
    }

    // helper
    private HoanVeDto.SingleRequest readBody(HttpServletRequest request)
            throws IOException {

        try {
            HoanVeDto.SingleRequest input = json.readValue(
                    request.getInputStream(), HoanVeDto.SingleRequest.class);

            if (input == null) {
                throw new DomainException(400, "Thiếu dữ liệu hoàn tiền.");
            }

            return input;
        } catch (JsonProcessingException e) {
            throw new DomainException(400, "JSON không hợp lệ.");
        }
    }

    private String[] loaiPath(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null || !path.matches("/[A-Za-z0-9_-]{1,40}(/(chinh-sach-hoan|hoan-tien))?")) {
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
