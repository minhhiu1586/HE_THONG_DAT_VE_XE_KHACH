package com.datvexe.repository;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

public final class ThanhToanRepository extends JpaRepository<ThanhToan> {
    public ThanhToanRepository(EntityManager em) {
        super(em, ThanhToan.class);
    }

    public long countCompetingHolds(String tripId, Set<String> seatIds,
                                   String orderId, LocalDateTime now) {
        return em.createQuery("""
                        select count(h) from GiuCho h
                        where h.chuyenXe.maChuyen = :trip and h.ghe.maGhe in :seats
                          and h.trangThai = :state and h.thoiDiemHetHan > :now
                          and (h.donDatVe is null or h.donDatVe <> :order)
                        """, Long.class)
                .setParameter("trip", tripId).setParameter("seats", seatIds)
                .setParameter("state", TrangThaiGiuCho.DANG_GIU)
                .setParameter("now", now).setParameter("order", em.getReference(DonDatVe.class, orderId))
                .getSingleResult();
    }

    public List<String> tripIdsFromHolds(String orderId) {
        return em.createQuery("select distinct h.chuyenXe.maChuyen from GiuCho h where h.donDatVe.maDon = :order", String.class)
                .setParameter("order", orderId).getResultList();
    }

    public DonDatVe findOrder(String id) {
        return em.find(DonDatVe.class, id);
    }

    public ChuyenXe findTrip(String id) {
        return em.find(ChuyenXe.class, id);
    }

    public PhuongThucThanhToan findMethod(String id) {
        return em.find(PhuongThucThanhToan.class, id);
    }

    public List<GiuCho> holdsForOrder(String orderId) {
        return em.createQuery("select h from GiuCho h where h.donDatVe.maDon = :order", GiuCho.class)
                .setParameter("order", orderId).getResultList();
    }

    public List<GiuCho> refreshedHoldsForOrder(String orderId) {
        List<GiuCho> holds = holdsForOrder(orderId);
        for (GiuCho hold : holds) {
            em.refresh(hold);
        }
        return holds;
    }

    public KhuyenMai lockPromotion(String id) {
        KhuyenMai promotion = em.find(KhuyenMai.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (promotion != null) {
            em.refresh(promotion, LockModeType.PESSIMISTIC_WRITE);
        }
        return promotion;
    }

    public ThanhToan lockById(String id) {
        ThanhToan payment = em.find(ThanhToan.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (payment != null) {
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        }
        return payment;
    }

    public boolean hasCollectedForOrder(String orderId) {
        return em.createQuery("""
                        select count(t)
                        from ThanhToan t
                        where t.donDatVe.maDon = :order
                          and t.trangThai in :states
                        """, Long.class)
                .setParameter("order", orderId)
                .setParameter("states", List.of(
                        TrangThaiThanhToan.THANH_CONG,
                        TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN,
                        TrangThaiThanhToan.DA_HOAN_TIEN))
                .getSingleResult() > 0;
    }

    public List<ThanhToan> forOrder(String orderId) {
        return em.createQuery("""
                        select t from ThanhToan t
                        where t.donDatVe.maDon = :order
                        order by t.maGiaoDich
                        """, ThanhToan.class)
                .setParameter("order", orderId)
                .getResultList();
    }

    public long countForOrder(String orderId, TrangThaiThanhToan state) {
        return em.createQuery(
                        "select count(t) from ThanhToan t where t.donDatVe.maDon = :order"
                                + " and (:state is null or t.trangThai = :state)",
                        Long.class)
                .setParameter("order", orderId)
                .setParameter("state", state)
                .getSingleResult();
    }

    public List<ThanhToan> pageForOrder(
            String orderId, TrangThaiThanhToan state,
            int offset, int limit, boolean descending) {
        String jpql = "select t from ThanhToan t where t.donDatVe.maDon = :order"
                + " and (:state is null or t.trangThai = :state)"
                + " order by t.maGiaoDich " + (descending ? "desc" : "asc");
        return em.createQuery(jpql, ThanhToan.class)
                .setParameter("order", orderId)
                .setParameter("state", state)
                .setFirstResult(Math.max(0, offset))
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    public ThanhToan pendingForOrder(String orderId) {
        List<ThanhToan> values = em.createQuery("""
                        select t from ThanhToan t
                        where t.donDatVe.maDon = :order and t.trangThai = :state
                        order by t.maGiaoDich
                        """, ThanhToan.class)
                .setParameter("order", orderId)
                .setParameter("state", TrangThaiThanhToan.CHO_XU_LY)
                .setMaxResults(1)
                .getResultList();
        return values.isEmpty() ? null : values.get(0);
    }
}
