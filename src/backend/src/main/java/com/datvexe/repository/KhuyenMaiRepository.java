package com.datvexe.repository;

import com.datvexe.domain.Entities.KhuyenMai;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Queries promotions using the timestamp captured when the service started the operation. */
public final class KhuyenMaiRepository extends JpaRepository<KhuyenMai> {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final char LIKE_ESCAPE = '!';

    private final LocalDateTime now;

    public KhuyenMaiRepository(EntityManager em) {
        this(em, LocalDateTime.now(BUSINESS_ZONE));
    }

    public KhuyenMaiRepository(EntityManager em, LocalDateTime now) {
        super(em, KhuyenMai.class);
        if (now == null) {
            throw new IllegalArgumentException("Thời điểm hiện tại không được null.");
        }
        this.now = now;
    }

    public List<KhuyenMai> timKiem(
            String tuKhoa,
            TrangThaiKhuyenMai trangThai,
            LocalDate tuNgay,
            LocalDate denNgay,
            int offset,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }

        QueryParts parts = filters(tuKhoa, trangThai, tuNgay, denNgay);
        TypedQuery<KhuyenMai> query = em.createQuery(
                "select k from KhuyenMai k" + parts.where()
                        + " order by k.ngayBatDau desc, k.maKhuyenMai asc",
                KhuyenMai.class);
        bind(query, parts.parameters());
        query.setFirstResult(Math.max(0, offset));
        query.setMaxResults(limit);
        return query.getResultList();
    }

    /** Đếm toàn bộ kết quả sau lọc, dùng cùng điều kiện với timKiem. */
    public long dem(
            String tuKhoa,
            TrangThaiKhuyenMai trangThai,
            LocalDate tuNgay,
            LocalDate denNgay) {
        QueryParts parts = filters(tuKhoa, trangThai, tuNgay, denNgay);
        TypedQuery<Long> query = em.createQuery(
                "select count(k) from KhuyenMai k" + parts.where(), Long.class);
        bind(query, parts.parameters());
        return query.getSingleResult();
    }

    public boolean daDuocSuDung(String maKhuyenMai) {
        if (maKhuyenMai == null) {
            return false;
        }

        Long usedQuantity = em.createQuery(
                        "select count(k) from KhuyenMai k "
                                + "where k.maKhuyenMai = :code and k.soLuongDaDung > 0",
                        Long.class)
                .setParameter("code", maKhuyenMai)
                .getSingleResult();
        if (usedQuantity > 0) {
            return true;
        }

        Long orders = em.createQuery(
                        "select count(d) from DonDatVe d where d.maKhuyenMaiDaApDung = :code",
                        Long.class)
                .setParameter("code", maKhuyenMai)
                .getSingleResult();
        return orders > 0;
    }

    /** Public catalogue contains only discounts usable at the captured business time. */
    public List<KhuyenMai> dangHieuLuc(int offset, int limit) {
        if (limit <= 0) return List.of();
        return em.createQuery("select k from KhuyenMai k where k.ngayBatDau <= :now and k.ngayKetThuc > :now " +
                "and k.soLuongDaDung < k.soLuong order by k.ngayKetThuc, k.maKhuyenMai", KhuyenMai.class)
            .setParameter("now", now).setFirstResult(Math.max(0, offset)).setMaxResults(limit).getResultList();
    }

    private QueryParts filters(
            String tuKhoa,
            TrangThaiKhuyenMai trangThai,
            LocalDate tuNgay,
            LocalDate denNgay) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> parameters = new LinkedHashMap<>();

        if (tuKhoa != null && !tuKhoa.isEmpty()) {
            where.append(" and (lower(k.maKhuyenMai) like :keyword escape '!'")
                    .append(" or lower(k.tenKhuyenMai) like :keyword escape '!')");
            parameters.put("keyword", "%" + escapeLike(tuKhoa.toLowerCase(Locale.ROOT)) + "%");
        }

        if (trangThai != null) {
            where.append(" and (");
            switch (trangThai) {
                case HET_HAN -> where.append("k.ngayKetThuc <= :now");
                case HET_LUOT -> where.append("k.ngayKetThuc > :now and k.soLuongDaDung >= k.soLuong");
                case HOAT_DONG -> where.append("k.ngayKetThuc > :now and k.soLuongDaDung < k.soLuong");
            }
            where.append(')');
            parameters.put("now", now);
        }

        if (tuNgay != null) {
            where.append(" and k.ngayBatDau >= :fromDate");
            parameters.put("fromDate", startOfBusinessDate(tuNgay));
        }
        if (denNgay != null && !LocalDate.MAX.equals(denNgay)) {
            LocalDate nextDate = denNgay.plusDays(1);
            where.append(" and k.ngayBatDau < :toDateExclusive");
            parameters.put("toDateExclusive", startOfBusinessDate(nextDate));
        }
        return new QueryParts(where.toString(), parameters);
    }

    private static LocalDateTime startOfBusinessDate(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toLocalDateTime();
    }

    private static String escapeLike(String value) {
        return value.replace(String.valueOf(LIKE_ESCAPE), "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private static void bind(TypedQuery<?> query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private record QueryParts(String where, Map<String, Object> parameters) {}
}
