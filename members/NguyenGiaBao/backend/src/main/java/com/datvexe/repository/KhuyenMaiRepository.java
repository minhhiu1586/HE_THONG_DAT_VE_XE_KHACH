package com.datvexe.repository;

import com.datvexe.domain.Entities.KhuyenMai;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import org.hibernate.exception.ConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** Queries promotions using the timestamp captured when the service started the operation. */
public final class KhuyenMaiRepository extends JpaRepository<KhuyenMai> {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final char LIKE_ESCAPE = '!';
    private static final String DIEU_KIEN_TIM_KIEM = " where (:keyword is null or "
            + "(lower(k.maKhuyenMai) like :keyword escape '!' "
            + "or lower(k.tenKhuyenMai) like :keyword escape '!'))"
            + " and (:status is null or ((:status = 'HET_HAN' and k.ngayKetThuc <= :now)"
            + " or (:status = 'HET_LUOT' and k.ngayKetThuc > :now"
            + " and k.soLuongDaDung >= k.soLuong)"
            + " or (:status = 'HOAT_DONG' and k.ngayKetThuc > :now"
            + " and k.soLuongDaDung < k.soLuong)))"
            + " and (cast(:fromDate as LocalDateTime) is null or k.ngayBatDau >= :fromDate)"
            + " and (cast(:toDateExclusive as LocalDateTime) is null or k.ngayBatDau < :toDateExclusive)";

    private final LocalDateTime now;

    public KhuyenMaiRepository(EntityManager em) {
        this(em, LocalDateTime.now(BUSINESS_ZONE));
    }

    public KhuyenMaiRepository(EntityManager em, LocalDateTime now) {
        super(em, KhuyenMai.class);
        this.now = now;
    }

    @Override
    public void save(KhuyenMai khuyenMai) {
        try {
            em.persist(khuyenMai);
            em.flush();
        } catch (PersistenceException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "khuyen_mai_pkey".equals(violation.getConstraintName())) {
                    throw new AccountException(409, "CONFLICT", "Mã khuyến mãi đã tồn tại.");
                }
            }
            throw e;
        }
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

        String keyword = tuKhoa == null || tuKhoa.isEmpty()
                ? null : "%" + escapeLike(tuKhoa.toLowerCase(Locale.ROOT)) + "%";
        String status = trangThai == null ? null : trangThai.name();
        LocalDateTime fromDate = tuNgay == null ? null : tuNgay.atStartOfDay();
        LocalDateTime toDateExclusive = denNgay == null || LocalDate.MAX.equals(denNgay)
                ? null : denNgay.plusDays(1).atStartOfDay();


        TypedQuery<KhuyenMai> query = em.createQuery(
                "select k from KhuyenMai k" + DIEU_KIEN_TIM_KIEM
                        + " order by k.ngayBatDau desc, k.maKhuyenMai asc",
                KhuyenMai.class);

        query.setParameter("keyword", keyword);
        query.setParameter("status", status);
        query.setParameter("now", now);
        query.setParameter("fromDate", fromDate);
        query.setParameter("toDateExclusive", toDateExclusive);
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
        String keyword = tuKhoa == null || tuKhoa.isEmpty()
                ? null : "%" + escapeLike(tuKhoa.toLowerCase(Locale.ROOT)) + "%";
        String status = trangThai == null ? null : trangThai.name();
        LocalDateTime fromDate = tuNgay == null ? null : tuNgay.atStartOfDay();
        LocalDateTime toDateExclusive = denNgay == null || LocalDate.MAX.equals(denNgay)
                ? null : denNgay.plusDays(1).atStartOfDay();


        TypedQuery<Long> query = em.createQuery(
                "select count(k) from KhuyenMai k" + DIEU_KIEN_TIM_KIEM, Long.class);

        query.setParameter("keyword", keyword);
        query.setParameter("status", status);
        query.setParameter("now", now);
        query.setParameter("fromDate", fromDate);
        query.setParameter("toDateExclusive", toDateExclusive);

        return query.getSingleResult();
    }

    public boolean daDuocSuDung(String maKhuyenMai) {
        Long orders = em.createQuery(
                        "select count(d) from DonDatVe d where d.maKhuyenMaiDaApDung = :code "
                                + "and d.trangThai <> :draft",
                        Long.class)
                .setParameter("code", maKhuyenMai)
                .setParameter("draft", TrangThaiDon.CHO_XU_LY)
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

    private static String escapeLike(String value) {
        return value.replace(String.valueOf(LIKE_ESCAPE), "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
