package com.datvexe.controller;

import com.datvexe.service.DomainException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HoanVeControllerTest {
    @Test
    void unknownRouteReturns404() {
        assertEquals(404, assertThrows(DomainException.class, () -> new HoanVeController().service(
                request("GET", "/R1/unknown", ""), response(new HashMap<>()))).status());
    }

    @Test
    void getOnActionReturns405() {
        Map<String, String> output = new HashMap<>();
        assertEquals(405, assertThrows(DomainException.class, () -> new HoanVeController().service(
                request("GET", "/R1/xac-minh", ""), response(output))).status());
        assertEquals("POST, OPTIONS", output.get("Allow"));
    }

    @Test
    void postOnDetailReturns405() {
        Map<String, String> output = new HashMap<>();
        assertEquals(405, assertThrows(DomainException.class, () -> new HoanVeController().service(
                request("POST", "/R1", "{}"), response(output))).status());
        assertEquals("GET, HEAD, OPTIONS", output.get("Allow"));
    }

    @Test
    void optionsAdvertisesMethodsForEachRoute() throws Exception {
        Map<String, String> output = new HashMap<>();
        HoanVeController controller = new HoanVeController();
        controller.service(request("OPTIONS", "/R1", ""), response(output));
        assertEquals("GET, HEAD, OPTIONS", output.get("Allow"));
        assertEquals("204", output.get("status"));
        controller.service(request("OPTIONS", "/R1/thu-lai", ""), response(output));
        assertEquals("POST, OPTIONS", output.get("Allow"));
        assertEquals("204", output.get("status"));
    }

    @Test
    void invalidActionBodiesAreRejectedBeforeServiceCall() {
        for (String body : new String[]{"", "null", "[]", "{", "{\"extra\":1}", "{} {}"}) {
            assertEquals(400, assertThrows(DomainException.class, () -> new HoanVeController().service(
                    request("POST", "/R1/xac-minh", body), response(new HashMap<>()))).status(), body);
        }
    }

    private HttpServletRequest request(String method, String path, String body) {
        ByteArrayInputStream input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        ServletInputStream stream = new ServletInputStream() {
            @Override public int read() { return input.read(); }
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, called, args) -> switch (called.getName()) {
                    case "getMethod" -> method;
                    case "getPathInfo" -> path;
                    case "getInputStream" -> stream;
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
