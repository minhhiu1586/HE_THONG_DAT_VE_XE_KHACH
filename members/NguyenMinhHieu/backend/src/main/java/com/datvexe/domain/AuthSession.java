package com.datvexe.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity(name = "AuthSession")
@Table(name = "phien_dang_nhap", indexes = {@Index(name = "ix_phien_nguoi_dung", columnList = "ma_nguoi_dung"),
    @Index(name = "ix_phien_het_han", columnList = "het_han")})
public class AuthSession {
    @Id @Column(name = "token_hash", length = 64) public String tokenHash;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nguoi_dung", nullable = false) public Entities.NguoiDung nguoiDung;
    @Column(name = "csrf_token", nullable = false, length = 64) public String csrfToken;
    @Column(name = "tao_luc", nullable = false) public Instant taoLuc;
    @Column(name = "het_han", nullable = false) public Instant hetHan;
    public AuthSession() {}
}
