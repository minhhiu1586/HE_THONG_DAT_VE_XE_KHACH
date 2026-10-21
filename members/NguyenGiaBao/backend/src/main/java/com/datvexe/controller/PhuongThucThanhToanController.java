package com.datvexe.controller;

import com.datvexe.dto.PhuongThucThanhToanDto;
import com.datvexe.service.DomainException;
import com.datvexe.service.PhuongThucThanhToanService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {
        "/api/phuong-thuc-thanh-toan",
        "/api/quan-tri/phuong-thuc-thanh-toan",
        "/api/quan-tri/phuong-thuc-thanh-toan/*"
})
public class PhuongThucThanhToanController extends HttpServlet {

    private static final String PUBLIC_PATH = "/api/phuong-thuc-thanh-toan";
    private static final String ADMIN_PATH = "/api/quan-tri/phuong-thuc-thanh-toan";

    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PhuongThucThanhToanService service;

    @Override
    public void init() {
        service = new PhuongThucThanhToanService();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // /api/phuong-thuc-thanh-toan
        if (PUBLIC_PATH.equals(request.getServletPath())) {
            writeJson(response, 200, service.danhSachKhaDung());
            return;
        }

        String[] path = loaiPath(request);

        // /api/quan-tri/phuong-thuc-thanh-toan
        if (path.length == 0) {
            writeJson(response, 200, service.danhSach());
        }
        // /api/quan-tri/phuong-thuc-thanh-toan/{ma}
        else if (path.length == 1) {
            writeJson(response, 200, service.chiTiet(path[0]));
        }
        else {
            response.setHeader("Allow", "PUT, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }
    }

// POST /api/quan-tri/phuong-thuc-thanh-toan
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (PUBLIC_PATH.equals(request.getServletPath())) {
            response.setHeader("Allow", "GET, HEAD, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }

        String[] path = loaiPath(request);
        if (path.length != 0) {
            response.setHeader("Allow", path.length == 1 ? "GET, HEAD, PUT, OPTIONS" : "PUT, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }

        PhuongThucThanhToanDto.Request input = readBody(request);
        PhuongThucThanhToanDto.Response result = service.tao(input);
        response.setHeader("Location", request.getContextPath()
                + ADMIN_PATH + "/" + result.maPhuongThuc());
        writeJson(response, 201, result);
    }

    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (PUBLIC_PATH.equals(request.getServletPath())) {
            response.setHeader("Allow", "GET, HEAD, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }

        // block /api/quan-tri/phuong-thuc-thanh-toan
        String[] path = loaiPath(request);
        if (path.length == 0) {
            response.setHeader("Allow", "GET, HEAD, POST, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }

        var input = readBody(request);

        // /api/quan-tri/phuong-thuc-thanh-toan/{ma}/trang-thai
        if (path.length == 2) {
            var result = service.capNhatTrangThai(path[0], input.trangThai());
            writeJson(response, 200, result);
            return;
        }

        // /api/quan-tri/phuong-thuc-thanh-toan/{ma}
        var result = service.capNhat(path[0], input);
        writeJson(response, 200, result);
    }

    private PhuongThucThanhToanDto.Request readBody(
            HttpServletRequest request) throws IOException {

        try {
            var input = json.readValue(
                    request.getInputStream(), PhuongThucThanhToanDto.Request.class);

            if (input == null) {
                throw new DomainException(400, "Body phải là một JSON object.");
            }

            return input;
        } catch (JsonProcessingException e) {
            throw new DomainException(
                    400, "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private String[] loaiPath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        // -
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            return new String[0];
        }

        String[] segments = pathInfo.substring(1).split("/", -1);

        // {ma}
        if (segments.length == 1 && !segments[0].isBlank()) {
            return segments;
        }

        // {ma}/trang-thai
        if (segments.length == 2
                && !segments[0].isBlank()
                && "trang-thai".equals(segments[1])) {
            return segments;
        }
        throw new DomainException(404, "API không tồn tại.");
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
