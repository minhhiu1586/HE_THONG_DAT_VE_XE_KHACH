package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** Readiness check for both the WAR and its PostgreSQL connection. */
@WebServlet("/api/health")
public class HealthController extends HttpServlet {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        boolean databaseReady;
        try {
            Jpa.read(em -> em.createNativeQuery("SELECT 1").getSingleResult());
            databaseReady = true;
        } catch (RuntimeException error) {
            databaseReady = false;
            getServletContext().log("Health check failed trace=" + response.getHeader("X-Trace-Id"), error);
        }
        response.setStatus(databaseReady ? 200 : 503);
        JSON.writeValue(response.getOutputStream(), Map.of(
            "status", databaseReady ? "UP" : "DOWN",
            "database", databaseReady ? "UP" : "DOWN"));
    }
}
