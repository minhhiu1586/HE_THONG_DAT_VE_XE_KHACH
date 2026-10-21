package com.datvexe.service;

import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.dto.AccountResponses.Profile;

final class AccountMapper {
    private AccountMapper() {}

    static Profile profile(NguoiDung user) {
        return new Profile(
                user.maNguoiDung,
                user.hoTen,
                user.email,
                user.soDienThoai,
                user.gioiTinh,
                user.ngaySinh,
                user.cccd,
                user.loaiTaiKhoan,
                user.trangThai);
    }
}
