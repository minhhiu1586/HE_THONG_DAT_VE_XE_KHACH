package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiNhaXe;
import com.datvexe.dto.OperatorInputs;
import com.datvexe.security.*;
import com.datvexe.service.OperatorService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(urlPatterns = {"/api/quan-tri/nha-xe", "/api/quan-tri/nha-xe/*"})
public class OperatorController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private OperatorService operators;
    @Override public void init() { operators = new OperatorService(Jpa.factory()); }

    @Override protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8"); res.setContentType("application/json; charset=UTF-8");
        var identity = (com.datvexe.service.AccountService.Identity) req.getAttribute(AuthFilter.IDENTITY);
        CurrentUser user = identity.user();
        String[] parts = req.getPathInfo() == null || req.getPathInfo().equals("/")
            ? new String[0] : req.getPathInfo().substring(1).split("/");
        String method = req.getMethod(); Object result; int status = 200;
        try {
            if (parts.length == 0 && method.equals("GET")) {
                TrangThaiNhaXe state = parseState(req.getParameter("trangThai"));
                result = operators.list(user, req.getParameter("q"), state,
                    integer(req, "page", 1), integer(req, "size", 20));
            } else if (parts.length == 0 && method.equals("POST")) {
                var created = operators.create(user, body(req, OperatorInputs.NhaXe.class));
                result = created; status = 201;
                audit(res, user, "create", created.maNhaXe(), null);
            } else if (parts.length == 1 && method.equals("GET")) {
                result = operators.get(user, parts[0]);
            } else if (parts.length == 2 && parts[1].equals("anh-huong") && method.equals("GET")) {
                result = operators.impact(user, parts[0]);
            } else if (parts.length == 1 && method.equals("PUT")) {
                result = operators.update(user, parts[0], body(req, OperatorInputs.NhaXe.class));
                audit(res, user, "update", parts[0], null);
            } else if (parts.length == 1 && method.equals("DELETE")) {
                operators.delete(user, parts[0]); result = null; status = 204;
                audit(res, user, "delete", parts[0], null);
            } else if (parts.length == 2 && parts[1].equals("trang-thai") && method.equals("PUT")) {
                var changed = operators.state(user, parts[0], body(req, OperatorInputs.TrangThai.class));
                result = changed;
                audit(res, user, "state", parts[0], changed.nhaXe().trangThai().name()
                    + " upcoming=" + changed.anhHuong().chuyenSapKhoiHanh()
                    + " running=" + changed.anhHuong().chuyenDangChay()
                    + " paid=" + changed.anhHuong().veCoThanhToanThanhCong());
            } else if (parts.length == 2 && parts[1].equals("quan-ly") && method.equals("GET")) {
                result = operators.managers(user, parts[0]);
            } else if (parts.length == 2 && parts[1].equals("quan-ly") && method.equals("POST")) {
                var input = body(req, OperatorInputs.QuanLy.class);
                result = operators.grant(user, parts[0], input); status = 201;
                audit(res, user, "grant", parts[0], input.maNguoiDung());
            } else if (parts.length == 3 && parts[1].equals("quan-ly") && method.equals("DELETE")) {
                operators.revoke(user, parts[0], parts[2]); result = null; status = 204;
                audit(res, user, "revoke", parts[0], parts[2]);
            } else throw new AccountException(404, "RESOURCE_NOT_FOUND", "API không tồn tại.");
            res.setStatus(status); if (result != null) json.writeValue(res.getOutputStream(), result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
            throw AccountException.invalid("body", "Dữ liệu yêu cầu không hợp lệ.");
        }
    }
    private <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        T result = json.readValue(req.getInputStream(), type);
        if (result == null) throw AccountException.invalid("body", "Nội dung yêu cầu là bắt buộc.");
        return result;
    }
    private static int integer(HttpServletRequest req, String key, int fallback) {
        String value = req.getParameter(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw AccountException.invalid(key, "Giá trị phân trang không hợp lệ."); }
    }
    private static TrangThaiNhaXe parseState(String value) {
        if (value == null || value.isBlank()) return null;
        try { return TrangThaiNhaXe.valueOf(value); }
        catch (IllegalArgumentException error) { throw AccountException.invalid("trangThai", "Trạng thái nhà xe không hợp lệ."); }
    }
    private void audit(HttpServletResponse res, CurrentUser user,
                       String action, String operator, String target) {
        getServletContext().log("Operator action=" + action + " actor=" + user.maNguoiDung()
            + " operator=" + operator + (target == null ? "" : " target=" + target)
            + " trace=" + res.getHeader("X-Trace-Id"));
    }
}
