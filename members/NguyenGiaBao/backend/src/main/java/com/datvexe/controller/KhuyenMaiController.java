package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import com.datvexe.dto.KhuyenMaiDto;
import com.datvexe.security.AccountException;
import com.datvexe.service.DomainException;
import com.datvexe.service.KhuyenMaiService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

@WebServlet(urlPatterns = {
        "/api/quan-tri/khuyen-mai",
        "/api/quan-tri/khuyen-mai/*"
})
public class KhuyenMaiController extends HttpServlet {

    private static final Set<String> FILTERS = Set.of(
            "tuKhoa", "trangThai", "tuNgay", "denNgay", "page", "size"
    );

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private KhuyenMaiService service;

    @Override
    public void init() {
        service = new KhuyenMaiService(Jpa.factory());
    }

    // Chặn method ngoài phạm vi; HttpServlet vẫn phân phối đến doGet/doPost...
    @Override
    protected void service(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        switch (request.getMethod()) {
            case "GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS" ->
                    super.service(request, response);

            default ->
                    throw methodNotAllowed(response, maTuPath(request) == null);
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String ma = maTuPath(request);

        // GET /khuyen-mai/{ma}
        if (ma != null) {
            kiemTraQuery(request, Set.of());

            writeJson(response, 200, service.chiTiet(ma));
            return;
        }

        // GET /khuyen-mai
        kiemTraQuery(request, FILTERS);

        int page;
        int size;
        TrangThaiKhuyenMai trangThai;
        LocalDate tuNgay;
        LocalDate denNgay;

        try {
            page = Integer.parseInt(param(request, "page", "1"));
            size = Integer.parseInt(param(request, "size", "20"));

            String trangThaiValue = request.getParameter("trangThai");
            trangThai = trangThaiValue == null
                    ? null
                    : TrangThaiKhuyenMai.valueOf(trangThaiValue);

            String tuNgayValue = request.getParameter("tuNgay");
            tuNgay = tuNgayValue == null ? null : LocalDate.parse(tuNgayValue);

            String denNgayValue = request.getParameter("denNgay");
            denNgay = denNgayValue == null ? null : LocalDate.parse(denNgayValue);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw AccountException.invalid(
                    "query",
                    "page, size, trạng thái hoặc ngày không đúng định dạng."
            );
        }

        if (page < 1) {
            throw AccountException.invalid("page", "page phải từ 1.");
        }

        if (size < 1 || size > 100) {
            throw AccountException.invalid(
                    "size", "size phải từ 1 đến 100.");
        }

        String tuKhoa = request.getParameter("tuKhoa");

        if (tuKhoa != null && tuKhoa.length() > 200) {
            throw AccountException.invalid("tuKhoa", "Từ khóa quá dài.");
        }

        if (tuNgay != null && denNgay != null && tuNgay.isAfter(denNgay)) {
            throw AccountException.invalid(
                    "denNgay", "denNgay phải từ tuNgay trở đi.");
        }

        var result = service.danhSach(
                tuKhoa, trangThai, tuNgay, denNgay, page, size);

        writeJson(response, 200, result);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // POST chỉ được dùng trên đường dẫn danh sách.
        if (maTuPath(request) != null) {
            throw methodNotAllowed(response, false);
        }

        kiemTraQuery(request, Set.of());

        KhuyenMaiDto.Request input = readBody(request, true);
        KhuyenMaiDto.Response result = service.tao(input);

        String encodedMa = URLEncoder.encode(
                result.maKhuyenMai(),
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        response.setHeader(
                "Location",
                request.getContextPath()
                        + request.getServletPath()
                        + "/"
                        + encodedMa
        );

        writeJson(response, 201, result);
    }

    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String ma = maTuPath(request);

        // PUT phải có mã trên URL.
        if (ma == null) {
            throw methodNotAllowed(response, true);
        }

        kiemTraQuery(request, Set.of());

        KhuyenMaiDto.Request input = readBody(request, false);
        KhuyenMaiDto.Response result = service.capNhat(ma, input);

        writeJson(response, 200, result);
    }

    @Override
    protected void doDelete(
            HttpServletRequest request,
            HttpServletResponse response) {

        String ma = maTuPath(request);

        if (ma == null) {
            throw methodNotAllowed(response, true);
        }

        kiemTraQuery(request, Set.of());

        service.xoa(ma);

        // 204 không có response body.
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private String maTuPath(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null || path.isEmpty() || path.equals("/")) {
            return null;
        }

        String ma = path.substring(1);

        if (ma.isBlank() || ma.contains("/")) {
            throw new DomainException(404, "API không tồn tại.");
        }

        if (ma.length() > 40) {
            throw AccountException.invalid(
                    "maKhuyenMai", "Mã tối đa 40 ký tự.");
        }

        return ma;
    }

    private DomainException methodNotAllowed(
            HttpServletResponse response,
            boolean collection) {

        response.setHeader(
                "Allow",
                collection
                        ? "GET, HEAD, POST, OPTIONS"
                        : "GET, HEAD, PUT, DELETE, OPTIONS"
        );

        return new DomainException(
                405, "Phương thức không được hỗ trợ tại đường dẫn này.");
    }

    private void kiemTraQuery(
            HttpServletRequest request,
            Set<String> allowed) {

        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw AccountException.invalid(
                    "query", "Có tham số không được hỗ trợ.");
        }
    }

    private String param(
            HttpServletRequest request,
            String name,
            String defaultValue) {

        String value = request.getParameter(name);
        return value == null ? defaultValue : value;
    }

    private KhuyenMaiDto.Request readBody(
            HttpServletRequest request,
            boolean taoMoi) throws IOException {

        try {
            JsonNode body = json.readTree(request.getInputStream());

            if (body == null || !body.isObject()) {
                throw AccountException.invalid(
                        "body", "Body phải là một JSON object.");
            }

            // PUT lấy mã từ URL, không nhận mã trong body, kể cả null.
            if (!taoMoi && body.has("maKhuyenMai")) {
                throw AccountException.invalid(
                        "maKhuyenMai", "Khi sửa, mã chỉ lấy từ URL.");
            }

            // Tránh Jackson tự chuyển JSON number thành String.
            for (String field : Set.of(
                    "maKhuyenMai", "tenKhuyenMai", "moTa",
                    "loaiGiam", "giamToiDa", "donToiThieu",
                    "ngayBatDau", "ngayKetThuc")) {

                JsonNode value = body.get(field);

                if (value != null
                        && !value.isNull()
                        && !value.isTextual()) {

                    throw AccountException.invalid(
                            field, "Trường này phải là chuỗi.");
                }
            }

            JsonNode soLuong = body.get("soLuong");

            if (soLuong != null
                    && !soLuong.isNull()
                    && !soLuong.isIntegralNumber()) {

                throw AccountException.invalid(
                        "soLuong", "Số lượng phải là JSON số nguyên.");
            }

            return json.treeToValue(body, KhuyenMaiDto.Request.class);

        } catch (JsonProcessingException e) {
            throw AccountException.invalid(
                    "body",
                    "JSON sai định dạng, kiểu dữ liệu hoặc tên trường."
            );
        }
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
