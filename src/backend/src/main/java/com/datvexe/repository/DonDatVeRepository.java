package com.datvexe.repository;

import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.domain.States.TrangThaiGiuCho;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

public final class DonDatVeRepository extends JpaRepository<DonDatVe> {
    public DonDatVeRepository(EntityManager em) { super(em, DonDatVe.class); }

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
