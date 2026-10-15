package com.datvexe.service;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.States.*;
import com.datvexe.dto.AccountInputs;
import com.datvexe.repository.NguoiDungRepository;
import com.datvexe.security.*;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

// TẦNG NGHIỆP VỤ: đăng nhập, vòng đời phiên, hồ sơ và quản trị tài khoản.
// Controller lo HTTP; service quyết định điều kiện hợp lệ và thao tác DB bằng JPA.
public final class AccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    // Tài khoản không tồn tại vẫn so khớp hash giả để giảm khác biệt xử lý quá rõ giữa các trường hợp.
    private static final String DUMMY_HASH = Passwords.hash("DummyPasswordForTiming123");
    private final EntityManagerFactory factory;
    private final Clock clock;
    private final Duration lifetime;
    public AccountService(EntityManagerFactory factory) { this(factory, Clock.systemUTC(), Duration.ofHours(1)); }
    public AccountService(EntityManagerFactory factory, Clock clock, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("Session lifetime must be positive");
        this.factory = factory; this.clock = clock; this.lifetime = lifetime;
    }
    // DTO đầu ra chỉ chọn thông tin hồ sơ; không serialize entity chứa hash mật khẩu ra frontend.
    public record Profile(String maNguoiDung, String hoTen, String email, String soDienThoai,
                          GioiTinh gioiTinh, LocalDate ngaySinh, String cccd,
                          LoaiTaiKhoan loaiTaiKhoan, TrangThaiTaiKhoan trangThai) {}
    // Login là kết quả nội bộ có raw token để Controller đặt cookie; không trả nguyên record này thành JSON.
    public record Login(String token, String csrfToken, Instant hetHan, Profile nguoiDung) {}
    public record Identity(CurrentUser user, String csrfToken, Instant hetHan) {}
    private static Profile view(NguoiDung n) {
        return new Profile(n.maNguoiDung, n.hoTen, n.email, n.soDienThoai, n.gioiTinh,
            n.ngaySinh, n.cccd, n.loaiTaiKhoan, n.trangThai);
    }
    // Mỗi nghiệp vụ mở EM/transaction riêng: thành công commit, RuntimeException rollback, cuối cùng đóng EM.
    private <T> T tx(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try { T result = action.apply(em); tx.commit(); return result; }
            catch (RuntimeException e) { if (tx.isActive()) tx.rollback(); throw e; }
        }
    }
    private static String name(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 160)
            throw AccountException.invalid("hoTen", "Họ tên là bắt buộc, tối đa 160 ký tự.");
        return value.strip();
    }
    public static String email(String value) {
        if (value == null || value.strip().length() > 254
                || !value.strip().matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+"))
            throw AccountException.invalid("email", "Email không hợp lệ.");
        return value.strip().toLowerCase(Locale.ROOT);
    }
    private static String phone(String value) {
        if (value == null) throw AccountException.invalid("soDienThoai", "Số điện thoại là bắt buộc.");
        String result = value.strip();
        if (result.startsWith("+84")) result = "0" + result.substring(3);
        if (!result.matches("0[35789][0-9]{8}"))
            throw AccountException.invalid("soDienThoai", "Số điện thoại Việt Nam cần 10 chữ số hoặc dạng +84.");
        return result;
    }
    private String cccd(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.strip().toUpperCase(Locale.ROOT);
        if (!result.matches("(?:[0-9]{12}|[A-Z][0-9]{7})"))
            throw AccountException.invalid("cccd", "CCCD cần 12 chữ số; hộ chiếu cần một chữ và 7 số.");
        return result;
    }
    private void birth(LocalDate value) {
        if (value != null && (value.isAfter(LocalDate.now(clock)) || value.isBefore(LocalDate.of(1900, 1, 1))))
            throw AccountException.invalid("ngaySinh", "Ngày sinh không hợp lệ.");
    }
    private static AccountException unauthenticated() {
        return new AccountException(401, "UNAUTHENTICATED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
    }
    private static NguoiDung active(EntityManager em, String id) {
        NguoiDung n = new NguoiDungRepository(em).lock(id);
        if (n == null || n.trangThai != TrangThaiTaiKhoan.HOAT_DONG) throw unauthenticated();
        return n;
    }
    private static AccountException duplicate() {
        return new AccountException(409, "ACCOUNT_ALREADY_EXISTS", "Thông tin định danh đã được sử dụng.");
    }
    private static boolean constraint(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause())
            if (c instanceof org.hibernate.exception.ConstraintViolationException) return true;
        return false;
    }
    // ĐĂNG KÝ: kiểm tra dữ liệu, hash mật khẩu, ngăn trùng định danh; service tự cấp KHACH_HANG/HOAT_DONG.
    public Profile register(AccountInputs.DangKy input) {
        String fullName = name(input.hoTen()), address = email(input.email()), number = phone(input.soDienThoai()), identity = cccd(input.cccd());
        birth(input.ngaySinh()); Passwords.validate(input.matKhau(), "matKhau");
        String passwordHash = Passwords.hash(input.matKhau());
        try {
            return tx(em -> {
                NguoiDungRepository repo = new NguoiDungRepository(em);
                if (repo.duplicate("", address, number, identity)) throw duplicate();
                NguoiDung n = new NguoiDung(); n.maNguoiDung = UUID.randomUUID().toString();
                n.hoTen = fullName; n.email = address; n.soDienThoai = number; n.cccd = identity;
                n.gioiTinh = input.gioiTinh(); n.ngaySinh = input.ngaySinh(); n.matKhau = passwordHash;
                n.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG; n.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
                repo.save(n); em.flush(); return view(n);
            });
        } catch (PersistenceException e) { if (constraint(e)) throw duplicate(); throw e; }
    }
    // LOGIN ← AccountController: chuẩn hóa email → tìm user → so mật khẩu/trạng thái → lưu phiên → trả Login.
    public Login login(AccountInputs.DangNhap input) {
        String address;
        try { address = email(input.email()); }
        catch (AccountException e) { address = "invalid@invalid.test"; }
        final String normalized = address;
        return tx(em -> {
            // BƯỚC 1: repository tìm bằng JPQL; nếu có user thì lock và refresh trước khi quyết định đăng nhập.
            NguoiDung n = new NguoiDungRepository(em).byEmail(normalized);
            if (n != null) n = new NguoiDungRepository(em).lock(n.maNguoiDung);
            // BƯỚC 2: PBKDF2 so mật khẩu nhập với hash đã lưu (hoặc hash giả nếu không tìm thấy user).
            boolean valid = Passwords.matches(input.matKhau(), n == null ? DUMMY_HASH : n.matKhau);
            // BƯỚC 3: sai mật khẩu, không tồn tại hoặc bị khóa đều bị từ chối chung 401.
            if (!valid || n == null || n.trangThai != TrangThaiTaiKhoan.HOAT_DONG)
                throw new AccountException(401, "UNAUTHENTICATED", "Email hoặc mật khẩu không đúng, hoặc tài khoản không thể đăng nhập.");
            // BƯỚC 4: dọn phiên hết hạn rồi tạo phiên mới; DB chỉ lưu hash token, CSRF và thời gian.
            em.createQuery("delete from AuthSession s where s.hetHan <= :now").setParameter("now", clock.instant()).executeUpdate();
            String token = randomToken(); AuthSession session = new AuthSession();
            session.tokenHash = tokenHash(token); session.nguoiDung = n; session.csrfToken = randomToken();
            session.taoLuc = clock.instant(); session.hetHan = session.taoLuc.plus(lifetime);
            // persist đăng ký entity với JPA; tx() commit trước khi kết quả quay về Controller đặt cookie.
            em.persist(session); return new Login(token, session.csrfToken, session.hetHan, view(n));
        });
    }
    // XÁC THỰC CÁC REQUEST SAU LOGIN: Filter gọi bằng token cookie, không cần gửi lại email/mật khẩu.
    public Identity authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthenticated();
        return tx(em -> {
            // Hash token để tra phiên; từ chối nếu không tồn tại/hết hạn rồi kiểm tra tài khoản còn hoạt động.
            AuthSession s = em.find(AuthSession.class, tokenHash(token));
            if (s == null || !clock.instant().isBefore(s.hetHan)) throw unauthenticated();
            NguoiDung n = active(em, s.nguoiDung.maNguoiDung);
            // Re-read after locking the account: logout/change-password may have revoked it meanwhile.
            try { em.refresh(s); } catch (EntityNotFoundException e) { throw unauthenticated(); }
            if (!clock.instant().isBefore(s.hetHan)) throw unauthenticated();
            // Role/nhà xe/nhân viên lấy từ DB hiện tại; không tin role do frontend tự gửi. /me không gia hạn phiên ở đây.
            return new Identity(new CurrentUser(n.maNguoiDung, n.loaiTaiKhoan, n.nhaXe == null ? null : n.nhaXe.maNhaXe, n.nhanVien == null ? null : n.nhanVien.maNhanVien, s.tokenHash), s.csrfToken, s.hetHan);
        });
    }
    public Profile profile(CurrentUser user) { return tx(em -> view(AccessControl.authenticated(em, user, clock.instant()))); }
    public Profile update(CurrentUser user, AccountInputs.HoSo input) {
        String fullName = name(input.hoTen()), address = email(input.email()), number = phone(input.soDienThoai()), identity = cccd(input.cccd());
        birth(input.ngaySinh());
        try {
            return tx(em -> {
                NguoiDung n = AccessControl.authenticated(em, user, clock.instant());
                if (new NguoiDungRepository(em).duplicate(n.maNguoiDung, address, number, identity)) throw duplicate();
                n.hoTen = fullName; n.email = address; n.soDienThoai = number; n.cccd = identity;
                n.gioiTinh = input.gioiTinh(); n.ngaySinh = input.ngaySinh(); em.flush(); return view(n);
            });
        } catch (PersistenceException e) { if (constraint(e)) throw duplicate(); throw e; }
    }
    // LOGOUT: xóa đúng phiên hiện tại và đúng user; các phiên khác của user không bị xóa bởi thao tác này.
    public void logout(String token, CurrentUser user) {
        tx(em -> {
            AccessControl.authenticated(em, user, clock.instant());
            em.createQuery("delete from AuthSession s where s.tokenHash = :token and s.nguoiDung.maNguoiDung = :id")
                .setParameter("token", tokenHash(token)).setParameter("id", user.maNguoiDung()).executeUpdate(); return null;
        });
    }
    // ĐỔI MẬT KHẨU: xác minh mật khẩu hiện tại, hash mật khẩu mới và thu hồi tất cả phiên của user.
    public void changePassword(CurrentUser user, AccountInputs.DoiMatKhau input) {
        Passwords.validate(input.matKhauMoi(), "matKhauMoi");
        tx(em -> {
            NguoiDung n = AccessControl.authenticated(em, user, clock.instant());
            if (!Passwords.matches(input.matKhauHienTai(), n.matKhau))
                throw AccountException.invalid("matKhauHienTai", "Mật khẩu hiện tại không đúng.");
            if (Passwords.matches(input.matKhauMoi(), n.matKhau))
                throw AccountException.invalid("matKhauMoi", "Mật khẩu mới phải khác mật khẩu hiện tại.");
            n.matKhau = Passwords.hash(input.matKhauMoi()); revoke(em, n); return null;
        });
    }
    // DANH SÁCH QUẢN TRỊ: kiểm tra phiên và role ADMIN trong transaction trước khi đếm/lấy dữ liệu phân trang.
    public Map<String, Object> accounts(CurrentUser user, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw AccountException.invalid("page", "Phân trang không hợp lệ.");
        return tx(em -> {
            AccessControl.role(AccessControl.authenticated(em, user, clock.instant()), LoaiTaiKhoan.ADMIN);
            long count = em.createQuery("select count(n) from NguoiDung n", Long.class).getSingleResult();
            long offset = ((long) page - 1) * size;
            var items = offset >= count ? List.<Profile>of() : em.createQuery("select n from NguoiDung n order by n.maNguoiDung", NguoiDung.class)
                .setFirstResult(Math.toIntExact(offset)).setMaxResults(size).getResultList()
                .stream().map(AccountService::view).toList();
            return Map.of("items", items, "page", page, "size", size, "totalItems", count, "totalPages", (count + size - 1) / size);
        });
    }
    // KHÓA/MỞ KHÓA: chỉ ADMIN, không tự đổi trạng thái tài khoản mình; thu hồi phiên của tài khoản mục tiêu.
    public Profile status(CurrentUser user, String id, AccountInputs.TrangThai input) {
        if (input.trangThai() != TrangThaiTaiKhoan.KHOA && input.trangThai() != TrangThaiTaiKhoan.HOAT_DONG)
            throw AccountException.invalid("trangThai", "Chỉ cho phép khóa hoặc mở khóa tài khoản.");
        return tx(em -> {
            AccessControl.role(AccessControl.authenticated(em, user, clock.instant()), LoaiTaiKhoan.ADMIN);
            if (id.equals(user.maNguoiDung())) throw new AccountException(409, "INVALID_STATE_TRANSITION", "Không tự thay đổi trạng thái tài khoản quản trị.");
            NguoiDung n = new NguoiDungRepository(em).lock(id);
            if (n == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
            n.trangThai = input.trangThai(); revoke(em, n);
            return view(n);
        });
    }
    // Thu hồi mọi phiên của một user: dùng khi đổi mật khẩu hoặc đổi trạng thái tài khoản.
    private static void revoke(EntityManager em, NguoiDung n) {
        em.createQuery("delete from AuthSession s where s.nguoiDung = :user").setParameter("user", n).executeUpdate();
    }
    // Sinh 32 byte ngẫu nhiên → Base64 URL không padding, 43 ký tự; đây không phải JWT.
    private static String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    // SHA-256 dùng cho token ngẫu nhiên để đối chiếu phiên; mật khẩu người dùng dùng PBKDF2 riêng.
    public static String tokenHash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
