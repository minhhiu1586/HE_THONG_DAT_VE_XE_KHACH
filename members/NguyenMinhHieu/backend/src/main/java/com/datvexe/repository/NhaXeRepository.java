package com.datvexe.repository;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;

import jakarta.persistence.*;

import java.time.*;
import java.util.*;

public final class NhaXeRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final EntityManager em;

    public NhaXeRepository(EntityManager em) {
        this.em = em;
    }

    public record SearchResult(List<NhaXe> items, long total) {}

    public record ImpactCounts(
            long chuyenSapKhoiHanh,
            long chuyenDangChay,
            long giuChoDangGiu,
            long veDaPhatHanh,
            long veCoThanhToanThanhCong) {}

    public NhaXe find(String id, boolean lock) {
        return em.find(NhaXe.class, id, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
    }

    public SearchResult search(String term, TrangThaiNhaXe state, int page, int size) {
        String where =
                " where 1 = 1"
                        + (state == null ? "" : " and x.trangThai = :state")
                        + (term.isEmpty()
                                ? ""
                                : " and (lower(x.maNhaXe) like :pattern or lower(x.tenNhaXe) like"
                                      + " :pattern)");
        TypedQuery<Long> countQuery =
                em.createQuery("select count(x) from NhaXe x" + where, Long.class);
        parameters(countQuery, term, state);
        long total = countQuery.getSingleResult();
        long offset = ((long) page - 1) * size;
        if (offset >= total) return new SearchResult(List.of(), total);
        TypedQuery<NhaXe> itemQuery =
                em.createQuery(
                        "select x from NhaXe x" + where + " order by x.maNhaXe", NhaXe.class);
        parameters(itemQuery, term, state);
        return new SearchResult(
                itemQuery
                        .setFirstResult(Math.toIntExact(offset))
                        .setMaxResults(size)
                        .getResultList(),
                total);
    }

    private void parameters(TypedQuery<?> query, String term, TrangThaiNhaXe state) {
        if (state != null) query.setParameter("state", state);
        if (!term.isEmpty()) query.setParameter("pattern", "%" + term + "%");
    }

    public List<NguoiDung> managers(NhaXe operator) {
        return em.createQuery(
                        "select n from NguoiDung n where n.nhaXe = :operator and n.loaiTaiKhoan ="
                            + " :role order by n.hoTen",
                        NguoiDung.class)
                .setParameter("operator", operator)
                .setParameter("role", LoaiTaiKhoan.NHA_XE)
                .getResultList();
    }

    public boolean hasReferences(NhaXe operator) {
        for (String entity : List.of("NguoiDung", "NhanVien", "Xe", "TuyenXe")) {
            long count =
                    em.createQuery(
                                    "select count(item) from "
                                            + entity
                                            + " item where item.nhaXe = :operator",
                                    Long.class)
                            .setParameter("operator", operator)
                            .getSingleResult();
            if (count > 0) return true;
        }
        return false;
    }

    private long count(String query, NhaXe operator, String parameter, Object value) {
        TypedQuery<Long> statement =
                em.createQuery(query, Long.class).setParameter("operator", operator);
        if (parameter != null) statement.setParameter(parameter, value);
        return statement.getSingleResult();
    }

    public ImpactCounts impact(NhaXe operator) {
        long upcoming =
                em.createQuery(
                                "select count(c) from ChuyenXe c where c.tuyenXe.nhaXe = :operator"
                                    + " and c.trangThai in (:draft,:legacy,:sale) and"
                                    + " c.ngayKhoiHanh >= :today",
                                Long.class)
                        .setParameter("operator", operator)
                        .setParameter("draft", TrangThaiChuyen.NHAP)
                        .setParameter("legacy", TrangThaiChuyen.CHUA_KHOI_HANH)
                        .setParameter("sale", TrangThaiChuyen.DANG_MO_BAN)
                        .setParameter("today", LocalDate.now(BUSINESS_ZONE))
                        .getSingleResult();
        long running =
                count(
                        "select count(c) from ChuyenXe c where c.tuyenXe.nhaXe = :operator and"
                            + " c.trangThai = :state",
                        operator,
                        "state",
                        TrangThaiChuyen.DANG_CHAY);
        long holds =
                em.createQuery(
                                "select count(g) from GiuCho g where g.chuyenXe.tuyenXe.nhaXe ="
                                    + " :operator and g.trangThai = :state and g.thoiDiemHetHan >"
                                    + " :now",
                                Long.class)
                        .setParameter("operator", operator)
                        .setParameter("state", TrangThaiGiuCho.DANG_GIU)
                        .setParameter("now", LocalDateTime.now(BUSINESS_ZONE))
                        .getSingleResult();
        long tickets =
                count(
                        "select count(v) from VeXe v where v.chuyenXe.tuyenXe.nhaXe = :operator and"
                            + " v.trangThai = :state",
                        operator,
                        "state",
                        TrangThaiVe.DA_XUAT_VE);
        long paid =
                count(
                        "select count(distinct v) from VeXe v join v.donDatVe d join"
                            + " d.danhSachThanhToan t where v.chuyenXe.tuyenXe.nhaXe = :operator"
                            + " and t.trangThai in :states",
                        operator,
                        "states",
                        java.util.List.of(
                                TrangThaiThanhToan.THANH_CONG,
                                TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN,
                                TrangThaiThanhToan.DA_HOAN_TIEN));
        return new ImpactCounts(upcoming, running, holds, tickets, paid);
    }
}
