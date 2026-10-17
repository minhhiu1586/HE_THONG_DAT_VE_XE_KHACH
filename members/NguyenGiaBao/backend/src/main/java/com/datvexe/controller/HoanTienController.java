package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.service.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;

@WebServlet("/api/hoan-tien/*")
public final class HoanTienController extends HttpServlet {
    private HoanVeService service;
    @Override public void init() { service = new HoanVeService(Jpa.factory()); }
    @Override protected void service(HttpServletRequest request,HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || !path.matches("/[A-Za-z0-9_-]{1,40}(/(xac-minh|thu-lai))?")) throw new DomainException(404,"API không tồn tại.");
        RefundHttp.query(request,Set.of());
        String[] parts = path.substring(1).split("/");
        String allow = parts.length == 1 ? "GET, HEAD, OPTIONS" : "POST, OPTIONS";
        if (request.getMethod().equals("OPTIONS")) { response.setHeader("Allow",allow); response.setStatus(204); return; }
        if (parts.length == 1 && Set.of("GET","HEAD").contains(request.getMethod())) {
            var result = service.get(parts[0]);
            if (request.getMethod().equals("HEAD")) response.setStatus(200); else RefundHttp.write(response,200,result);
            return;
        }
        if (parts.length == 2 && request.getMethod().equals("POST")) {
            RefundHttp.empty(request);
            if (parts[1].equals("xac-minh")) RefundHttp.write(response,200,service.verify(parts[0],request.getRemoteAddr()));
            else {
                var result = service.retry(parts[0],request.getHeader("Idempotency-Key"),request.getRemoteAddr());
                RefundHttp.write(response,result.status(),result.body());
            }
            return;
        }
        response.setHeader("Allow",allow); throw new DomainException(405,"Phương thức không được hỗ trợ.");
    }
}
