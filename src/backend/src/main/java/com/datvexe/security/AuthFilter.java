package com.datvexe.security;

import com.datvexe.config.Jpa;
import com.datvexe.service.AccountService;
import com.datvexe.service.DomainException;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@WebFilter(urlPatterns = "/api/*")
public class AuthFilter implements Filter {
    public static final String COOKIE = "datvexe_session";
    public static final String IDENTITY = "datvexe.identity";
    private final ObjectMapper json = new ObjectMapper();
    private AccountService accounts;
    @Override public void init(FilterConfig config) { accounts = new AccountService(Jpa.factory()); }
    public static String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie c : request.getCookies()) if (COOKIE.equals(c.getName())) return c.getValue();
        return null;
    }
    public static boolean secureCookies() { return Boolean.parseBoolean(System.getenv().getOrDefault("AUTH_COOKIE_SECURE", "false")); }
    public static void cookie(HttpServletResponse response, String token, long seconds) {
        response.addHeader("Set-Cookie", COOKIE + "=" + token + "; Path=/api; HttpOnly; SameSite=Lax; Max-Age=" + seconds
            + (secureCookies() ? "; Secure" : ""));
    }
    @Override public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) input; HttpServletResponse res = (HttpServletResponse) output;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String origin = req.getHeader("Origin"), allowedOrigin = System.getenv().getOrDefault("FRONTEND_ORIGIN", "http://localhost:3000");
        res.setHeader("X-Trace-Id", UUID.randomUUID().toString()); res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Vary", "Origin");
        if (allowedOrigin.equals(origin)) {
            res.setHeader("Access-Control-Allow-Origin", allowedOrigin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
            res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token, X-Requested-With, Idempotency-Key");
            res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            res.setHeader("Access-Control-Expose-Headers", "X-Trace-Id");
        }
        try {
            if (path.contains("%") || path.contains(";") || path.contains("//")) throw AccountException.invalid("path", "Đường dẫn không hợp lệ.");
            if (req.getMethod().equals("OPTIONS")) { if (origin != null && !allowedOrigin.equals(origin)) throw AccessControl.denied(); res.setStatus(204); return; }
            boolean safe = Set.of("GET", "HEAD").contains(req.getMethod());
            if (!safe && origin != null && !allowedOrigin.equals(origin)) throw AccessControl.denied();
            boolean publicApi = AccessPolicy.publicEndpoint(req.getMethod(), path);
            AccountService.Identity identity = null;
            if (!publicApi) {
                try { identity = accounts.authenticate(token(req)); }
                catch (AccountException e) { cookie(res, "", 0); throw e; }
                req.setAttribute(IDENTITY, identity);
                AccessPolicy.role(identity.user(), path);
                if (!safe && !csrf(identity.csrfToken(), req.getHeader("X-CSRF-Token")))
                    throw new AccountException(403, "FORBIDDEN", "Yêu cầu không hợp lệ. Vui lòng tải lại trang.");
            } else if (!safe && !"datvexe".equals(req.getHeader("X-Requested-With"))) {
                throw AccessControl.denied();
            }
            JsonNode body = null;
            HttpServletRequest actual = req;
            if (!safe && req.getContentLengthLong() != 0) {
                if (req.getContentType() == null || !req.getContentType().toLowerCase(Locale.ROOT).split(";", 2)[0].strip().equals("application/json"))
                    throw new AccountException(415, "UNSUPPORTED_MEDIA_TYPE", "Chỉ hỗ trợ application/json.");
                byte[] bytes = req.getInputStream().readNBytes(65_537);
                if (bytes.length > 65_536) throw new AccountException(413, "REQUEST_TOO_LARGE", "Nội dung yêu cầu quá lớn.");
                if (bytes.length > 0) {
                    try { body = json.readTree(bytes); } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw AccountException.invalid("body", "JSON không hợp lệ."); }
                }
                actual = new CachedRequest(req, bytes);
            }
            RequestIdentity.set(new RequestIdentity.Context(identity == null ? null : identity.user(), req.getMethod(), path, body));
            chain.doFilter(actual, res);
        } catch (DomainException e) { ApiErrors.write(res, e); }
        catch (RuntimeException e) {
            req.getServletContext().log("API failure trace=" + res.getHeader("X-Trace-Id"), e);
            ApiErrors.write(res, new AccountException(500, "INTERNAL_ERROR", "Không thể xử lý yêu cầu lúc này."));
        } finally { RequestIdentity.clear(); }
    }
    private static boolean csrf(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        CachedRequest(HttpServletRequest request, byte[] bytes) { super(request); this.bytes = bytes; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return stream.read(); }
                @Override public boolean isFinished() { return stream.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
