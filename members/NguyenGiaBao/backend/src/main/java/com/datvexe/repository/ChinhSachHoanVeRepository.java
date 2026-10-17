package com.datvexe.repository;

import com.datvexe.domain.Entities.ChinhSachHoanVe;
import com.datvexe.domain.States.TrangThaiChinhSach;
import jakarta.persistence.EntityManager;
import java.util.List;

/** Refund policy queries; all operations participate in the caller's transaction. */
public final class ChinhSachHoanVeRepository extends JpaRepository<ChinhSachHoanVe> {
    private static final long CONFIGURATION_LOCK_KEY = 0x4353484F414E5645L;

    public ChinhSachHoanVeRepository(EntityManager em) {
        super(em, ChinhSachHoanVe.class);
    }

    public List<ChinhSachHoanVe> list(int offset, int limit) {
        return em.createQuery(
                        "select p from ChinhSachHoanVe p order by p.maChinhSach",
                        ChinhSachHoanVe.class)
                .setFirstResult(Math.max(0, offset))
                .setMaxResults(Math.max(0, limit))
                .getResultList();
    }

    public long count() {
        return em.createQuery("select count(p) from ChinhSachHoanVe p", Long.class)
                .getSingleResult();
    }

    /** Returns the global active policy, or null when none is configured. */
    public ChinhSachHoanVe active() {
        List<ChinhSachHoanVe> matches = em.createQuery(
                        "select p from ChinhSachHoanVe p where p.trangThai = :active",
                        ChinhSachHoanVe.class)
                .setParameter("active", TrangThaiChinhSach.HOAT_DONG)
                .setMaxResults(1)
                .getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Deactivates the current policy and clears stale managed status values. */
    public void deactivateActive() {
        deactivateActiveExcept(null);
    }

    /** Deactivates the current policy unless it is the requested target policy. */
    public void deactivateActiveExcept(String maChinhSach) {
        em.flush();
        var update = em.createQuery(
                "update ChinhSachHoanVe p set p.trangThai = :inactive "
                        + "where p.trangThai = :active"
                        + (maChinhSach == null ? "" : " and p.maChinhSach <> :id"));
        update.setParameter("inactive", TrangThaiChinhSach.TAM_NGUNG);
        update.setParameter("active", TrangThaiChinhSach.HOAT_DONG);
        if (maChinhSach != null) update.setParameter("id", maChinhSach);
        update.executeUpdate();
        em.clear();
    }

    /** A policy is used as soon as any order references it, including pending orders. */
    public boolean used(String maChinhSach) {
        return em.createQuery(
                        "select count(d) from DonDatVe d "
                                + "where d.chinhSachHoanVe.maChinhSach = :id",
                        Long.class)
                .setParameter("id", maChinhSach)
                .getSingleResult() > 0;
    }

    /**
     * Serializes global policy changes until the surrounding transaction ends. Call this
     * before taking policy or booking rows so policy activation has a consistent lock order.
     */
    public void lockConfiguration() {
        em.createNativeQuery("select pg_advisory_xact_lock(?1)")
                .setParameter(1, CONFIGURATION_LOCK_KEY)
                .getSingleResult();
    }
}
