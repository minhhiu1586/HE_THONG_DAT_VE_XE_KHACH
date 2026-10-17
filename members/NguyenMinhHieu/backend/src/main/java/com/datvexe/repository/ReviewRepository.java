package com.datvexe.repository;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import jakarta.persistence.EntityManager;
import java.util.List;

/** T22 queries reuse the booking owner, issued tickets and completed trip. */
public final class ReviewRepository {
    private final EntityManager em;
    public ReviewRepository(EntityManager em){this.em=em;}
    public DanhGia forAccountTrip(String account,String trip) {
        var rows=em.createQuery("select d from DanhGia d where d.nguoiDung.maNguoiDung=:account and d.chuyenXe.maChuyen=:trip",DanhGia.class)
                .setParameter("account",account).setParameter("trip",trip).getResultList();
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean hasEligibleTicket(String account,String trip) {
        return em.createQuery("select count(v) from VeXe v where v.chuyenXe.maChuyen=:trip and v.donDatVe.nguoiDat.maNguoiDung=:account "
                +"and v.donDatVe.trangThai=:paid and v.trangThai in :tickets "
                +"and exists(select p.maGiaoDich from ThanhToan p where p.donDatVe=v.donDatVe and p.trangThai in :payments)",Long.class)
                .setParameter("account",account).setParameter("trip",trip).setParameter("paid",TrangThaiDon.DA_THANH_TOAN)
                .setParameter("tickets",List.of(TrangThaiVe.DA_XUAT_VE,TrangThaiVe.DA_LEN_XE))
                .setParameter("payments",List.of(TrangThaiThanhToan.THANH_CONG,TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN)).getSingleResult()>0;
    }
}
