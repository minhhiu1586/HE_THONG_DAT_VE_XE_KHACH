package com.datvexe.controller;

import com.datvexe.service.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VeXeControllerTest {
    @Test
    void unknownRouteReturns404() {
        var request = request("GET", "/V1/unknown", Map.of());
        assertEquals(404, assertThrows(DomainException.class,
                () -> new VeXeController().service(request, response(new HashMap<>()))).status());
        var boardingRequest = request("POST", "/V1/len-xe", Map.of());
        assertEquals(404, assertThrows(DomainException.class,
                () -> new VeXeController().service(boardingRequest, response(new HashMap<>()))).status());
    }

    @Test
    void postIsOnlyAllowedOnRefundRoute() {
        Map<String, String> output = new HashMap<>();
        var response = response(output);
        var request = request("POST", "/V1", Map.of());
        assertEquals(405, assertThrows(DomainException.class,
                () -> new VeXeController().service(request, response)).status());
        assertEquals("GET, HEAD, OPTIONS", output.get("Allow"));
    }

    @Test
    void refundOptionsAdvertisesPost() throws Exception {
        Map<String, String> output = new HashMap<>();
        new VeXeController().service(request("OPTIONS", "/V1/hoan-tien", Map.of()), response(output));
        assertEquals("GET, HEAD, POST, OPTIONS", output.get("Allow"));
        assertEquals("204", output.get("status"));
    }

    @Test
    void optionsDoesNotApplyGenericQueryValidation() throws Exception {
        Map<String, String> output = new HashMap<>();
        var request = request("OPTIONS", "/V1/hoan-tien", Map.of("extra", new String[]{"1"}));
        new VeXeController().service(request, response(output));
        assertEquals("204", output.get("status"));
    }

    @Test
    void invalidPageReturnsValidationError() {
        var request = request("GET", "/V1/hoan-tien", Map.of("page", new String[]{"abc"}));
        assertEquals(400, assertThrows(DomainException.class,
                () -> new VeXeController().service(request, response(new HashMap<>()))).status());
    }

    private HttpServletRequest request(String method, String path, Map<String, String[]> query) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, called, args) -> switch (called.getName()) {
                    case "getMethod" -> method;
                    case "getPathInfo" -> path;
                    case "getParameterMap" -> query;
                    case "getParameter" -> query.containsKey(args[0]) ? query.get(args[0])[0] : null;
                    default -> null;
                });
    }

    private HttpServletResponse response(Map<String, String> output) {
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, called, args) -> {
                    if (called.getName().equals("setHeader")) {
                        output.put((String) args[0], (String) args[1]);
                    } else if (called.getName().equals("setStatus")) {
                        output.put("status", String.valueOf(args[0]));
                    }
                    return null;
                });
    }
}
