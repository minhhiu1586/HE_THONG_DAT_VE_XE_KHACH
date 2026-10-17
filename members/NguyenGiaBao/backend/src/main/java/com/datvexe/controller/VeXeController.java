package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.security.AccountException;
import com.datvexe.service.DomainException;
import com.datvexe.service.ThanhToanService;
import com.datvexe.service.HoanVeService;
import com.datvexe.dto.HoanVeDto;
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
    private HoanVeService refunds;

    @Override public void init() {
        service = new ThanhToanService(Jpa.factory());
        refunds = new HoanVeService(Jpa.factory());
    }

    @Override protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String info = request.getPathInfo();
        if (info != null && info.matches("/[A-Za-z0-9_-]{1,40}/(chinh-sach-hoan|hoan-tien)")) {
            String[] parts = info.substring(1).split("/");
            boolean policy = parts[1].equals("chinh-sach-hoan");
            String allow = policy ? "GET, HEAD, OPTIONS" : "GET, HEAD, POST, OPTIONS";
            boolean reading = request.getMethod().equals("GET") || request.getMethod().equals("HEAD");
            RefundHttp.query(request,policy || !reading ? java.util.Set.of() : java.util.Set.of("page","size","sort"));
            if (request.getMethod().equals("OPTIONS")) { response.setHeader("Allow",allow); response.setStatus(204); return; }
            if (request.getMethod().equals("GET") || request.getMethod().equals("HEAD")) {
                Object result = policy ? refunds.policy(parts[0]) : refunds.history(parts[0],RefundHttp.integer(request,"page",1),RefundHttp.integer(request,"size",20),request.getParameter("sort"));
                if (request.getMethod().equals("HEAD")) response.setStatus(200); else RefundHttp.write(response,200,result);
                return;
            }
            if (!policy && request.getMethod().equals("POST")) {
                var result = refunds.createTicket(parts[0],RefundHttp.body(request,HoanVeDto.SingleRequest.class),request.getHeader("Idempotency-Key"),request.getRemoteAddr());
                response.setHeader("Location",request.getContextPath()+"/api/hoan-tien/"+((HoanVeDto.Response.class.isInstance(result.body()))?((HoanVeDto.Response)result.body()).maHoanTien():((com.fasterxml.jackson.databind.JsonNode)result.body()).path("maHoanTien").asText()));
                RefundHttp.write(response,result.status(),result.body()); return;
            }
            response.setHeader("Allow",allow); throw new DomainException(405,"Phương thức không được hỗ trợ.");
        }
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
