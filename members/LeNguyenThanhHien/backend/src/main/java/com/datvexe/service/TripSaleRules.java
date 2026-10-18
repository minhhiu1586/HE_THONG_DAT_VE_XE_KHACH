package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.security.AccessControl;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Kiểm tra điều kiện mở bán trong transaction đang giữ khóa nhà xe và chuyến. */
public final class TripSaleRules {
    private TripSaleRules() {}

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record Check(String maDieuKien, String tenDieuKien, boolean dat, String lyDo) {}

    public record Result(
            boolean duDieuKien,
            OffsetDateTime ngungNhanDatVeLuc,
            OffsetDateTime serverTime,
            List<Check> dieuKien) {}

    public static long cutoffMinutes() {
        long minutes =
                Long.parseLong(
                        System.getProperty(
                                "trip.booking.cutoff.minutes",
                                System.getenv().getOrDefault("TRIP_BOOKING_CUTOFF_MINUTES", "60")));
        if (minutes < 0) {
            throw new IllegalStateException("TRIP_BOOKING_CUTOFF_MINUTES must be nonnegative");
        }
        return minutes;
    }

    public static boolean draft(ChuyenXe trip) {
        return trip.trangThai == TrangThaiChuyen.NHAP
                || trip.trangThai == TrangThaiChuyen.CHUA_KHOI_HANH;
    }

    public static boolean scheduled(ChuyenXe trip) {
        return draft(trip) || trip.trangThai == TrangThaiChuyen.DANG_MO_BAN;
    }

