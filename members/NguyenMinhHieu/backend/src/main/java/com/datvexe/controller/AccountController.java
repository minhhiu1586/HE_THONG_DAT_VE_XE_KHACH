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

@WebServlet("/api/tai-khoan/*")
public class AccountController extends HttpServlet {
    private AccountService accounts;

    @Override
    public void init() {
        accounts = new AccountService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AccountHttp.prepare(response);
        try {
            String path = AccountHttp.path(request);
            switch (request.getMethod() + " " + path) {
                case "GET /api/tai-khoan/me" -> profile(request, response);
                case "PUT /api/tai-khoan/me" -> update(request, response);
                case "PUT /api/tai-khoan/me/mat-khau" -> changePassword(request, response);
                default -> {
                    if (path.startsWith("/api/tai-khoan/") && !path.startsWith("/api/tai-khoan/me"))
                        throw AccessControl.denied();
                    throw AccountHttp.notFound();
                }
            }
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw AccountException.invalid(
                    "body", "Dữ liệu không hợp lệ hoặc chứa trường không được phép.");
        }
    }

    private void profile(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var identity = AccountHttp.identity(request);
        AccountHttp.write(
                response,
                200,
                new Session(
                        accounts.profile(identity.user()),
                        identity.csrfToken(),
                        identity.hetHan()));
    }

    private void update(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var input = AccountHttp.body(request, AccountInputs.HoSo.class);
        AccountHttp.write(
                response, 200, accounts.update(AccountHttp.identity(request).user(), input));
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        accounts.changePassword(
                AccountHttp.identity(request).user(),
                AccountHttp.body(request, AccountInputs.DoiMatKhau.class));
        AuthCookies.write(response, "", 0);
        AccountHttp.write(response, 204, null);
    }
}
