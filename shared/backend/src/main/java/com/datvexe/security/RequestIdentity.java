package com.datvexe.security;

import com.fasterxml.jackson.databind.JsonNode;

/** Scoped to one servlet request; always cleared in finally, including pooled-thread reuse. */
public final class RequestIdentity {
    public record Context(CurrentUser user, String method, String path, JsonNode body) {}
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private RequestIdentity() {}
    public static Context get() { return CURRENT.get(); }
    public static void set(Context context) { CURRENT.set(context); }
    public static void clear() { CURRENT.remove(); }
    public static String operatorId() {
        var context = get();
        return context != null && context.user() != null && context.user().loaiTaiKhoan() == com.datvexe.domain.States.LoaiTaiKhoan.NHA_XE
            ? context.user().maNhaXe() : null;
    }
}
