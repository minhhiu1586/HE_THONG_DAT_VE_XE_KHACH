package com.datvexe.repository;

import com.datvexe.domain.Entities.PhuongThucThanhToan;
import com.datvexe.domain.States.TrangThaiPhuongThuc;
import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;

/** Dùng EntityManager và transaction do service quản lý. */
public final class PhuongThucThanhToanRepository extends JpaRepository<PhuongThucThanhToan> {
    public PhuongThucThanhToanRepository(EntityManager em) {
        super(em, PhuongThucThanhToan.class);
    }

    @Override
    public void save(PhuongThucThanhToan phuongThuc) {
        try {
            em.persist(phuongThuc);
            em.flush();
        } catch (PersistenceException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "phuong_thuc_thanh_toan_pkey".equals(violation.getConstraintName())) {
                    throw new AccountException(
                            409, "CONFLICT", "Mã phương thức thanh toán đã tồn tại.");
                }
            }
            throw e;
        }
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
