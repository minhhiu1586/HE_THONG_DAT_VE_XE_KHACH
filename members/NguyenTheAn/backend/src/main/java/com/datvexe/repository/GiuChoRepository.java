package com.datvexe.repository;

import com.datvexe.domain.Entities.GiuCho;
import com.datvexe.domain.States.TrangThaiGiuCho;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Repository truy xuất và thao tác dữ liệu Giữ chỗ (GiuCho).
 * Quản lý trạng thái khóa ghế tạm thời (15 phút) trong quá trình đặt vé.
 * Thiết kế theo chuẩn DAO của Cinema-main với JPA EntityManager.
 */
public class GiuChoRepository {

    private final EntityManager em;

    public GiuChoRepository() {
        this.em = null;
    }

    public GiuChoRepository(EntityManager em) {
        this.em = em;
    }

    /**
     * Tìm danh sách mã ghế đang bị giữ hợp lệ trên một chuyến xe cụ thể.
     * Ghế đang bị giữ nếu trạng thái là DANG_GIU và thời điểm hết hạn > thời điểm hiện tại.
     *
     * @param em EntityManager quản lý giao dịch
     * @param maChuyen Mã chuyến xe
     * @param danhSachGhe Danh sách mã ghế cần kiểm tra
     * @param hienTai Thời điểm kiểm tra hiện tại
     * @return Danh sách các mã ghế đang bị khóa/giữ chỗ
     */
    public List<String> timCacGheDangBiGiu(
            EntityManager em,
            String maChuyen,
            List<String> danhSachGhe,
            LocalDateTime hienTai
    ) {
        if (danhSachGhe == null || danhSachGhe.isEmpty()) {
            return List.of();
        }

        String jpql = "SELECT g.ghe.maGhe FROM GiuCho g "
                + "WHERE g.chuyenXe.maChuyen = :maChuyen "
                + "AND g.ghe.maGhe IN :danhSachGhe "
                + "AND g.trangThai = :dangGiu "
                + "AND g.thoiDiemHetHan > :hienTai";

        return em.createQuery(jpql, String.class)
                .setParameter("maChuyen", maChuyen)
                .setParameter("danhSachGhe", danhSachGhe)
                .setParameter("dangGiu", TrangThaiGiuCho.DANG_GIU)
                .setParameter("hienTai", hienTai)
                .getResultList();
    }

    /**
     * Lưu thực thể giữ chỗ xuống CSDL.
     *
     * @param em EntityManager quản lý giao dịch
     * @param giuCho Thực thể GiuCho cần lưu
     */
    public void save(EntityManager em, GiuCho giuCho) {
        em.persist(giuCho);
    }

    /**
     * Lấy toàn bộ các bản ghi giữ chỗ liên kết với đơn đặt vé (bất kể trạng thái).
     *
     * @param maDon Mã đơn đặt vé
     * @return Danh sách GiuCho thuộc đơn
     */
    public List<GiuCho> forOrder(String maDon) {
        String jpql = "SELECT g FROM GiuCho g "
                + "WHERE g.donDatVe.maDon = :order";

        return entityManager().createQuery(jpql, GiuCho.class)
                .setParameter("order", maDon)
                .getResultList();
    }

    /**
     * Lấy các bản ghi giữ chỗ còn hiệu lực cho đơn đặt vé tại thời điểm hiện tại.
     *
     * @param maDon Mã đơn đặt vé
     * @return Danh sách GiuCho đang giữ
     */
    public List<GiuCho> activeForOrder(String maDon) {
        return activeForOrder(maDon, LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    /**
     * Lấy các bản ghi giữ chỗ còn hiệu lực cho đơn đặt vé tại một thời điểm xác định.
     *
     * @param maDon Mã đơn đặt vé
     * @param now Thời điểm mốc kiểm tra
     * @return Danh sách GiuCho đang giữ
     */
    public List<GiuCho> activeForOrder(String maDon, LocalDateTime now) {
        String jpql = "SELECT g FROM GiuCho g "
                + "WHERE g.donDatVe.maDon = :order "
                + "AND g.trangThai = :holding "
                + "AND g.thoiDiemHetHan > :now";

        return entityManager().createQuery(jpql, GiuCho.class)
                .setParameter("order", maDon)
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                .setParameter("now", now)
                .getResultList();
    }

    /**
     * Lấy tất cả bản ghi giữ chỗ của một chuyến xe.
     *
     * @param maChuyen Mã chuyến xe
     * @return Danh sách GiuCho thuộc chuyến
     */
    public List<GiuCho> forTrip(String maChuyen) {
        String jpql = "SELECT g FROM GiuCho g "
                + "WHERE g.chuyenXe.maChuyen = :trip";

        return entityManager().createQuery(jpql, GiuCho.class)
                .setParameter("trip", maChuyen)
                .getResultList();
    }

    private EntityManager entityManager() {
        if (em == null) {
            throw new IllegalStateException("EntityManager is required for this repository method.");
        }
        return em;
    }
}

