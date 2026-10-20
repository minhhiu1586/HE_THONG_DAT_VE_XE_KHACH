package com.datvexe.controller;

import com.datvexe.dto.AccountResponses.Identity;
import com.datvexe.security.AccountException;
import com.datvexe.security.AuthFilter;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.*;

import java.io.IOException;

final class AccountHttp {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

    private AccountHttp() {}

    static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    static Identity identity(HttpServletRequest request) {
        return (Identity) request.getAttribute(AuthFilter.IDENTITY);
    }

    static void prepare(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
    }

    static <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
        T input = JSON.readValue(request.getInputStream(), type);
        if (input == null) throw AccountException.invalid("body", "Nội dung yêu cầu là bắt buộc.");
        return input;
    }

    static int integer(HttpServletRequest request, String key, int fallback) {
        String value = request.getParameter(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        if (body != null) JSON.writeValue(response.getOutputStream(), body);
    }

    static AccountException notFound() {
        return new AccountException(404, "RESOURCE_NOT_FOUND", "API không tồn tại.");
    }
}
