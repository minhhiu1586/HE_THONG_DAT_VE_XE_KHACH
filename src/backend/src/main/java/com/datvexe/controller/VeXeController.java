package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.security.AccountException;
import com.datvexe.service.DomainException;
import com.datvexe.service.ThanhToanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/api/ve-xe/*")
public class VeXeController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private ThanhToanService service;

    @Override public void init() { service = new ThanhToanService(Jpa.factory()); }

    @Override protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String info = request.getPathInfo();
        if (info == null || !info.matches("/[A-Za-z0-9_-]{1,40}"))
            throw new DomainException(404, "API không tồn tại.");
        if (!request.getParameterMap().isEmpty()) throw AccountException.invalid("query", "API không nhận query.");
        if (request.getMethod().equals("OPTIONS")) {
            response.setHeader("Allow", "GET, HEAD, OPTIONS");
            response.setStatus(204);
            return;
        }
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) {
            response.setHeader("Allow", "GET, HEAD, OPTIONS");
            throw new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
        }
        super.service(request, response);
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var result = service.ticket(request.getPathInfo().substring(1));
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), result);
    }
}
