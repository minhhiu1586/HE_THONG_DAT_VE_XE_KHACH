package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.AccountInputs;
import com.datvexe.security.*;
import com.datvexe.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebServlet(urlPatterns = {"/api/quan-tri/tai-khoan", "/api/quan-tri/tai-khoan/*"})
public class AdminAccountController extends HttpServlet {
    private AdminAccountService accounts;

    @Override
    public void init() {
        accounts = new AdminAccountService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AccountHttp.prepare(response);
        try {
            String path = AccountHttp.path(request);
            if (path.equals("/api/quan-tri/tai-khoan") && request.getMethod().equals("GET"))
                list(request, response);
            else if (path.matches("/api/quan-tri/tai-khoan/[^/]+/trang-thai")
                    && request.getMethod().equals("PUT"))
                status(request, response, path.split("/")[4]);
            else throw AccountHttp.notFound();
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw AccountException.invalid(
                    "body", "Dữ liệu không hợp lệ hoặc chứa trường không được phép.");
        }
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var user = AccountHttp.identity(request).user();
        var result =
                accounts.accounts(
                        user,
                        AccountHttp.integer(request, "page", 1),
                        AccountHttp.integer(request, "size", 20));
        AccountHttp.write(response, 200, result);
    }

    private void status(HttpServletRequest request, HttpServletResponse response, String id)
            throws IOException {
        var user = AccountHttp.identity(request).user();
        var result =
                accounts.status(user, id, AccountHttp.body(request, AccountInputs.TrangThai.class));
        getServletContext()
                .log(
                        "Account status changed actor="
                                + user.maNguoiDung()
                                + " target="
                                + result.maNguoiDung()
                                + " status="
                                + result.trangThai()
                                + " trace="
                                + response.getHeader("X-Trace-Id"));
        AccountHttp.write(response, 200, result);
    }
}
