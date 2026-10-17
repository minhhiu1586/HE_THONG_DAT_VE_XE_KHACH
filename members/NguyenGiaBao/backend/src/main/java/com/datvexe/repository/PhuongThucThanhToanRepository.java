package com.datvexe.repository;

import com.datvexe.domain.Entities.PhuongThucThanhToan;
import com.datvexe.domain.States.TrangThaiPhuongThuc;
import jakarta.persistence.EntityManager;
import java.util.List;

/** Dùng EntityManager và transaction do service quản lý. */
public final class PhuongThucThanhToanRepository extends JpaRepository<PhuongThucThanhToan> {
    public PhuongThucThanhToanRepository(EntityManager em) {
        super(em, PhuongThucThanhToan.class);
    }

    @Override
    public List<PhuongThucThanhToan> list() {
        return em.createQuery(
                "select p from PhuongThucThanhToan p order by p.maPhuongThuc",
                PhuongThucThanhToan.class).getResultList();
    }

    public List<PhuongThucThanhToan> danhSachKhaDung() {
        return em.createQuery(
                "select p from PhuongThucThanhToan p where p.trangThai = :trangThai "
                        + "order by p.maPhuongThuc",
                PhuongThucThanhToan.class)
                .setParameter("trangThai", TrangThaiPhuongThuc.HOAT_DONG)
                .getResultList();
    }
}
