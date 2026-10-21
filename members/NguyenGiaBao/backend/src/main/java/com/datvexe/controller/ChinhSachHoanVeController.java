package com.datvexe.controller;

import com.datvexe.dto.ChinhSachHoanVeDto;
import com.datvexe.service.ChinhSachHoanVeService;
import com.datvexe.service.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {
        "/api/quan-tri/chinh-sach-hoan-ve",
        "/api/quan-tri/chinh-sach-hoan-ve/*"
})
public class ChinhSachHoanVeController extends HttpServlet {
    private static final String ADMIN_PATH = "/api/quan-tri/chinh-sach-hoan-ve";

    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ChinhSachHoanVeService service;

    @Override
    public void init() {
        service = new ChinhSachHoanVeService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] path = loaiPath(request);
        if (path.length == 0) {
            int page = 1;
            int size = 20;
            String pageValue = request.getParameter("page");
            String sizeValue = request.getParameter("size");
            try {
                if (pageValue != null && !pageValue.isBlank()) {
                    page = Integer.parseInt(pageValue);
                }
                if (sizeValue != null && !sizeValue.isBlank()) {
                    size = Integer.parseInt(sizeValue);
                }
            } catch (NumberFormatException e) {
                throw new DomainException(400, "page và size phải là số nguyên.");
            }
            writeJson(response, 200, service.danhSach(page, size));
            return;
        }
        if (path.length == 1) {
            writeJson(response, 200, service.chiTiet(path[0]));
            return;
        }

        response.setHeader("Allow", "PUT, OPTIONS");
        throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] path = loaiPath(request);
        if (path.length != 0) {
            response.setHeader("Allow", path.length == 1
                    ? "GET, HEAD, PUT, OPTIONS"
                    : "PUT, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }

        ChinhSachHoanVeDto.Request input = readBody(request);
        ChinhSachHoanVeDto.Response result = service.tao(input);
        response.setHeader("Location", request.getContextPath()
                + ADMIN_PATH + "/" + result.maChinhSach());
        writeJson(response, 201, result);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] path = loaiPath(request);
        if (path.length == 0) {
            response.setHeader("Allow", "GET, HEAD, POST, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }

        ChinhSachHoanVeDto.Request input = readBody(request);
        if (path.length == 1) {
            writeJson(response, 200, service.capNhat(path[0], input));
        } else {
            writeJson(response, 200, service.capNhatTrangThai(path[0], input.trangThai()));
        }
    }

    private ChinhSachHoanVeDto.Request readBody(HttpServletRequest request) throws IOException {
        try {
            ChinhSachHoanVeDto.Request body = json.readValue(
                    request.getInputStream(), ChinhSachHoanVeDto.Request.class);
            if (body == null) {
                throw new DomainException(400, "Body không được để trống.");
            }
            return body;
        } catch (JsonProcessingException e) {
            throw new DomainException(400, "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private String[] loaiPath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            return new String[0];
        }
        String[] segments = pathInfo.substring(1).split("/", -1);
        if (segments.length == 1 && !segments[0].isBlank()) {
            return segments;
        }
        if (segments.length == 2
                && !segments[0].isBlank()
                && "trang-thai".equals(segments[1])) {
            return segments;
        }
        throw new DomainException(404, "API không tồn tại.");
    }

    private void writeJson(HttpServletResponse response, int status, Object result) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setStatus(status);
        json.writeValue(response.getOutputStream(), result);
    }
}
