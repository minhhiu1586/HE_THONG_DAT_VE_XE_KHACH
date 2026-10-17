package com.datvexe.security;

import com.datvexe.domain.States.LoaiTaiKhoan;

/** Trusted snapshot; protected operations revalidate current DB state in their transaction. */
public record CurrentUser(String maNguoiDung, LoaiTaiKhoan loaiTaiKhoan,
                          String maNhaXe, String maNhanVien, String sessionHash) {}
