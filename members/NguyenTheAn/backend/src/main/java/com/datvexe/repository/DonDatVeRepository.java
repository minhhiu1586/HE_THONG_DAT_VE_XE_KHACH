package com.datvexe.repository;

import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.domain.States.TrangThaiGiuCho;
import com.datvexe.domain.States.TrangThaiThanhToan;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất và thao tác dữ liệu Đơn đặt vé (DonDatVe).
 * Thiết kế theo mẫu DAO chuẩn của Cinema-main với JPA EntityManager.
 */
public final class DonDatVeRepository extends JpaRepository<DonDatVe> {

    public DonDatVeRepository(EntityManager em) {
        super(em, DonDatVe.class);
    }

    /**
     * Tìm đơn đặt vé theo mã đơn (trả về Optional).
     *
     * @param maDon Mã đơn đặt vé
     * @return Optional chứa DonDatVe nếu tìm thấy
     */
    public Optional<DonDatVe> findById(String maDon) {
        if (maDon == null || maDon.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(find(maDon));
    }

    /**
     * Đếm số lượt sử dụng khuyến mãi đang được giữ chỗ (cho đơn đã chốt giá chưa hết hạn).
     * Đơn đã hết hạn thời gian giữ ghế sẽ bị loại trừ trực tiếp mà không cần đợi scheduler.
     *
     * @param promotionId Mã khuyến mãi cần kiểm tra
     * @param excludedOrderId Mã đơn hiện tại được loại trừ khỏi tính toán
     * @param now Thời điểm kiểm tra hiện tại
     * @return Số lượng đơn đang bảo lưu mã khuyến mãi này
     */
    public long reservedPromotionUses(String promotionId, String excludedOrderId, LocalDateTime now) {
        String jpql = "SELECT COUNT(d) FROM DonDatVe d "
                + "WHERE d.maKhuyenMaiDaApDung = :promotion "
                + "AND d.maDon <> :excluded "
                + "AND d.trangThai = :frozen "
                + "AND EXISTS ("
                + "    SELECT h.maGiuCho FROM GiuCho h "
                + "    WHERE h.donDatVe = d "
                + "    AND h.trangThai = :holding "
                + "    AND h.thoiDiemHetHan > :now"
                + ") "
                + "AND NOT EXISTS ("
                + "    SELECT h.maGiuCho FROM GiuCho h "
                + "    WHERE h.donDatVe = d "
                + "    AND h.trangThai = :holding "
                + "    AND h.thoiDiemHetHan <= :now"
                + ")";

        return em.createQuery(jpql, Long.class)
                .setParameter("promotion", promotionId)
                .setParameter("excluded", excludedOrderId)
                .setParameter("frozen", TrangThaiDon.CHO_THANH_TOAN)
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                .setParameter("now", now)
                .getSingleResult();
    }

    /**
     * Kiểm tra xem đơn đặt vé có giao dịch thanh toán đang xử lý hoặc đã thanh toán hay không.
     * Dùng để ngăn chặn việc hủy đơn hoặc sửa đổi khi giao dịch tiền tệ đang diễn ra.
     *
     * @param orderId Mã đơn đặt vé
     * @return true nếu có giao dịch chặn thay đổi
     */
    public boolean hasBlockingPayment(String orderId) {
        String jpql = "SELECT COUNT(t) FROM ThanhToan t "
                + "WHERE t.donDatVe.maDon = :order "
                + "AND t.trangThai IN :states";

        List<TrangThaiThanhToan> blockingStates = List.of(
                TrangThaiThanhToan.CHO_XU_LY,
                TrangThaiThanhToan.THANH_CONG,
                TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN,
                TrangThaiThanhToan.DA_HOAN_TIEN
        );

        return em.createQuery(jpql, Long.class)
                .setParameter("order", orderId)
                .setParameter("states", blockingStates)
                .getSingleResult() > 0;
    }

    /**
     * Lấy danh sách các mã chuyến xe có chỗ giữ đã quá hạn hoặc đơn hết hạn chờ thanh toán.
     *
     * @param now Thời điểm kiểm tra hiện tại
     * @return Danh sách mã chuyến xe cần giải phóng ghế hết hạn
     */
    public List<String> expiredTripIds(LocalDateTime now) {
        String jpql = "SELECT DISTINCT h.chuyenXe.maChuyen FROM GiuCho h "
                + "LEFT JOIN h.donDatVe d "
                + "WHERE (h.trangThai = :holding AND h.thoiDiemHetHan <= :now) "
                + "OR (d.trangThai IN :pending AND h.thoiDiemHetHan <= :now)";

        List<TrangThaiDon> pendingStates = List.of(
                TrangThaiDon.CHO_XU_LY,
                TrangThaiDon.DA_XAC_NHAN,
                TrangThaiDon.CHO_THANH_TOAN
        );

        return em.createQuery(jpql, String.class)
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                .setParameter("pending", pendingStates)
                .setParameter("now", now)
                .getResultList();
    }

    /**
     * Lấy danh sách mã các đơn đặt vé liên kết với chuyến xe thông qua các chỗ giữ.
     *
     * @param tripId Mã chuyến xe
     * @return Danh sách mã đơn đã sắp xếp
     */
    public List<String> orderIdsForTrip(String tripId) {
        String jpql = "SELECT DISTINCT h.donDatVe.maDon FROM GiuCho h "
                + "WHERE h.chuyenXe.maChuyen = :trip "
                + "AND h.donDatVe IS NOT NULL "
                + "ORDER BY h.donDatVe.maDon";

        return em.createQuery(jpql, String.class)
                .setParameter("trip", tripId)
                .getResultList();
    }
}

