package com.datvexe.repository;

import com.datvexe.domain.Entities.GiuCho;
import com.datvexe.domain.States.TrangThaiGiuCho;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class GiuChoRepository {
        private final EntityManager em;

        public GiuChoRepository() { this.em = null; }
        public GiuChoRepository(EntityManager em) { this.em = em; }

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

        // 2. Lưu xuống Database
        public void save(EntityManager em, GiuCho giuCho) {
                em.persist(giuCho);
        }

        public List<GiuCho> forOrder(String maDon) {
                return entityManager().createQuery("select g from GiuCho g "
                                + "where g.donDatVe.maDon = :order", GiuCho.class)
                                .setParameter("order", maDon)
                                .getResultList();
        }

        public List<GiuCho> activeForOrder(String maDon) {
                return activeForOrder(maDon, LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        }

        public List<GiuCho> activeForOrder(String maDon, LocalDateTime now) {
                return entityManager().createQuery("select g from GiuCho g "
                                + "where g.donDatVe.maDon = :order and g.trangThai = :holding "
                                + "and g.thoiDiemHetHan > :now", GiuCho.class)
                                .setParameter("order", maDon)
                                .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                                .setParameter("now", now)
                                .getResultList();
        }

        public List<GiuCho> forTrip(String maChuyen) {
                return entityManager().createQuery("select g from GiuCho g "
                                + "where g.chuyenXe.maChuyen = :trip", GiuCho.class)
                                .setParameter("trip", maChuyen)
                                .getResultList();
        }

        private EntityManager entityManager() {
                if (em == null) {
                        throw new IllegalStateException("EntityManager is required for this repository method.");
                }
                return em;
        }
}
