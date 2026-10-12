package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiNhaXe;
import java.time.LocalDate;

/** Admin-only inputs. Role and operator scope are never accepted from a public registration request. */
public final class OperatorInputs {
    private OperatorInputs() {}
    public record NhaXe(String maNhaXe, String tenNhaXe, String hotline, String email,
                        String diaChi, String moTa, String logoUrl, LocalDate ngayThanhLap) {}
    public record TrangThai(TrangThaiNhaXe trangThai) {}
    public record QuanLy(String maNguoiDung) {}
}
