package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.ChinhSachHoanVeDto;
import com.datvexe.security.AccountException;
import com.datvexe.service.ChinhSachHoanVeService;
import com.datvexe.service.DomainException;
import com.fasterxml.jackson.core.JsonParser;
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
import java.util.Set;

@WebServlet(urlPatterns = {
        "/api/quan-tri/chinh-sach-hoan-ve",
        "/api/quan-tri/chinh-sach-hoan-ve/*"
})
public class ChinhSachHoanVeController extends HttpServlet {
    private static final String ADMIN_PATH = "/api/quan-tri/chinh-sach-hoan-ve";
    private static final Set<String> LIST_QUERY = Set.of("page", "size");

    private final ObjectMapper json = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ChinhSachHoanVeService service;

    @Override
    public void init() {
        service = new ChinhSachHoanVeService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        switch (request.getMethod()) {
            case "GET", "HEAD", "POST", "PUT", "OPTIONS" -> super.service(request, response);
            default -> throw methodNotAllowed(response, path(request).kind());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        DuongDan route = path(request);
        switch (route.kind()) {
            case DANH_SACH -> {
                validateQuery(request, LIST_QUERY);
                int page = parsePositiveInt(request.getParameter("page"), 1, "page");
                int size = parsePositiveInt(request.getParameter("size"), 20, "size");
                if (size > 100) throw AccountException.invalid("size", "size phải từ 1 đến 100.");
                writeJson(response, 200, service.danhSach(page, size));
            }
            case CHI_TIET -> {
                validateQuery(request, Set.of());
                writeJson(response, 200, service.chiTiet(route.ma()));
            }
            case TRANG_THAI -> throw methodNotAllowed(response, route.kind());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        DuongDan route = path(request);
        if (route.kind() != LoaiDuongDan.DANH_SACH) {
            throw methodNotAllowed(response, route.kind());
        }
        validateQuery(request, Set.of());
        ChinhSachHoanVeDto.Response result = service.tao(readRequest(request, true));
        String encodedId = URLEncoder.encode(result.maChinhSach(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Location", request.getContextPath() + ADMIN_PATH + "/" + encodedId);
        writeJson(response, 201, result);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        DuongDan route = path(request);
        validateQuery(request, Set.of());
        switch (route.kind()) {
            case CHI_TIET -> writeJson(response, 200,
                    service.capNhat(route.ma(), readRequest(request, false)));
            case TRANG_THAI -> writeJson(response, 200,
                    service.capNhatTrangThai(route.ma(), readStatus(request).trangThai()));
            case DANH_SACH -> throw methodNotAllowed(response, route.kind());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Allow", allow(path(request).kind()));
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private DuongDan path(HttpServletRequest request) {
        if (!ADMIN_PATH.equals(request.getServletPath())) throw notFound();
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            return new DuongDan(LoaiDuongDan.DANH_SACH, null);
        }
        if (!pathInfo.startsWith("/")) throw notFound();
        String[] segments = pathInfo.substring(1).split("/", -1);
        if (segments.length == 1 && !segments[0].isBlank()) {
            return new DuongDan(LoaiDuongDan.CHI_TIET, validateId(segments[0]));
        }
        if (segments.length == 2 && !segments[0].isBlank()
                && "trang-thai".equals(segments[1])) {
            return new DuongDan(LoaiDuongDan.TRANG_THAI, validateId(segments[0]));
        }
        throw notFound();
    }

    private String validateId(String value) {
        if (value.length() > 40) throw AccountException.invalid("maChinhSach", "Mã tối đa 40 ký tự.");
        return value;
    }

    private ChinhSachHoanVeDto.Request readRequest(HttpServletRequest request, boolean creating)
            throws IOException {
        try {
            JsonNode body = readObject(request);
            if (!creating && body.has("maChinhSach")) {
                throw AccountException.invalid("maChinhSach", "Khi sửa, mã chỉ lấy từ URL.");
            }
            requireTextFields(body, "maChinhSach", "tenChinhSach", "phiHoan", "trangThai");
            JsonNode hours = body.get("soGioToiThieuTruocKhoiHanh");
            if (hours != null && !hours.isNull() && !hours.isIntegralNumber()) {
                throw AccountException.invalid(
                        "soGioToiThieuTruocKhoiHanh", "Số giờ phải là JSON số nguyên.");
            }
            JsonNode rate = body.get("tyLeHoan");
            if (rate != null && !rate.isNull() && !rate.isNumber()) {
                throw AccountException.invalid("tyLeHoan", "Tỷ lệ hoàn phải là JSON number.");
            }
            JsonNode allowPromotion = body.get("choPhepHoanVeKhuyenMai");
            if (allowPromotion != null && !allowPromotion.isNull() && !allowPromotion.isBoolean()) {
                throw AccountException.invalid("choPhepHoanVeKhuyenMai", "Trường này phải là boolean.");
            }
            return json.treeToValue(body, ChinhSachHoanVeDto.Request.class);
        } catch (JsonProcessingException e) {
            throw AccountException.invalid("body", "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private ChinhSachHoanVeDto.StatusRequest readStatus(HttpServletRequest request) throws IOException {
        try {
            JsonNode body = readObject(request);
            if (!body.has("trangThai") || body.get("trangThai").isNull()
                    || !body.get("trangThai").isTextual()) {
                throw AccountException.invalid("trangThai", "Trạng thái phải là chuỗi hợp lệ.");
            }
            return json.treeToValue(body, ChinhSachHoanVeDto.StatusRequest.class);
        } catch (JsonProcessingException e) {
            throw AccountException.invalid("body", "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private JsonNode readObject(HttpServletRequest request) throws IOException {
        JsonNode body = json.readTree(request.getInputStream());
        if (body == null || !body.isObject()) {
            throw AccountException.invalid("body", "Body phải là một JSON object.");
        }
        return body;
    }

    private void requireTextFields(JsonNode body, String... fields) {
        for (String field : fields) {
            JsonNode value = body.get(field);
            if (value != null && !value.isNull() && !value.isTextual()) {
                throw AccountException.invalid(field, "Trường này phải là chuỗi.");
            }
        }
    }

    private int parsePositiveInt(String value, int defaultValue, String field) {
        if (value == null) return defaultValue;
        try {
            int result = Integer.parseInt(value);
            if (result < 1) throw new NumberFormatException("must be positive");
            return result;
        } catch (NumberFormatException e) {
            throw AccountException.invalid(field, field + " phải là số nguyên dương.");
        }
    }

    private void validateQuery(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw AccountException.invalid("query", "Có tham số không được hỗ trợ.");
        }
    }

    private DomainException methodNotAllowed(HttpServletResponse response, LoaiDuongDan kind) {
        response.setHeader("Allow", allow(kind));
        return new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
    }

    private String allow(LoaiDuongDan kind) {
        return switch (kind) {
            case DANH_SACH -> "GET, HEAD, POST, OPTIONS";
            case CHI_TIET -> "GET, HEAD, PUT, OPTIONS";
            case TRANG_THAI -> "PUT, OPTIONS";
        };
    }

    private DomainException notFound() {
        return new DomainException(404, "API không tồn tại.");
    }

    private void writeJson(HttpServletResponse response, int status, Object result) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setStatus(status);
        json.writeValue(response.getOutputStream(), result);
    }

    private enum LoaiDuongDan { DANH_SACH, CHI_TIET, TRANG_THAI }
    private record DuongDan(LoaiDuongDan kind, String ma) {}
}
