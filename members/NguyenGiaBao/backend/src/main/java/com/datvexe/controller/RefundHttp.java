package com.datvexe.controller;

import com.datvexe.security.AccountException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;

/** Small HTTP helpers shared by the two refund servlet routes. */
final class RefundHttp {
    private RefundHttp() {}
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static {
        JSON.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float,CoercionAction.Fail).setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
    }
    static <T> T body(HttpServletRequest request,Class<T> type) throws IOException {
        try {
            JsonNode node = JSON.readTree(request.getInputStream());
            if (node == null || !node.isObject()) throw invalid("body","Cần một JSON object.");
            return JSON.treeToValue(node,type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw invalid("body","JSON không hợp lệ hoặc chứa trường không được hỗ trợ."); }
    }
    static void empty(HttpServletRequest request) throws IOException {
        var node = body(request,JsonNode.class);
        if (!node.isEmpty()) throw invalid("body","API chỉ nhận object rỗng.");
    }
    static void query(HttpServletRequest request,Set<String> allowed) {
        request.getParameterMap().forEach((key,values) -> {
            if (!allowed.contains(key) || values.length != 1) throw invalid("query","Query không hợp lệ hoặc bị lặp.");
        });
    }
    static int integer(HttpServletRequest request,String field,int fallback) {
        String value = request.getParameter(field);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw invalid(field,"Cần một số nguyên."); }
    }
    static void write(HttpServletResponse response,int status,Object body) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        JSON.writeValue(response.getOutputStream(),body);
    }
    static AccountException invalid(String field,String message) { return AccountException.invalid(field,message); }
}
