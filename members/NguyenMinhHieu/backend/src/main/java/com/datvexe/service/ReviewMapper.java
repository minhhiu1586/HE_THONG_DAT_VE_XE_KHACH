package com.datvexe.service;

import com.datvexe.domain.Entities.DanhGia;

import java.util.*;

final class ReviewMapper {
    private ReviewMapper() {}

    static Map<String, Object> view(DanhGia review, boolean publicView) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maDanhGia", review.maDanhGia);
        result.put("maChuyen", review.chuyenXe.maChuyen);
        result.put("soSao", review.soSao);
        result.put("binhLuan", Objects.toString(review.binhLuan, ""));
        result.put("ngayDanhGia", review.ngayDanhGia.toString());
        result.put("tenHienThi", "Khách đã đặt vé");
        if (!publicView) {
            result.put("maNguoiDung", review.nguoiDung.maNguoiDung);
            result.put("trangThai", review.daXoa ? "DA_XOA" : review.trangThai.name());
        }
        return result;
    }

    static Map<String, Object> page(
            List<DanhGia> rows, int page, int size, long total, boolean publicView) {
        return Map.of(
                "items",
                rows.stream().map(d -> view(d, publicView)).toList(),
                "page",
                page,
                "totalItems",
                total,
                "totalPages",
                (total + size - 1) / size);
    }
}
