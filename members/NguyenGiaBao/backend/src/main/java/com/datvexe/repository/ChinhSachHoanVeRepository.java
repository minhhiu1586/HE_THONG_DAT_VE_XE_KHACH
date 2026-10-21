package com.datvexe.repository;

import com.datvexe.domain.Entities.ChinhSachHoanVe;
import com.datvexe.domain.States.TrangThaiChinhSach;
import jakarta.persistence.EntityManager;
import java.util.List;

/** Truy vấn chính sách hoàn vé trong transaction do service quản lý. */
public final class ChinhSachHoanVeRepository extends JpaRepository<ChinhSachHoanVe> {
    private static final long CONFIGURATION_LOCK_KEY = 0x4353484F414E5645L;

    public ChinhSachHoanVeRepository(EntityManager em) {
        super(em, ChinhSachHoanVe.class);
    }

    public List<ChinhSachHoanVe> list(int offset, int limit) {
        return em.createQuery(
                        "select p from ChinhSachHoanVe p "
                                + "order by p.maChinhSach",
                        ChinhSachHoanVe.class)
                .setFirstResult(Math.max(0, offset))
                .setMaxResults(Math.max(0, limit))
                .getResultList();
    }

    public long count() {
        return em.createQuery(
                        "select count(p) from ChinhSachHoanVe p",
                        Long.class)
                .getSingleResult();
    }

    /** Lấy chính sách đang áp dụng toàn hệ thống, hoặc null nếu chưa có. */
    public ChinhSachHoanVe active() {
        List<ChinhSachHoanVe> matches = em.createQuery(
                        "select p from ChinhSachHoanVe p "
                                + "where p.trangThai = :active",
                        ChinhSachHoanVe.class)
                .setParameter("active", TrangThaiChinhSach.HOAT_DONG)
                .setMaxResults(1)
                .getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Tạm ngưng chính sách đang áp dụng; null nghĩa là tạm ngưng tất cả, còn mã cụ thể
     * được giữ nguyên trạng thái.
     */
    public void deactivateActiveExcept(String maChinhSach) {
        em.flush();
        var update = em.createQuery(
                "update ChinhSachHoanVe p "
                        + "set p.trangThai = :inactive "
                        + "where p.trangThai = :active "
                        + "and (:id is null or p.maChinhSach <> :id)");
        update.setParameter("inactive", TrangThaiChinhSach.TAM_NGUNG);
        update.setParameter("active", TrangThaiChinhSach.HOAT_DONG);
        update.setParameter("id", maChinhSach);
        update.executeUpdate();
        em.clear();
    }

    /** Chính sách đã được dùng khi có đơn tham chiếu, kể cả đơn chưa thanh toán. */
    public boolean used(String maChinhSach) {
        return em.createQuery(
                        "select count(d) from DonDatVe d "
                                + "where d.chinhSachHoanVe.maChinhSach = :id",
                        Long.class)
                .setParameter("id", maChinhSach)
                .getSingleResult() > 0;
    }

    /** Khóa cấu hình trước khi khóa dòng chính sách hoặc đơn đặt vé. */
    public void lockConfiguration() {
        em.createNativeQuery("select pg_advisory_xact_lock(?1)")
                .setParameter(1, CONFIGURATION_LOCK_KEY)
                .getSingleResult();
    }
}
