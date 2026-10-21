package com.datvexe.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.security.*;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;

import jakarta.persistence.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

class AuthFlowTest {
    private EntityManagerFactory factory;
    private AuthFilter filter;
    private AuthController auth;
    private AccountController accounts;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        factory =
                Persistence.createEntityManagerFactory(
                        "datvexe",
                        Map.of(
                                "jakarta.persistence.jdbc.driver",
                                "org.h2.Driver",
                                "jakarta.persistence.jdbc.url",
                                "jdbc:h2:mem:http_auth_" + UUID.randomUUID() + ";MODE=PostgreSQL",
                                "jakarta.persistence.jdbc.user",
                                "sa",
                                "jakarta.persistence.jdbc.password",
                                "",
                                "hibernate.hbm2ddl.auto",
                                "create-drop"));
        var service = new AuthService(factory);
        filter = inject(new AuthFilter(), "auth", service);
        auth = inject(new AuthController(), "auth", service);
        accounts = inject(new AccountController(), "accounts", new AccountService(factory));
    }

    @AfterEach
    void close() {
        RequestIdentity.clear();
        factory.close();
    }

    private <T> T inject(T target, String field, Object value) throws Exception {
        var member = target.getClass().getDeclaredField(field);
        member.setAccessible(true);
        member.set(target, value);
        return target;
    }

    private Reply call(String method, String path, String body, String token, String csrf)
            throws Exception {
        return call(method, path, body, token, csrf, "http://localhost:3000");
    }

    private Reply call(
            String method, String path, String body, String token, String csrf, String origin)
            throws Exception {
        var reply = new Reply();
        var attributes = new HashMap<String, Object>();
        var headers = new HashMap<String, String>();
        headers.put("X-Requested-With", "datvexe");
        if (body != null) headers.put("Content-Type", "application/json");
        if (origin != null) headers.put("Origin", origin);
        if (csrf != null) headers.put("X-CSRF-Token", csrf);
        var input =
                new ByteArrayInputStream(
                        body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
        var stream =
                new ServletInputStream() {
                    public int read() {
                        return input.read();
                    }

                    public boolean isFinished() {
                        return input.available() == 0;
                    }

                    public boolean isReady() {
                        return true;
                    }

                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException();
                    }
                };
        var request =
                (HttpServletRequest)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {HttpServletRequest.class},
                                (proxy, action, args) ->
                                        switch (action.getName()) {
                                            case "getRequestURI" -> path;
                                            case "getContextPath" -> "";
                                            case "getMethod" -> method;
                                            case "getRemoteAddr" -> "127.0.0.1";
                                            case "getHeader" -> headers.get(args[0]);
                                            case "getContentType" -> headers.get("Content-Type");
                                            case "getCookies" ->
                                                    token == null
                                                            ? null
                                                            : new Cookie[] {
                                                                new Cookie(AuthCookies.NAME, token)
                                                            };
                                            case "getInputStream" -> stream;
                                            case "getAttribute" -> attributes.get(args[0]);
                                            case "setAttribute" -> {
                                                attributes.put((String) args[0], args[1]);
                                                yield null;
                                            }
                                            default -> null;
                                        });
        filter.doFilter(
                request,
                reply.response(),
                (req, res) -> {
                    if (path.startsWith("/api/auth/"))
                        auth.service((HttpServletRequest) req, (HttpServletResponse) res);
                    else accounts.service((HttpServletRequest) req, (HttpServletResponse) res);
                });
        return reply;
    }

    @Test
    void registerLoginProfileLogoutKeepExistingWireContract() throws Exception {
        var registration =
                call(
                        "POST",
                        "/api/auth/dang-ky",
                        "{\"hoTen\":\"Nguyễn Văn"
                            + " An\",\"email\":\"an@example.test\",\"soDienThoai\":\"0901234567\",\"matKhau\":\"ValidPassword123\"}",
                        null,
                        null);
        assertEquals(201, registration.status);
        assertEquals("/api/tai-khoan/me", registration.headers.get("Location"));
        assertEquals("KHACH_HANG", registration.body().path("loaiTaiKhoan").asText());
        assertFalse(registration.body().has("matKhau"));
        var login =
                call(
                        "POST",
                        "/api/auth/dang-nhap",
                        "{\"email\":\"an@example.test\",\"matKhau\":\"ValidPassword123\"}",
                        null,
                        null);
        assertEquals(200, login.status);
        assertTrue(login.body().has("nguoiDung"));
        assertTrue(login.body().has("csrfToken"));
        assertTrue(login.body().has("hetHan"));
        assertFalse(login.body().has("token"));
        String cookie = login.headers.get("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api"));
        String token = cookie.split(";", 2)[0].substring(AuthCookies.NAME.length() + 1);
        String csrf = login.body().path("csrfToken").asText();
        var profile = call("GET", "/api/tai-khoan/me", null, token, null);
        assertEquals(200, profile.status);
        assertEquals("an@example.test", profile.body().path("nguoiDung").path("email").asText());
        assertEquals(403, call("POST", "/api/auth/dang-xuat", "{}", token, "wrong").status);
        var logout = call("POST", "/api/auth/dang-xuat", "{}", token, csrf);
        assertEquals(204, logout.status);
        assertEquals(0, logout.output.size());
        assertTrue(logout.headers.get("Set-Cookie").contains("Max-Age=0"));
        assertEquals(401, call("GET", "/api/tai-khoan/me", null, token, null).status);
        assertNull(RequestIdentity.get());
    }

    @Test
    void registrationCannotInjectServerControlledFields() throws Exception {
        var result =
                call(
                        "POST",
                        "/api/auth/dang-ky",
                        "{\"hoTen\":\"An\",\"email\":\"an@example.test\",\"soDienThoai\":\"0901234567\",\"matKhau\":\"ValidPassword123\",\"loaiTaiKhoan\":\"ADMIN\"}",
                        null,
                        null);
        assertEquals(400, result.status);
        try (var em = factory.createEntityManager()) {
            assertEquals(
                    0L,
                    em.createQuery("select count(n) from NguoiDung n", Long.class)
                            .getSingleResult());
        }
    }

    @Test
    void requestGuardsRejectInvalidJsonOriginAndOversizedBody() throws Exception {
        assertEquals(400, call("POST", "/api/auth/dang-nhap", "{broken", null, null).status);
        assertEquals(
                413, call("POST", "/api/auth/dang-nhap", "x".repeat(65_537), null, null).status);
        assertEquals(
                403,
                call("POST", "/api/auth/dang-nhap", "{}", null, null, "https://foreign.example")
                        .status);
        assertNull(RequestIdentity.get());
    }

    private final class Reply {
        int status = 200;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        JsonNode body() throws Exception {
            return json.readTree(output.toByteArray());
        }

        HttpServletResponse response() {
            var stream =
                    new ServletOutputStream() {
                        public void write(int value) {
                            output.write(value);
                        }

                        public boolean isReady() {
                            return true;
                        }

                        public void setWriteListener(WriteListener listener) {
                            throw new UnsupportedOperationException();
                        }
                    };
            return (HttpServletResponse)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {HttpServletResponse.class},
                            (proxy, action, args) ->
                                    switch (action.getName()) {
                                        case "setStatus" -> {
                                            status = (int) args[0];
                                            yield null;
                                        }
                                        case "getStatus" -> status;
                                        case "setHeader", "addHeader" -> {
                                            headers.put((String) args[0], (String) args[1]);
                                            yield null;
                                        }
                                        case "getHeader" -> headers.get(args[0]);
                                        case "getOutputStream" -> stream;
                                        default -> null;
                                    });
        }
    }
}
