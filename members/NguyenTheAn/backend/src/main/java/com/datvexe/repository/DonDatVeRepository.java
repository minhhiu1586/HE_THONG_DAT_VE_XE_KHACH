package com.datvexe.repository;

import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.domain.States.TrangThaiGiuCho;
import com.datvexe.domain.States.TrangThaiThanhToan;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

public final class DonDatVeRepository extends JpaRepository<DonDatVe> {
    public DonDatVeRepository(EntityManager em) { super(em, DonDatVe.class); }

    /** One live frozen order reserves one use; expired holds do not wait for the scheduler. */
    public long reservedPromotionUses(String promotionId, String excludedOrderId, LocalDateTime now) {
        return em.createQuery("select count(d) from DonDatVe d "
                + "where d.maKhuyenMaiDaApDung = :promotion and d.maDon <> :excluded "
                + "and d.trangThai = :frozen "
                + "and exists (select h.maGiuCho from GiuCho h where h.donDatVe = d "
                + "and h.trangThai = :holding and h.thoiDiemHetHan > :now) "
                + "and not exists (select h.maGiuCho from GiuCho h where h.donDatVe = d "
                + "and h.trangThai = :holding and h.thoiDiemHetHan <= :now)", Long.class)
                .setParameter("promotion", promotionId).setParameter("excluded", excludedOrderId)
                .setParameter("frozen", TrangThaiDon.CHO_THANH_TOAN)
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU).setParameter("now", now)
                .getSingleResult();
    }

    public boolean hasBlockingPayment(String orderId) {
        return em.createQuery("select count(t) from ThanhToan t where t.donDatVe.maDon = :order "
                + "and t.trangThai in :states", Long.class)
                .setParameter("order", orderId)
                .setParameter("states", List.of(TrangThaiThanhToan.CHO_XU_LY, TrangThaiThanhToan.THANH_CONG,
                        TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN, TrangThaiThanhToan.DA_HOAN_TIEN))
                .getSingleResult() > 0;
    }

    /** Trips with expired active holds or expired orders still awaiting payment. */
    public List<String> expiredTripIds(LocalDateTime now) {
        return em.createQuery("select distinct h.chuyenXe.maChuyen from GiuCho h "
                + "left join h.donDatVe d "
                + "where (h.trangThai = :holding and h.thoiDiemHetHan <= :now) "
                + "or (d.trangThai in :pending and h.thoiDiemHetHan <= :now)", String.class)
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                .setParameter("pending", List.of(TrangThaiDon.CHO_XU_LY,
                        TrangThaiDon.DA_XAC_NHAN, TrangThaiDon.CHO_THANH_TOAN))
                .setParameter("now", now)
                .getResultList();
    }

    /** Orders represented by any hold on the trip, regardless of hold state. */
    public List<String> orderIdsForTrip(String tripId) {
        return em.createQuery("select distinct h.donDatVe.maDon from GiuCho h "
                + "where h.chuyenXe.maChuyen = :trip and h.donDatVe is not null "
                + "order by h.donDatVe.maDon", String.class)
                .setParameter("trip", tripId)
                .getResultList();
    }
}
