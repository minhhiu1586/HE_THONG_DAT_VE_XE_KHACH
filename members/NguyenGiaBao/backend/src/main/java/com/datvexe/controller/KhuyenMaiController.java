package com.datvexe.controller;

import com.datvexe.domain.States.TrangThaiKhuyenMai;
import com.datvexe.dto.KhuyenMaiDto;
import com.datvexe.service.DomainException;
import com.datvexe.service.KhuyenMaiService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@WebServlet(urlPatterns = {
        "/api/khuyen-mai",
        "/api/quan-tri/khuyen-mai",
        "/api/quan-tri/khuyen-mai/*"
})
public class KhuyenMaiController extends HttpServlet {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private KhuyenMaiService service;

    @Override
    public void init() {
        service = new KhuyenMaiService();
    }


    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // GET /api/khuyen-mai
        if ("/api/khuyen-mai".equals(request.getServletPath())) {
            var result = service.danhSachCongKhai();
            writeJson(response, 200, result);
            return;
        }

        // GET /api/quan-tri/khuyen-mai
        String ma = maTuPath(request);

        if (ma != null) {
            writeJson(response, 200, service.chiTiet(ma));
            return;
        }

        // GET /api/quan-tri/khuyen-mai?optional=[page, size, tuKhoa, trangThai, tuNgay, denNgay]
        String tuKhoa = request.getParameter("tuKhoa");
        String pageText = request.getParameter("page");
        String sizeText = request.getParameter("size");
        String trangThaiText = request.getParameter("trangThai");
        String tuNgayText = request.getParameter("tuNgay");
        String denNgayText = request.getParameter("denNgay");

        int page = 1;
        int size = 20;
        TrangThaiKhuyenMai trangThai = null;
        LocalDate tuNgay = null;
        LocalDate denNgay = null;

        try {
            if (pageText != null && !pageText.trim().isEmpty()) {page = Integer.parseInt(pageText);}
            if (sizeText != null && !sizeText.trim().isEmpty()) {size = Integer.parseInt(sizeText);}
            if (trangThaiText != null && !trangThaiText.trim().isEmpty()) {trangThai = TrangThaiKhuyenMai.valueOf(trangThaiText);}
            if (tuNgayText != null && !tuNgayText.trim().isEmpty()) {tuNgay = LocalDate.parse(tuNgayText);}
            if (denNgayText != null && !denNgayText.trim().isEmpty()) {denNgay = LocalDate.parse(denNgayText);}

        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new DomainException(400, "Tham số tìm kiếm sai định dạng.");
        }

        var result = service.danhSach(
                tuKhoa, trangThai, tuNgay, denNgay, page, size);

        writeJson(response, 200, result);
    }

// POST /api/quan-tri/khuyen-mai
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (maTuPath(request) != null) {
            response.setHeader("Allow", "GET, HEAD, PUT, DELETE, OPTIONS");
            throw new DomainException(405, "Đường dẫn này không hỗ trợ POST.");
        }

        KhuyenMaiDto.Request input = readBody(request);
        KhuyenMaiDto.Response result = service.tao(input);

        response.setHeader(
                "Location",
                request.getContextPath()
                        + "/api/quan-tri/khuyen-mai/"
                        + result.maKhuyenMai()
        );

        writeJson(response, 201, result);
    }

// PUT /api/quan-tri/khuyen-mai/{ma}
    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String ma = maTuPath(request);

        if (ma == null) {
            response.setHeader("Allow", "GET, HEAD, POST, OPTIONS");
            throw new DomainException(405, "Đường dẫn này không hỗ trợ PUT.");
        }

        KhuyenMaiDto.Request input = readBody(request);
        KhuyenMaiDto.Response result = service.capNhat(ma, input);

        writeJson(response, 200, result);
    }

// DELETE /api/quan-tri/khuyen-mai/{ma}
    @Override
    protected void doDelete(
            HttpServletRequest request,
            HttpServletResponse response) {

        String ma = maTuPath(request);

        if (ma == null) {
            response.setHeader("Allow", "GET, HEAD, POST, OPTIONS");
            throw new DomainException(405, "Đường dẫn này không hỗ trợ DELETE.");
        }

        service.xoa(ma);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

// helper
    private KhuyenMaiDto.Request readBody(HttpServletRequest request)
            throws IOException {

        try {
            KhuyenMaiDto.Request input = json.readValue(
                    request.getInputStream(), KhuyenMaiDto.Request.class);

            if (input == null) {
                throw new DomainException(400, "Thiếu dữ liệu khuyến mãi.");
            }

            return input;
        } catch (JsonProcessingException e) {
            throw new DomainException(400, "JSON không hợp lệ.");
        }
    }

    private String maTuPath(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null || path.isEmpty() || path.equals("/")) {
            return null;
        }

        String ma = path.substring(1);

        if (ma.trim().isEmpty() || ma.contains("/")) {
            throw new DomainException(404, "API không tồn tại.");
        }

        return ma;
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
