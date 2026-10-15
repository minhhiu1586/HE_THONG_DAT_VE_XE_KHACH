package com.datvexe.security;

import com.datvexe.domain.States.LoaiTaiKhoan;

/** Trusted snapshot; protected operations revalidate current DB state in their transaction. */
// Danh tính server dựng từ DB sau khi xác thực; sessionHash giúp service kiểm tra lại phiên. Không nhận role tùy ý từ client.
public record CurrentUser(String maNguoiDung, LoaiTaiKhoan loaiTaiKhoan,
                          String maNhaXe, String maNhanVien, String sessionHash) {}
