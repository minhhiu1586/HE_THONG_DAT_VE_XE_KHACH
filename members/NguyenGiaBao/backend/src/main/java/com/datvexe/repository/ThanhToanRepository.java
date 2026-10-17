package com.datvexe.repository;

import com.datvexe.domain.Entities.ThanhToan;
import com.datvexe.domain.States.TrangThaiThanhToan;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;

public final class ThanhToanRepository extends JpaRepository<ThanhToan> {
    public ThanhToanRepository(EntityManager em) { super(em, ThanhToan.class); }

    public ThanhToan lockById(String id) {
        ThanhToan payment = em.find(ThanhToan.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (payment != null) em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        return payment;
    }

    public List<ThanhToan> forOrder(String orderId) {
        return em.createQuery("select t from ThanhToan t where t.donDatVe.maDon = :order "
                        + "order by t.maGiaoDich", ThanhToan.class)
                .setParameter("order", orderId).getResultList();
    }

    public long countForOrder(String orderId, TrangThaiThanhToan state) {
        String jpql = "select count(t) from ThanhToan t where t.donDatVe.maDon = :order"
                + (state == null ? "" : " and t.trangThai = :state");
        var query = em.createQuery(jpql, Long.class).setParameter("order", orderId);
        if (state != null) query.setParameter("state", state);
        return query.getSingleResult();
    }

    public List<ThanhToan> pageForOrder(String orderId, TrangThaiThanhToan state, int offset, int limit) {
        return pageForOrder(orderId, state, offset, limit, false);
    }

    public List<ThanhToan> pageForOrder(String orderId, TrangThaiThanhToan state, int offset, int limit, boolean descending) {
        String jpql = "select t from ThanhToan t where t.donDatVe.maDon = :order"
                + (state == null ? "" : " and t.trangThai = :state")
                + " order by t.maGiaoDich " + (descending ? "desc" : "asc");
        var query = em.createQuery(jpql, ThanhToan.class).setParameter("order", orderId)
                .setFirstResult(Math.max(0, offset)).setMaxResults(Math.max(1, limit));
        if (state != null) query.setParameter("state", state);
        return query.getResultList();
    }

    public ThanhToan pendingForOrder(String orderId) {
        List<ThanhToan> values = em.createQuery("select t from ThanhToan t where t.donDatVe.maDon = :order "
                        + "and t.trangThai = :state order by t.maGiaoDich", ThanhToan.class)
                .setParameter("order", orderId).setParameter("state", TrangThaiThanhToan.CHO_XU_LY)
                .setMaxResults(1).getResultList();
        return values.isEmpty() ? null : values.get(0);
    }
}
