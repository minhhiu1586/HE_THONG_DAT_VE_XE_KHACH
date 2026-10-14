package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.KiemVeDto;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.DomainException;
import com.datvexe.service.KiemVeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/kiem-ve/*")
public class KiemVeController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private KiemVeService service;

    @Override
    public void init() {
        service = new KiemVeService(Jpa.factory());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json; charset=UTF-8");

        String info = req.getPathInfo();
        String method = req.getMethod();

        if (method.equals("OPTIONS")) {
            res.setHeader("Allow", "POST, OPTIONS");
            res.setStatus(204);
            return;
        }

        try {
            if ("/chuyen-xe".equals(info) && method.equals("GET")) {
                json.writeValue(res.getOutputStream(),service.inspectionTrips());
            } else if ("/xac-minh".equals(info)) {
                if (!method.equals("POST")) {
                    res.setHeader("Allow", "POST, OPTIONS");
                    throw new DomainException(405, "Phương thức không được hỗ trợ.");
                }
                var body = RefundHttp.body(req, KiemVeDto.VerifyRequest.class);
                var result = service.verify(body);
                res.setStatus(200);
                json.writeValue(res.getOutputStream(), result);
            } else {
                throw new DomainException(404, "API không tồn tại.");
            }
        } catch (DomainException e) {
            ApiErrors.write(res, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            ApiErrors.write(res, new DomainException(400, "Dữ liệu yêu cầu không hợp lệ."));
        }
    }
}
