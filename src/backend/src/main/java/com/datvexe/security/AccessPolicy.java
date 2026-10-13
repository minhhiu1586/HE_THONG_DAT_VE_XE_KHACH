package com.datvexe.security;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.LoaiTaiKhoan;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Set;

/** Endpoint roles plus resource guards. Unknown endpoints do not become public by default. */
public final class AccessPolicy {
    private AccessPolicy() {}
    public static boolean publicEndpoint(String method, String path) {
        if (method.equals("POST")) return Set.of("/api/auth/dang-ky", "/api/auth/dang-nhap").contains(path);
        return method.equals("GET") && (Set.of("/api/health", "/api/tinh-thanh", "/api/ben-xe", "/api/tuyen-xe/cong-khai", "/api/chuyen-xe", "/api/phuong-thuc-thanh-toan", "/api/khuyen-mai").contains(path)
            || path.matches("/api/tinh-thanh/[^/]+") || path.matches("/api/ben-xe/[^/]+")
            || path.matches("/api/chuyen-xe/[^/]+(?:/(?:ghe|gia|trang-thai))?"));
    }
    public static void role(CurrentUser user, String path) { role(user, path, "POST"); }
    public static void role(CurrentUser user, String path, String method) {
        if (path.equals("/api/booking") || path.startsWith("/api/booking/")) {
            if (user.loaiTaiKhoan() != LoaiTaiKhoan.KHACH_HANG) throw AccessControl.denied();
            return;
        }
        if(path.equals("/api/quan-ly/chuyen-xe") || path.startsWith("/api/quan-ly/chuyen-xe/")) {
            if(user.loaiTaiKhoan()!=LoaiTaiKhoan.ADMIN && user.loaiTaiKhoan()!=LoaiTaiKhoan.NHA_XE) throw AccessControl.denied();
            if(user.loaiTaiKhoan()==LoaiTaiKhoan.NHA_XE && user.maNhaXe()==null) throw AccessControl.denied();
            return;
        }
        if(path.startsWith("/api/nhan-vien")) {
            if(user.loaiTaiKhoan()!=LoaiTaiKhoan.ADMIN && user.loaiTaiKhoan()!=LoaiTaiKhoan.NHA_XE
                && !(user.loaiTaiKhoan()==LoaiTaiKhoan.NHAN_VIEN && method.equals("GET"))) throw AccessControl.denied();
            return;
        }
        if (path.startsWith("/api/loai-xe") && method.equals("GET")) {
            if (user.loaiTaiKhoan() != LoaiTaiKhoan.ADMIN && user.loaiTaiKhoan() != LoaiTaiKhoan.NHA_XE) throw AccessControl.denied();
            return;
        }
        if (path.startsWith("/api/quan-tri/") || path.startsWith("/api/loai-xe")
                || path.startsWith("/api/tinh-thanh") || path.startsWith("/api/ben-xe")) {
            if (user.loaiTaiKhoan() != LoaiTaiKhoan.ADMIN) throw AccessControl.denied();
        } else if (path.startsWith("/api/tuyen-xe") || path.startsWith("/api/chuyen-xe")
                || path.startsWith("/api/xe") || path.startsWith("/api/phan-cong")) {
            if (user.loaiTaiKhoan() != LoaiTaiKhoan.NHA_XE && user.loaiTaiKhoan() != LoaiTaiKhoan.ADMIN)
                throw AccessControl.denied();
            if (user.loaiTaiKhoan() == LoaiTaiKhoan.NHA_XE && user.maNhaXe() == null) throw AccessControl.denied();
        }
    }
    /** Called inside the business service transaction, before reading or writing private data. */
    public static void guard(EntityManager em) {
        var context = RequestIdentity.get();
        if (context == null || publicEndpoint(context.method(), context.path())) return; // internal trusted calls/tests
        NguoiDung user = AccessControl.authenticated(em, context.user(), Instant.now());
        String path = context.path(); CurrentUser current = new CurrentUser(user.maNguoiDung, user.loaiTaiKhoan,
            user.nhaXe == null ? null : user.nhaXe.maNhaXe, user.nhanVien == null ? null : user.nhanVien.maNhanVien,
            context.user().sessionHash());
        role(current, path, context.method());
        RequestIdentity.set(new RequestIdentity.Context(current, context.method(), path, context.body()));
        String resourcePath=path.replaceFirst("^/api/quan-ly/chuyen-xe(?=/|$)","/api/chuyen-xe");
        String[] p = resourcePath.substring("/api/".length()).split("/");
        if (user.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE) {
            AccessControl.operator(user, user.nhaXe);
            if (!Set.of("GET", "HEAD").contains(context.method())) AccessControl.activeOperator(em, user);
            if (p.length > 1) switch (p[0]) {
                case "tuyen-xe" -> AccessControl.route(em, user, p[1]);
                case "chuyen-xe" -> AccessControl.trip(em, user, p[1]);
                case "xe" -> {
                    if (!(p.length == 2 && p[1].equals("pham-vi") && context.method().equals("GET"))) AccessControl.vehicle(em, user, p[1]);
                    if (p.length == 4 && p[2].equals("dieu-kien")) AccessControl.trip(em, user, p[3]);
                }
                case "phan-cong" -> {
                    var assignment = AccessControl.resource(em, PhanCongChuyenXe.class, p[1]);
                    AccessControl.operator(user, assignment.chuyenXe.tuyenXe.nhaXe);
                    AccessControl.operator(user, assignment.nhanVien.nhaXe);
                }
                case "nhan-vien" -> AccessControl.employee(em,user,p[1]);
                default -> { }
            }
            JsonNode body = context.body();
            if (body != null && body.isObject()) {
                if ((p[0].equals("xe") || p[0].equals("tuyen-xe") || p[0].equals("nhan-vien")) && p.length <= 2
                        && Set.of("POST", "PUT").contains(context.method())) {
                    String owner = text(body, "maNhaXe");
                    if ((owner != null && !owner.equals(user.nhaXe.maNhaXe)))
                        throw AccessControl.denied();
                }
                String route = text(body, "maTuyen"), vehicle = text(body, "maXe"), trip = text(body, "maChuyen"), employee = text(body, "maNhanVien");
                if (route != null && !(p[0].equals("tuyen-xe") && p.length == 1)) AccessControl.route(em, user, route);
                if (vehicle != null && !(p[0].equals("xe") && p.length == 1 && context.method().equals("POST"))) AccessControl.vehicle(em, user, vehicle);
                if (trip != null && !(p[0].equals("chuyen-xe") && p.length==1 && context.method().equals("POST")))
                    AccessControl.trip(em, user, trip);
                if (employee != null && !(p[0].equals("nhan-vien") && p.length==1 && context.method().equals("POST")))
                    AccessControl.employee(em, user, employee);
            }
        }
    }
    private static String text(JsonNode node, String key) {
        var value = node.get(key);
        if (value == null || value.isNull()) return null;
        // Match the API ID contract; never skip a scope guard because Jackson coerces a scalar to String.
        if (!value.isTextual()) throw AccountException.invalid(key, "Mã tài nguyên phải là chuỗi.");
        return value.asText().strip();
    }
}
