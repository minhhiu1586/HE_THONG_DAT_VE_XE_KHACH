package com.datvexe.security;

import com.datvexe.service.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public final class ApiErrors {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ApiErrors() {}
    public static void write(HttpServletResponse response, DomainException error) throws IOException {
        String code = error instanceof AccountException a ? a.code() : switch (error.status()) {
            case 400 -> "VALIDATION_ERROR"; case 401 -> "UNAUTHENTICATED"; case 403 -> "FORBIDDEN";
            case 404 -> "RESOURCE_NOT_FOUND"; case 409 -> "INVALID_STATE_TRANSITION";
            case 405 -> "METHOD_NOT_ALLOWED"; default -> "INTERNAL_ERROR";
        };
        String trace = response.getHeader("X-Trace-Id");
        if (trace == null) { trace = UUID.randomUUID().toString(); response.setHeader("X-Trace-Id", trace); }
        response.setStatus(error.status()); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json; charset=UTF-8");
        JSON.writeValue(response.getOutputStream(), Map.of("code", code, "message", error.getMessage(),
            "details", error instanceof AccountException a ? a.details() : Map.of(),
            "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString(), "traceId", trace));
    }
}
