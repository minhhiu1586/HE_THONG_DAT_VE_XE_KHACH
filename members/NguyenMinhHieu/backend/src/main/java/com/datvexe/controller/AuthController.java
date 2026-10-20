package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.AccountInputs;
import com.datvexe.dto.AccountResponses.Session;
import com.datvexe.security.*;
import com.datvexe.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.time.Instant;

@WebServlet("/api/auth/*")
public class AuthController extends HttpServlet {
    private AuthService auth;
    private final LoginRateLimiter rateLimiter = new LoginRateLimiter();

    @Override
    public void init() {
        auth = new AuthService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AccountHttp.prepare(response);
        try {
            String action = request.getMethod() + " " + AccountHttp.path(request);
            switch (action) {
                case "POST /api/auth/dang-ky" -> register(request, response);
                case "POST /api/auth/dang-nhap" -> login(request, response);
                case "POST /api/auth/dang-xuat" -> logout(request, response);
                default -> throw AccountHttp.notFound();
            }
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw AccountException.invalid(
                    "body", "Dữ liệu không hợp lệ hoặc chứa trường không được phép.");
        }
    }

    private void throttle(HttpServletRequest request, HttpServletResponse response) {
        try {
            rateLimiter.check(request.getRemoteAddr());
        } catch (AccountException error) {
            response.setHeader("Retry-After", "60");
            throw error;
        }
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        throttle(request, response);
        var profile = auth.register(AccountHttp.body(request, AccountInputs.DangKy.class));
        response.setHeader("Location", "/api/tai-khoan/me");
        AccountHttp.write(response, 201, profile);
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        throttle(request, response);
        var login = auth.login(AccountHttp.body(request, AccountInputs.DangNhap.class));
        AuthCookies.write(
                response,
                login.token(),
                Math.max(0, login.hetHan().getEpochSecond() - Instant.now().getEpochSecond()));
        AccountHttp.write(
                response, 200, new Session(login.nguoiDung(), login.csrfToken(), login.hetHan()));
    }

    private void logout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        auth.logout(AuthCookies.token(request), AccountHttp.identity(request).user());
        AuthCookies.write(response, "", 0);
        AccountHttp.write(response, 204, null);
    }
}
