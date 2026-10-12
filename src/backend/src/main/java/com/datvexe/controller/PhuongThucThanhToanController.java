package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.PhuongThucThanhToanDto;
import com.datvexe.security.AccountException;
import com.datvexe.service.DomainException;
import com.datvexe.service.PhuongThucThanhToanService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        service = new PhuongThucThanhToanService(Jpa.factory());
    }

    // Chặn method ngoài phạm vi và giữ Allow đúng theo từng đường dẫn.
    @Override
    protected void service(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        DuongDan path = duongDan(request);

        switch (request.getMethod()) {
            case "GET", "HEAD", "POST", "PUT", "OPTIONS" ->
                    super.service(request, response);
            default -> throw methodNotAllowed(response, path.loai());
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        DuongDan path = duongDan(request);
        kiemTraQuery(request);

        switch (path.loai()) {
            case CONG_KHAI -> writeJson(response, 200, service.danhSachKhaDung());
            case DANH_SACH -> writeJson(response, 200, service.danhSach());
            case CHI_TIET -> writeJson(response, 200, service.chiTiet(path.ma()));
            case TRANG_THAI -> throw methodNotAllowed(response, path.loai());
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        DuongDan path = duongDan(request);

        if (path.loai() != LoaiDuongDan.DANH_SACH) {
            throw methodNotAllowed(response, path.loai());
        }

        kiemTraQuery(request);

        PhuongThucThanhToanDto.Request input = readRequest(request, true);
        PhuongThucThanhToanDto.Response result = service.tao(input);
        String encodedMa = URLEncoder.encode(
                result.maPhuongThuc(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setHeader("Location", request.getContextPath()
                + ADMIN_PATH + "/" + encodedMa);
        writeJson(response, 201, result);
    }

    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        DuongDan path = duongDan(request);

        if (path.loai() == LoaiDuongDan.CHI_TIET) {
            kiemTraQuery(request);

            PhuongThucThanhToanDto.Request input = readRequest(request, false);
            writeJson(response, 200, service.capNhat(path.ma(), input));
            return;
        }

        if (path.loai() == LoaiDuongDan.TRANG_THAI) {
            kiemTraQuery(request);

            PhuongThucThanhToanDto.CapNhatTrangThai input = readStatus(request);
            writeJson(response, 200,
                    service.capNhatTrangThai(path.ma(), input.trangThai()));
            return;
        }

        throw methodNotAllowed(response, path.loai());
    }

    @Override
    protected void doOptions(
            HttpServletRequest request,
            HttpServletResponse response) {

        DuongDan path = duongDan(request);
        response.setHeader("Allow", allow(path.loai()));
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private DuongDan duongDan(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();

        if (PUBLIC_PATH.equals(servletPath)) {
            if (pathInfo == null || pathInfo.isEmpty()) {
                return new DuongDan(LoaiDuongDan.CONG_KHAI, null);
            }
            throw notFound();
        }

        if (!ADMIN_PATH.equals(servletPath)) {
            throw notFound();
        }

        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            return new DuongDan(LoaiDuongDan.DANH_SACH, null);
        }

        if (!pathInfo.startsWith("/")) {
            throw notFound();
        }

        // Giữ đoạn rỗng cuối để từ chối /ma/ và /ma/trang-thai/.
        String[] segments = pathInfo.substring(1).split("/", -1);

        if (segments.length == 1 && !segments[0].isBlank()) {
            return new DuongDan(LoaiDuongDan.CHI_TIET, kiemTraMa(segments[0]));
        }

        if (segments.length == 2
                && !segments[0].isBlank()
                && "trang-thai".equals(segments[1])) {
            return new DuongDan(LoaiDuongDan.TRANG_THAI, kiemTraMa(segments[0]));
        }

        throw notFound();
    }

    private String kiemTraMa(String ma) {
        if (ma.length() > 40) {
            throw AccountException.invalid(
                    "maPhuongThuc", "Mã phương thức tối đa 40 ký tự.");
        }
        return ma;
    }

    private DomainException notFound() {
        return new DomainException(404, "API không tồn tại.");
    }

    private DomainException methodNotAllowed(
            HttpServletResponse response,
            LoaiDuongDan path) {

        response.setHeader("Allow", allow(path));
        return new DomainException(
                405, "Phương thức không được hỗ trợ tại đường dẫn này.");
    }

    private String allow(LoaiDuongDan path) {
        return switch (path) {
            case CONG_KHAI -> "GET, HEAD, OPTIONS";
            case DANH_SACH -> "GET, HEAD, POST, OPTIONS";
            case CHI_TIET -> "GET, HEAD, PUT, OPTIONS";
            case TRANG_THAI -> "PUT, OPTIONS";
        };
    }

    private void kiemTraQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw AccountException.invalid(
                    "query", "Đường dẫn này không hỗ trợ tham số query.");
        }
    }

    private PhuongThucThanhToanDto.Request readRequest(
            HttpServletRequest request,
            boolean taoMoi) throws IOException {

        try {
            JsonNode body = readObject(request);

            if (!taoMoi && body.has("maPhuongThuc")) {
                throw AccountException.invalid(
                        "maPhuongThuc", "Khi sửa, mã chỉ lấy từ URL.");
            }

            kiemTraChuoi(body, "maPhuongThuc", "tenPhuongThuc", "moTa");
            return json.treeToValue(body, PhuongThucThanhToanDto.Request.class);
        } catch (JsonProcessingException e) {
            throw AccountException.invalid(
                    "body", "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private PhuongThucThanhToanDto.CapNhatTrangThai readStatus(
            HttpServletRequest request) throws IOException {

        try {
            JsonNode body = readObject(request);
            JsonNode trangThai = body.get("trangThai");

            if (trangThai == null || trangThai.isNull() || !trangThai.isTextual()) {
                throw AccountException.invalid(
                        "trangThai", "Trạng thái phải là chuỗi hợp lệ.");
            }

            return json.treeToValue(
                    body, PhuongThucThanhToanDto.CapNhatTrangThai.class);
        } catch (JsonProcessingException e) {
            throw AccountException.invalid(
                    "body", "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private JsonNode readObject(HttpServletRequest request) throws IOException {
        JsonNode body = json.readTree(request.getInputStream());

        if (body == null || !body.isObject()) {
            throw AccountException.invalid("body", "Body phải là một JSON object.");
        }

        return body;
    }

    private void kiemTraChuoi(JsonNode body, String... fields) {
        for (String field : fields) {
            JsonNode value = body.get(field);

            if (value != null && !value.isNull() && !value.isTextual()) {
                throw AccountException.invalid(field, "Trường này phải là chuỗi.");
            }
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

    private enum LoaiDuongDan {
        CONG_KHAI,
        DANH_SACH,
        CHI_TIET,
        TRANG_THAI
    }

    private record DuongDan(LoaiDuongDan loai, String ma) {}
}
