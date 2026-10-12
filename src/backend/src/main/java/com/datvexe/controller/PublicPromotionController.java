package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.DomainException;
import com.datvexe.service.KhuyenMaiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Read-only catalogue of currently usable promotions for public pages. */
@WebServlet("/api/khuyen-mai")
public class PublicPromotionController extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private KhuyenMaiService service;

    @Override public void init() { service = new KhuyenMaiService(Jpa.factory()); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        try {
            json.writeValue(response.getOutputStream(), service.danhSachCongKhai());
        } catch (DomainException error) {
            ApiErrors.write(response, error);
        } catch (jakarta.persistence.PersistenceException error) {
            getServletContext().log("Public promotion catalogue failure", error);
            ApiErrors.write(response, new DomainException(500, "Không thể tải ưu đãi lúc này."));
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiErrors.write(response, new DomainException(405, "Phương thức không được hỗ trợ."));
    }
}
