package com.datvexe.security;

import com.datvexe.config.Jpa;
import com.datvexe.dto.AccountResponses.Identity;
import com.datvexe.service.AuthService;
import com.datvexe.service.DomainException;
import com.fasterxml.jackson.databind.*;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@WebFilter(urlPatterns = "/api/*")
public class AuthFilter implements Filter {
    public static final String IDENTITY = "datvexe.identity";
    private final ObjectMapper json = new ObjectMapper();
    private AuthService auth;

    private record RequestBody(HttpServletRequest request, JsonNode json) {}

    public static boolean secureCookies() {
        return AuthCookies.secure();
    }

    @Override
    public void init(FilterConfig config) {
        auth = new AuthService(Jpa.factory());
    }

    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) input;
        HttpServletResponse response = (HttpServletResponse) output;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String origin = request.getHeader("Origin");
        String allowedOrigin =
                System.getenv().getOrDefault("FRONTEND_ORIGIN", "http://localhost:3000");
        prepareResponse(response, origin, allowedOrigin);
        try {
            validatePath(path);
            if (request.getMethod().equals("OPTIONS")) {
                validateOrigin(origin, allowedOrigin);
                response.setStatus(204);
                return;
            }
            boolean safe = Set.of("GET", "HEAD").contains(request.getMethod());
            if (!safe) validateOrigin(origin, allowedOrigin);
            Identity identity = authenticate(request, response, path, safe);
            RequestBody body = readBody(request, safe);
            RequestIdentity.set(
                    new RequestIdentity.Context(
                            identity == null ? null : identity.user(),
                            request.getMethod(),
                            path,
                            body.json()));
            chain.doFilter(body.request(), response);
        } catch (DomainException error) {
            ApiErrors.write(response, error);
        } catch (RuntimeException error) {
            request.getServletContext()
                    .log("API failure trace=" + response.getHeader("X-Trace-Id"), error);
            ApiErrors.write(
                    response,
                    new AccountException(
                            500, "INTERNAL_ERROR", "Không thể xử lý yêu cầu lúc này."));
        } finally {
            RequestIdentity.clear();
        }
    }

    private void prepareResponse(
            HttpServletResponse response, String origin, String allowedOrigin) {
        response.setHeader("X-Trace-Id", UUID.randomUUID().toString());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Vary", "Origin");
        if (allowedOrigin.equals(origin)) {
            response.setHeader("Access-Control-Allow-Origin", allowedOrigin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader(
                    "Access-Control-Allow-Headers",
                    "Content-Type, X-CSRF-Token, X-Requested-With, Idempotency-Key");
            response.setHeader(
                    "Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            response.setHeader("Access-Control-Expose-Headers", "X-Trace-Id");
        }
    }

    private void validatePath(String path) {
        if (path.contains("%") || path.contains(";") || path.contains("//"))
            throw AccountException.invalid("path", "Đường dẫn không hợp lệ.");
    }

    private void validateOrigin(String origin, String allowedOrigin) {
        if (origin != null && !allowedOrigin.equals(origin)) throw AccessControl.denied();
    }

    private Identity authenticate(
            HttpServletRequest request, HttpServletResponse response, String path, boolean safe) {
        if (AccessPolicy.publicEndpoint(request.getMethod(), path)) {
            if (!safe && !"datvexe".equals(request.getHeader("X-Requested-With")))
                throw AccessControl.denied();
            return null;
        }
        Identity identity;
        try {
            identity = auth.authenticate(AuthCookies.token(request));
        } catch (AccountException error) {
            AuthCookies.write(response, "", 0);
            throw error;
        }
        request.setAttribute(IDENTITY, identity);
        AccessPolicy.role(identity.user(), path, request.getMethod());
        if (!safe && !csrf(identity.csrfToken(), request.getHeader("X-CSRF-Token")))
            throw new AccountException(
                    403, "FORBIDDEN", "Yêu cầu không hợp lệ. Vui lòng tải lại trang.");
        return identity;
    }

    private RequestBody readBody(HttpServletRequest request, boolean safe) throws IOException {
        if (safe) return new RequestBody(request, null);
        byte[] bytes = request.getInputStream().readNBytes(65_537);
        if (bytes.length > 65_536)
            throw new AccountException(413, "REQUEST_TOO_LARGE", "Nội dung yêu cầu quá lớn.");
        if (bytes.length == 0) return new RequestBody(request, null);
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType
                        .toLowerCase(Locale.ROOT)
                        .split(";", 2)[0]
                        .strip()
                        .equals("application/json"))
            throw new AccountException(
                    415, "UNSUPPORTED_MEDIA_TYPE", "Chỉ hỗ trợ application/json.");
        try {
            JsonNode body = json.readTree(bytes);
            return new RequestBody(new CachedBodyRequest(request, bytes), body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw AccountException.invalid("body", "JSON không hợp lệ.");
        }
    }

    private boolean csrf(String expected, String supplied) {
        return supplied != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }
}
