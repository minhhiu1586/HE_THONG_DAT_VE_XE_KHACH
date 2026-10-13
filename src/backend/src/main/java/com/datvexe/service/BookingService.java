package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.GiuCho;
import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.Entities.Ghe;
import com.datvexe.domain.States.TrangThaiGiuCho;
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

            ChuyenXe chuyenXe = em.find(ChuyenXe.class, request.getMaChuyen());
            if (chuyenXe == null) {
                throw new IllegalArgumentException("Chuyến xe không tồn tại");
            }

            // 2. Lưu xuống Database
            for (String maGhe : request.getDanhSachMaGhe()) {
                Ghe ghe = em.find(Ghe.class, maGhe);
                if (ghe == null || chuyenXe.xe == null || !chuyenXe.xe.maXe.equals(ghe.xe.maXe)) {
                    throw new IllegalArgumentException("Ghế không thuộc xe của chuyến: " + maGhe);
                }
                GiuCho giuCho = new GiuCho();
                giuCho.maGiuCho = UUID.randomUUID().toString();
                giuCho.chuyenXe = chuyenXe;
                giuCho.ghe = ghe;
                giuCho.ngayDat = now;
                giuCho.thoiDiemBatDau = now;
                giuCho.thoiDiemHetHan = expiredAt;
                giuCho.trangThai = TrangThaiGiuCho.DANG_GIU;

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