package com.datvexe.repository;

import com.datvexe.domain.Entities.GiuCho;
import com.datvexe.domain.States.TrangThaiGiuCho;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

public class GiuChoRepository {

        // 1. Kiểm tra ghế đang bị giữ
        public List<String> timCacGheDangBiGiu(EntityManager em, String maChuyen, List<String> danhSachGhe,
                        LocalDateTime hienTai) {
                String jpql = "SELECT g.ghe.maGhe FROM GiuCho g WHERE g.chuyenXe.maChuyen = :maChuyen " +
                                "AND g.ghe.maGhe IN :danhSachGhe " +
                                "AND g.trangThai = :dangGiu " +
                                "AND g.thoiDiemHetHan > :hienTai";

                return em.createQuery(jpql, String.class)
                                .setParameter("maChuyen", maChuyen)
                                .setParameter("danhSachGhe", danhSachGhe)
                                .setParameter("dangGiu", TrangThaiGiuCho.DANG_GIU)
                                .setParameter("hienTai", hienTai)
                                .getResultList();
        }

        // 2. Dọn dẹp ghế quá hạn
        public int huyGiuChoQuaHan(EntityManager em, LocalDateTime hienTai) {
                String jpql = "UPDATE GiuCho g SET g.trangThai = :daHuy " +
                                "WHERE g.trangThai = :dangGiu AND g.thoiDiemHetHan < :hienTai";

                return em.createQuery(jpql)
                                .setParameter("daHuy", TrangThaiGiuCho.DA_HUY)
                                .setParameter("dangGiu", TrangThaiGiuCho.DANG_GIU)
                                .setParameter("hienTai", hienTai)
                                .executeUpdate();
        }

        // 3. Lưu xuống Database
        public void save(EntityManager em, GiuCho giuCho) {
                em.persist(giuCho);
        }
}
