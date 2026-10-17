package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.AccountInputs;
import com.datvexe.security.*;
import com.datvexe.service.AccountService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@WebServlet(urlPatterns = {"/api/auth/*", "/api/tai-khoan/*", "/api/quan-tri/tai-khoan", "/api/quan-tri/tai-khoan/*"})
public class AccountController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private AccountService accounts;
    private record Window(long since, int count) {}
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    @Override public void init() { accounts = new AccountService(Jpa.factory()); }
    private void throttle(HttpServletRequest req, HttpServletResponse res) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> now - e.getValue().since() >= 60_000);
        AtomicBoolean denied = new AtomicBoolean();
        String key = req.getRemoteAddr(); // Do not trust client-provided forwarded IP headers.
        attempts.compute(key, (k, old) -> {
            Window w = old == null ? new Window(now, 0) : old;
            if (w.count() >= 20) denied.set(true);
            return new Window(w.since(), w.count() + 1);
        });
        if (denied.get() || attempts.size() > 10_000) {
            res.setHeader("Retry-After", "60");
            throw new AccountException(429, "TOO_MANY_REQUESTS", "Bạn đã thử quá nhiều lần. Vui lòng chờ một phút.");
        }
    }
    @Override protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8"); res.setContentType("application/json; charset=UTF-8");
        String path = req.getRequestURI().substring(req.getContextPath().length()); String method = req.getMethod();
        var identity = (AccountService.Identity) req.getAttribute(AuthFilter.IDENTITY);
        Object result;
        int status = 200;
        try {
            if (path.equals("/api/auth/dang-ky") && method.equals("POST")) {
                throttle(req, res); result = accounts.register(body(req, AccountInputs.DangKy.class)); status = 201;
                res.setHeader("Location", "/api/tai-khoan/me");
            } else if (path.equals("/api/auth/dang-nhap") && method.equals("POST")) {
                throttle(req, res); var login = accounts.login(body(req, AccountInputs.DangNhap.class));
                AuthFilter.cookie(res, login.token(), Math.max(0, login.hetHan().getEpochSecond() - Instant.now().getEpochSecond()));
                result = Map.of("nguoiDung", login.nguoiDung(), "csrfToken", login.csrfToken(), "hetHan", login.hetHan());
            } else if (path.equals("/api/auth/dang-xuat") && method.equals("POST")) {
                accounts.logout(AuthFilter.token(req), identity.user()); AuthFilter.cookie(res, "", 0); result = null; status = 204;
            } else if (path.equals("/api/tai-khoan/me") && method.equals("GET")) {
                result = Map.of("nguoiDung", accounts.profile(identity.user()), "csrfToken", identity.csrfToken(), "hetHan", identity.hetHan());
            } else if (path.equals("/api/tai-khoan/me") && method.equals("PUT")) {
                result = accounts.update(identity.user(), body(req, AccountInputs.HoSo.class));
            } else if (path.equals("/api/tai-khoan/me/mat-khau") && method.equals("PUT")) {
                accounts.changePassword(identity.user(), body(req, AccountInputs.DoiMatKhau.class));
                AuthFilter.cookie(res, "", 0); result = null; status = 204;
            } else if (path.equals("/api/quan-tri/tai-khoan") && method.equals("GET")) {
                result = accounts.accounts(identity.user(), integer(req, "page", 1), integer(req, "size", 20));
            } else if (path.matches("/api/quan-tri/tai-khoan/[^/]+/trang-thai") && method.equals("PUT")) {
                String id = path.split("/")[4];
                var changed = accounts.status(identity.user(), id, body(req, AccountInputs.TrangThai.class));
                // Audit only actor, target and result; no password, token or profile data.
                getServletContext().log("Account status changed actor=" + identity.user().maNguoiDung()
                    + " target=" + changed.maNguoiDung() + " status=" + changed.trangThai() + " trace=" + res.getHeader("X-Trace-Id"));
                result = changed;
            } else if (path.startsWith("/api/tai-khoan/") && !path.startsWith("/api/tai-khoan/me")) {
                throw AccessControl.denied();
            } else throw new AccountException(404, "RESOURCE_NOT_FOUND", "API không tồn tại.");
            res.setStatus(status); if (result != null) json.writeValue(res.getOutputStream(), result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            throw AccountException.invalid("body", "Dữ liệu không hợp lệ hoặc chứa trường không được phép.");
        }
    }
    private <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        T input = json.readValue(req.getInputStream(), type);
        if (input == null) throw AccountException.invalid("body", "Nội dung yêu cầu là bắt buộc.");
        return input;
    }
    private int integer(HttpServletRequest req, String key, int fallback) {
        String value = req.getParameter(key); return value == null ? fallback : Integer.parseInt(value);
    }
}
