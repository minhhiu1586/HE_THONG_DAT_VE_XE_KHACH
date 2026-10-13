package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.GiuCho;
import com.datvexe.dto.HoldSeatRequest;
import com.datvexe.dto.HoldSeatResponse;
import com.datvexe.repository.GiuChoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BookingService {

    private GiuChoRepository giuChoRepository = new GiuChoRepository();

    public HoldSeatResponse holdSeats(HoldSeatRequest request) {
        // Dùng hàm Jpa.write() của bạn để tự động quản lý Transaction
        return Jpa.write(em -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiredAt = now.plusMinutes(15);
            String holdSessionId = "HOLD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 1. Kiểm tra xem ghế có ai đang giữ không
            List<String> gheDangBiGiu = giuChoRepository.timCacGheDangBiGiu(
                    em, request.getMaChuyen(), request.getDanhSachMaGhe(), now);

            // Nếu trùng ghế, throw exception (Jpa.write sẽ tự động rollback)
            if (!gheDangBiGiu.isEmpty()) {
                throw new RuntimeException("Ghế đang được giữ bởi người khác: " + String.join(", ", gheDangBiGiu));
            }

            // 2. Lưu xuống Database
            for (String maGhe : request.getDanhSachMaGhe()) {
                GiuCho giuCho = new GiuCho();
                giuCho.setMaGiuCho(UUID.randomUUID().toString());
                giuCho.setMaChuyen(request.getMaChuyen());
                giuCho.setMaGhe(maGhe);
                giuCho.setMaDon(holdSessionId);
                giuCho.setNgayDat(now);
                giuCho.setThoiDiemBatDau(now);
                giuCho.setThoiDiemHetHan(expiredAt);
                giuCho.setTrangThai("DANG_GIU");

                giuChoRepository.save(em, giuCho);
            }

            return new HoldSeatResponse(holdSessionId, expiredAt, request.getDanhSachMaGhe());
        });
    }

    // Hàm gọi bởi Scheduler mỗi phút
    public void releaseExpiredHolds() {
        Jpa.write(em -> {
            int updatedCount = giuChoRepository.huyGiuChoQuaHan(em, LocalDateTime.now());
            if (updatedCount > 0) {
                System.out.println("[Scheduler] Đã giải phóng " + updatedCount + " ghế quá hạn.");
            }
            return updatedCount;
        });
    }
}