package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.Entities.NhaXe;
import com.datvexe.domain.Entities.VeXe;
import com.datvexe.domain.States.TrangThaiVe;
import com.datvexe.dto.VeXeDto;
import com.datvexe.repository.HoanVeRepository;
import com.datvexe.repository.VeXeRepository;
import com.datvexe.security.AccessControl;
import com.datvexe.security.AccountException;
import com.datvexe.security.RequestIdentity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;

/** Tra cứu vé và quyền xem QR. */
public final class VeXeService {

    private final EntityManagerFactory factory;
    private final Clock clock;

    public VeXeService() {
        this(Jpa.factory(), Clock.system(TripSaleRules.ZONE));
    }

    public VeXeService(EntityManagerFactory factory, Clock clock) {
        this.factory = factory;
        this.clock = clock;
    }

    public VeXeDto.Response chiTiet(String maVe) {
        if (maVe == null || maVe.trim().isEmpty()) {
            throw new DomainException(400, "Mã vé là bắt buộc.");
        }

        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();

            try {
                RequestIdentity.Context identity = RequestIdentity.get();
                if (identity == null || identity.user() == null) {
                    throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
                }

                NguoiDung nguoiDung = AccessControl.authenticated(em, identity.user(), clock.instant());
                VeXeRepository repository = new VeXeRepository(em);
                VeXe ve = repository.findWithOrder(maVe);
                if (ve == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy vé.");
                }

                // Giữ thứ tự khóa: nhà xe → chuyến → đơn → vé.
                String maDon = ve.donDatVe.maDon;
                Set<String> danhSachMaChuyen = repository.tripIdsForOrderIncludingHolds(maDon);
                if (danhSachMaChuyen.size() != 1) {
                    throw new AccountException(409, "INVALID_STATE_TRANSITION", "Đơn phải thuộc đúng một chuyến.");
                }

                String maChuyen = danhSachMaChuyen.iterator().next();
                ChuyenXe chuyen = repository.findTrip(maChuyen);
                if (chuyen.tuyenXe.nhaXe != null) {
                    AccessControl.resource(em, NhaXe.class, chuyen.tuyenXe.nhaXe.maNhaXe);
                }
                AccessControl.resource(em, ChuyenXe.class, maChuyen);
                AccessControl.resource(em, DonDatVe.class, maDon);
                AccessControl.ticketAccess(em, nguoiDung, maVe);
                ve = repository.findWithOrder(maVe);

                // Đếm cả vé đã hoàn/hủy để giữ đúng giá gốc lịch sử.
                long soVeBanDau = repository.countForOrder(maDon);
                BigDecimal giaGoc = ve.donDatVe.tongTien.divide(
                        BigDecimal.valueOf(soVeBanDau), 0, RoundingMode.UNNECESSARY);
                BigDecimal thanhTien = ve.giaVe == null ? BigDecimal.ZERO : ve.giaVe;
                BigDecimal tienGiam = giaGoc.subtract(thanhTien).max(BigDecimal.ZERO);

                boolean dangHoan = new HoanVeRepository(em).ticketBlocked(maVe);
                boolean veConHieuLuc = ve.trangThai == TrangThaiVe.DA_XUAT_VE
                        || ve.trangThai == TrangThaiVe.DA_LEN_XE;
                String qrToken = null;
                if (!dangHoan && veConHieuLuc) {
                    qrToken = ve.maQR;
                }

                OffsetDateTime thoiDiemPhatHanh = null;
                if (ve.ngayXuatVe != null) {
                    thoiDiemPhatHanh = ve.ngayXuatVe.atZone(TripSaleRules.ZONE).toOffsetDateTime();
                }

                String giaGocText = giaGoc.toPlainString();
                String tienGiamText = tienGiam.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
                String thanhTienText = thanhTien.setScale(0, RoundingMode.UNNECESSARY).toPlainString();

                VeXeDto.Response result = new VeXeDto.Response(
                        ve.maVe, ve.maTraCuu, maDon, ve.chuyenXe.maChuyen, ve.ghe.maGhe,
                        ve.hoTenKhachHang, ve.soDienThoaiKhachHang, ve.cccdHanhKhach,
                        giaGocText, tienGiamText, thanhTienText, "VND",
                        qrToken, thoiDiemPhatHanh, ve.trangThai, dangHoan);

                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }
}
