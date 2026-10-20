package com.datvexe.security;

import jakarta.servlet.http.*;

public final class AuthCookies {
    public static final String NAME = "datvexe_session";

    private AuthCookies() {}

    public static String token(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (NAME.equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }

    public static boolean secure() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("AUTH_COOKIE_SECURE", "false"));
    }

    public static void write(HttpServletResponse response, String token, long seconds) {
        response.addHeader(
                "Set-Cookie",
                NAME
                        + "="
                        + token
                        + "; Path=/api; HttpOnly; SameSite=Lax; Max-Age="
                        + seconds
                        + (secure() ? "; Secure" : ""));
    }
}
