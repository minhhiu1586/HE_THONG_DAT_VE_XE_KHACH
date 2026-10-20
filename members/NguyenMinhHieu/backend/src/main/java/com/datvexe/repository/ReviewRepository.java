package com.datvexe.repository;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.*;

public final class ReviewRepository {
    private final EntityManager em;

    public ReviewRepository(EntityManager em) {
        this.em = em;
    }

    public DanhGia forAccountTrip(String account, String trip) {
        var rows =
                em.createQuery(
                                "select d from DanhGia d where d.nguoiDung.maNguoiDung=:account and"
                                    + " d.chuyenXe.maChuyen=:trip",
                                DanhGia.class)
                        .setParameter("account", account)
                        .setParameter("trip", trip)
                        .getResultList();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasEligibleTicket(String account, String trip) {
        return em.createQuery(
                                "select count(v) from VeXe v where v.chuyenXe.maChuyen=:trip and"
                                    + " v.donDatVe.nguoiDat.maNguoiDung=:account and"
                                    + " v.donDatVe.trangThai=:paid and v.trangThai in :tickets and"
                                    + " exists(select p.maGiaoDich from ThanhToan p where"
                                    + " p.donDatVe=v.donDatVe and p.trangThai in :payments)",
                                Long.class)
                        .setParameter("account", account)
                        .setParameter("trip", trip)
                        .setParameter("paid", TrangThaiDon.DA_THANH_TOAN)
                        .setParameter(
                                "tickets", List.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE))
                        .setParameter(
                                "payments",
                                List.of(
                                        TrangThaiThanhToan.THANH_CONG,
                                        TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN))
                        .getSingleResult()
                > 0;
    }

    public record ReviewPage(List<DanhGia> items, long total) {}

    public Object[] summary(ChuyenXe trip) {
        return em.createQuery(
                        "select count(d),avg(d.soSao) from DanhGia d where d.chuyenXe=:trip and"
                            + " d.trangThai=:approved and d.daXoa=false",
                        Object[].class)
                .setParameter("trip", trip)
                .setParameter("approved", TrangThaiDanhGia.DA_DUYET)
                .getSingleResult();
    }

    public List<DanhGia> publicPage(ChuyenXe trip, int page, int size) {
        return em.createQuery(
                        "select d from DanhGia d where d.chuyenXe=:trip and d.trangThai=:approved"
                            + " and d.daXoa=false order by d.ngayDanhGia desc,d.maDanhGia",
                        DanhGia.class)
                .setParameter("trip", trip)
                .setParameter("approved", TrangThaiDanhGia.DA_DUYET)
                .setFirstResult((page - 1) * size)
                .setMaxResults(size)
                .getResultList();
    }

    public ReviewPage management(NhaXe owner, String tripId, String state, int page, int size) {
        String where = " from DanhGia d where 1=1";
        Map<String, Object> params = new LinkedHashMap<>();
        if (owner != null) {
            where += " and d.chuyenXe.tuyenXe.nhaXe=:owner";
            params.put("owner", owner);
        }
        if (tripId != null) {
            where += " and d.chuyenXe.maChuyen=:trip";
            params.put("trip", tripId);
        }
        if ("DA_XOA".equals(state)) where += " and d.daXoa=true";
        else {
            where += " and d.daXoa=false";
            if (state != null && !state.isBlank()) {
                where += " and d.trangThai=:state";
                params.put("state", TrangThaiDanhGia.valueOf(state));
            }
        }
        var count = em.createQuery("select count(d)" + where, Long.class);
        var query =
                em.createQuery(
                        "select d" + where + " order by d.ngayDanhGia desc,d.maDanhGia",
                        DanhGia.class);
        params.forEach(
                (key, value) -> {
                    count.setParameter(key, value);
                    query.setParameter(key, value);
                });
        var items = query.setFirstResult((page - 1) * size).setMaxResults(size).getResultList();
        return new ReviewPage(items, count.getSingleResult());
    }

    public List<Object[]> history(String id) {
        return em.createNativeQuery(
                        "select"
                            + " ma_nguoi_thao_tac,thao_tac,trang_thai_cu,trang_thai_moi,ly_do,created_at"
                            + " from review_history where ma_danh_gia=:id order by created_at,id")
                .setParameter("id", id)
                .getResultList();
    }

    public void audit(
            DanhGia review,
            NguoiDung actor,
            String action,
            TrangThaiDanhGia before,
            Integer stars,
            String text,
            String reason,
            Instant now) {
        em.flush();
        em.createNativeQuery(
                        "insert into"
                            + " review_history(id,ma_danh_gia,ma_nguoi_thao_tac,thao_tac,trang_thai_cu,trang_thai_moi,so_sao_cu,so_sao_moi,binh_luan_cu,binh_luan_moi,ly_do,created_at)"
                            + " values"
                            + " (:id,:review,:actor,:action,:before,:after,:oldStars,:stars,:oldText,:text,:reason,:now)")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("review", review.maDanhGia)
                .setParameter("actor", actor.maNguoiDung)
                .setParameter("action", action)
                .setParameter("before", before == null ? null : before.name())
                .setParameter("after", review.daXoa ? "DA_XOA" : review.trangThai.name())
                .setParameter("oldStars", stars)
                .setParameter("stars", review.soSao)
                .setParameter("oldText", text)
                .setParameter("text", review.binhLuan)
                .setParameter("reason", reason)
                .setParameter("now", now)
                .executeUpdate();
    }
}