    public static LocalDateTime start(ChuyenXe trip) {
        return LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);
    }

    public static LocalDateTime cutoff(ChuyenXe trip) {
        return start(trip).minusMinutes(cutoffMinutes());
    }

    public static boolean validPrice(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            return false;
        }
        BigDecimal normalized = price.stripTrailingZeros();
        return normalized.scale() <= 0 && normalized.precision() - normalized.scale() <= 12;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new DomainException(409, message);
        }
    }

    // Đọc lại sau khi chờ khóa để tránh kiểm tra tuyến/bến từ dữ liệu cũ.
    private static void reloadRouteAndStations(EntityManager em, ChuyenXe trip) {
        if (trip.tuyenXe.nhaXe != null) {
            AccessControl.resource(em, NhaXe.class, trip.tuyenXe.nhaXe.maNhaXe);
        }
        trip.tuyenXe = AccessControl.resource(em, TuyenXe.class, trip.tuyenXe.maTuyen);
        TuyenXe route = trip.tuyenXe;
        Map<String, BenXe> stations = new TreeMap<>();
        if (route.tramKhoiHanh != null) {
            stations.put(route.tramKhoiHanh.maBenXe, route.tramKhoiHanh);
        }
        if (route.tramDen != null) {
            stations.put(route.tramDen.maBenXe, route.tramDen);
        }
        // Khóa bến theo cùng thứ tự mã để các giao dịch không khóa ngược nhau.
        for (String stationId : stations.keySet()) {
            BenXe station = AccessControl.resource(em, BenXe.class, stationId);
            stations.put(stationId, station);
        }
        if (route.tramKhoiHanh != null) {
            route.tramKhoiHanh = stations.get(route.tramKhoiHanh.maBenXe);
        }
        if (route.tramDen != null) {
            route.tramDen = stations.get(route.tramDen.maBenXe);
        }
    }

    public static Result inspect(EntityManager em, ChuyenXe trip, LocalDateTime now) {
        reloadRouteAndStations(em, trip);
        List<Check> checks = new ArrayList<>();
        try {
            require(scheduled(trip), "Chuyến đã chạy, hoàn tất hoặc hủy; không thể mở bán.");
            checks.add(new Check("TRANG_THAI", "Trạng thái chuyến", true, null));
        } catch (DomainException error) {
            checks.add(new Check("TRANG_THAI", "Trạng thái chuyến", false, error.getMessage()));
        }
        try {
            require(now.isBefore(cutoff(trip)), "Đã đến thời điểm ngừng nhận đặt vé.");
            checks.add(new Check("HAN_DAT_VE", "Thời hạn nhận đặt vé", true, null));
        } catch (DomainException error) {
            checks.add(new Check("HAN_DAT_VE", "Thời hạn nhận đặt vé", false, error.getMessage()));
        }
        try {

            require(
                    trip.gioDenDuKien != null && trip.gioDenDuKien.isAfter(start(trip)),
                    "Giờ đến phải sau khởi hành.");
            require(
                    validPrice(trip.tuyenXe.giaCoBan),
                    "Giá vé phải là số nguyên VND không âm, tối đa 12 chữ số.");

            checks.add(new Check("LICH_TRINH", "Ngày giờ và giá vé", true, null));
        } catch (DomainException error) {
            checks.add(new Check("LICH_TRINH", "Ngày giờ và giá vé", false, error.getMessage()));
        }
        try {

            TuyenXe route = trip.tuyenXe;
            require(
                    route.trangThai == TrangThaiTuyenXe.DANG_KHAI_THAC,
                    "Tuyến chưa được khai thác.");
            require(
                    route.tramKhoiHanh != null
                            && route.tramDen != null
                            && !route.tramKhoiHanh.maBenXe.equals(route.tramDen.maBenXe),
                    "Bến đầu/cuối không hợp lệ.");
            require(
                    route.tramKhoiHanh.trangThai == TrangThaiBenXe.HOAT_DONG
                            && route.tramDen.trangThai == TrangThaiBenXe.HOAT_DONG,
                    "Bến khởi hành hoặc bến đến không hoạt động.");

            checks.add(new Check("TUYEN_BEN", "Tuyến và bến xe", true, null));
        } catch (DomainException error) {
            checks.add(new Check("TUYEN_BEN", "Tuyến và bến xe", false, error.getMessage()));
        }
        try {

            require(trip.tuyenXe.nhaXe != null, "Chuyến chưa có nhà xe vận hành.");
            AccessControl.businessAllowed(em, trip.tuyenXe.nhaXe);

            checks.add(new Check("NHA_XE", "Nhà xe vận hành", true, null));
        } catch (DomainException error) {
            checks.add(new Check("NHA_XE", "Nhà xe vận hành", false, error.getMessage()));
        }
        try {

            require(trip.xe != null, "Chuyến chưa được gán xe.");
            VehicleRules.ready(em, trip.xe.maXe, trip);

            checks.add(new Check("XE_DANG_KIEM", "Xe, đăng kiểm và sơ đồ ghế", true, null));
        } catch (DomainException error) {
            checks.add(
                    new Check(
                            "XE_DANG_KIEM",
                            "Xe, đăng kiểm và sơ đồ ghế",
                            false,
                            error.getMessage()));
        }
        try {

            require(trip.xe != null, "Chuyến chưa được gán xe.");
            AssignmentRules.validateVehicleSchedule(em, trip, trip.xe.maXe);

            checks.add(new Check("LICH_XE", "Lịch vận hành và thời gian chuẩn bị xe", true, null));
        } catch (DomainException error) {
            checks.add(
                    new Check(
                            "LICH_XE",
                            "Lịch vận hành và thời gian chuẩn bị xe",
                            false,
                            error.getMessage()));
        }
        try {
            AssignmentRules.readyForOperation(em, trip);
            checks.add(new Check("NHAN_SU", "Phân công, bằng lái và lịch nhân viên", true, null));
        } catch (DomainException error) {
            checks.add(
                    new Check(
                            "NHAN_SU",
                            "Phân công, bằng lái và lịch nhân viên",
                            false,
                            error.getMessage()));
        }
        boolean allPassed = true;
        for (Check condition : checks) {
            if (!condition.dat()) {
                allPassed = false;
                break;
            }
        }
        return new Result(
                allPassed,
                cutoff(trip).atZone(ZONE).toOffsetDateTime(),
                now.atZone(ZONE).toOffsetDateTime(),
                List.copyOf(checks));
    }

    public static void ensureOpenable(EntityManager em, ChuyenXe trip, LocalDateTime now) {
        Result result = inspect(em, trip, now);
        if (result.duDieuKien()) {
            return;
        }
        for (Check condition : result.dieuKien()) {
            if (!condition.dat()) {
                throw new DomainException(409, condition.lyDo());
            }
        }
        throw new DomainException(409, "Chuyến chưa đủ điều kiện mở bán.");
    }

    /** Giữ chỗ/đặt vé gọi trong transaction ghi và dùng thời gian máy chủ. */
    public static void ensureSellable(EntityManager em, ChuyenXe trip, LocalDateTime now) {
        require(
                trip.trangThai == TrangThaiChuyen.DANG_MO_BAN,
                "Chuyến chưa mở bán hoặc đã kết thúc nhận đặt vé.");
        ensureOpenable(em, trip, now);
    }
}
