package com.datvexe.repository;

import com.datvexe.domain.GiuCho;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

public class GiuChoRepository {

        // 1. Kiểm tra ghế đang bị giữ
        public List<String> timCacGheDangBiGiu(EntityManager em, String maChuyen, List<String> danhSachGhe,
                        LocalDateTime hienTai) {
                String jpql = "SELECT g.maGhe FROM GiuCho g WHERE g.maChuyen = :maChuyen " +
                                "AND g.maGhe IN :danhSachGhe " +
                                "AND g.trangThai = 'DANG_GIU' " +
                                "AND g.thoiDiemHetHan > :hienTai";

                return em.createQuery(jpql, String.class)
                                .setParameter("maChuyen", maChuyen)
                                .setParameter("danhSachGhe", danhSachGhe)
                                .setParameter("hienTai", hienTai)
                                .getResultList();
        }

        // 2. Dọn dẹp ghế quá hạn
        public int huyGiuChoQuaHan(EntityManager em, LocalDateTime hienTai) {
                String jpql = "UPDATE GiuCho g SET g.trangThai = 'DA_HUY' " +
                                "WHERE g.trangThai = 'DANG_GIU' AND g.thoiDiemHetHan < :hienTai";

                return em.createQuery(jpql)
                                .setParameter("hienTai", hienTai)
                                .executeUpdate();
        }

        // 3. Lưu xuống Database
        public void save(EntityManager em, GiuCho giuCho) {
                em.persist(giuCho);
        }
}