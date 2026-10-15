package com.datvexe.domain;

import jakarta.persistence.*;
import java.time.Instant;

// JPA ánh xạ phiên đăng nhập vào phien_dang_nhap; một user có thể có nhiều phiên trên các trình duyệt.
@Entity(name = "AuthSession")
@Table(name = "phien_dang_nhap", indexes = {@Index(name = "ix_phien_nguoi_dung", columnList = "ma_nguoi_dung"),
    @Index(name = "ix_phien_het_han", columnList = "het_han")})
public class AuthSession {
    // Khóa tra phiên là SHA-256 của token; raw token nằm trong cookie HttpOnly ở browser.
    @Id @Column(name = "token_hash", length = 64) public String tokenHash;
    // Nhiều phiên thuộc một người dùng; khóa ngoại ma_nguoi_dung liên kết tới entity NguoiDung.
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nguoi_dung", nullable = false) public Entities.NguoiDung nguoiDung;
    // CSRF được gửi về frontend để gắn vào header thao tác thay đổi dữ liệu; khác với token cookie xác định phiên.
    @Column(name = "csrf_token", nullable = false, length = 64) public String csrfToken;
    @Column(name = "tao_luc", nullable = false) public Instant taoLuc;
    // Backend kiểm tra hạn này ở mỗi lần xác thực; chỉnh cookie client không tự kéo dài phiên trong DB.
    @Column(name = "het_han", nullable = false) public Instant hetHan;
    public AuthSession() {}
}
