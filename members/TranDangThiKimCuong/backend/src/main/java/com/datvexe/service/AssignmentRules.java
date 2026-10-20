package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.repository.PhanCongChuyenXeRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import org.hibernate.Hibernate;
import jakarta.persistence.LockModeType;

/** T11 checks on the approved model. T12 calls this inside its transaction. */
public final class AssignmentRules {
    private static final Duration DRIVING_PLAN_THRESHOLD = Duration.ofHours(4);

    private AssignmentRules() {}

    public static long vehicleTurnaroundMinutes() {
        String defaultMinutes = System.getenv().getOrDefault("VEHICLE_TURNAROUND_MINUTES", "30");
        String configuredMinutes = System.getProperty("vehicle.turnaround.minutes", defaultMinutes);
        long minutes = Long.parseLong(configuredMinutes);
        if (minutes < 0) {
            throw new IllegalStateException("vehicle.turnaround.minutes must be nonnegative");
        }
        return minutes;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new DomainException(409, message);
        }
    }

    public static void validate(EntityManager em, ChuyenXe trip, NhanVien employee, VaiTroChuyen role, String excludedId) {
        trip = (ChuyenXe) Hibernate.unproxy(trip);
        employee = (NhanVien) Hibernate.unproxy(employee);
        trip.tuyenXe = (TuyenXe) Hibernate.unproxy(trip.tuyenXe);
        if (role == null) {
            throw new DomainException(400, "Thiếu vai trò.");
        }
        require(role != VaiTroChuyen.KIEM_VE, "Vai trò phân công không còn được hỗ trợ.");
        require(employee.trangThai == TrangThaiNhanVien.DANG_LAM_VIEC, "Nhân viên không đang làm việc.");
        LoaiNhanVien requiredType = switch (role) {
            case PHU_XE -> LoaiNhanVien.PHU_XE;
            case KIEM_VE -> throw new DomainException(409, "Vai trò phân công không còn được hỗ trợ.");
            default -> LoaiNhanVien.TAI_XE;
        };
        require(employee.loaiNhanVien == requiredType,
            "Loại nhân viên không phù hợp vai trò.");
        require(trip.tuyenXe.nhaXe != null && employee.nhaXe != null && employee.nhaXe.maNhaXe.equals(trip.tuyenXe.nhaXe.maNhaXe),
            "Nhân viên không thuộc nhà xe của tuyến.");
        if (employee.loaiNhanVien == LoaiNhanVien.TAI_XE) {
            require(employee.soBangLai != null && !employee.soBangLai.isBlank()
                    && employee.ngayHetHanBangLai != null && !employee.ngayHetHanBangLai.isBefore(trip.gioDenDuKien.toLocalDate()),
                "Bằng lái thiếu hoặc hết hạn trước khi kết thúc chuyến.");
        }
        LocalDateTime start = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);
        ChuyenXe previous = null, next = null;
        for (PhanCongChuyenXe other : new PhanCongChuyenXeRepository(em).list()) {
            other.chuyenXe = (ChuyenXe) Hibernate.unproxy(other.chuyenXe);
            other.nhanVien = (NhanVien) Hibernate.unproxy(other.nhanVien);
            if (other.maPhanCong.equals(excludedId)
                || other.chuyenXe.trangThai == TrangThaiChuyen.DA_HUY
                || other.chuyenXe.trangThai == TrangThaiChuyen.HOAN_TAT) {
                continue;
            }
            if (other.chuyenXe.maChuyen.equals(trip.maChuyen)) {
                require(!other.nhanVien.maNhanVien.equals(employee.maNhanVien), "Nhân viên đã có phân công trên chuyến.");
                if (role == VaiTroChuyen.TAI_XE_CHINH) {
                    require(other.vaiTro != VaiTroChuyen.TAI_XE_CHINH, "Chuyến đã có tài xế chính.");
                }
            }
            if (!other.nhanVien.maNhanVien.equals(employee.maNhanVien)) {
                continue;
            }
            ChuyenXe existing = other.chuyenXe;
            LocalDateTime otherStart = LocalDateTime.of(existing.ngayKhoiHanh, existing.gioKhoiHanh);
            boolean overlaps = start.isBefore(existing.gioDenDuKien)
                && otherStart.isBefore(trip.gioDenDuKien);
            require(!overlaps,
                "Nhân viên đã có chuyến trùng thời gian.");
            boolean nearestPrevious = !start.isBefore(existing.gioDenDuKien)
                && (previous == null || existing.gioDenDuKien.isAfter(previous.gioDenDuKien));
            boolean nearestNext = !otherStart.isBefore(trip.gioDenDuKien)
                && (next == null || otherStart.isBefore(LocalDateTime.of(next.ngayKhoiHanh, next.gioKhoiHanh)));
            if (nearestPrevious) {
                previous = existing;
            }
            if (nearestNext) {
                next = existing;
            }
        }
        if (previous != null) {
            connection(previous, trip);
        }
        if (next != null) {
            connection(trip, next);
        }
    }

    public static void connection(ChuyenXe before, ChuyenXe after) {
        require(!LocalDateTime.of(after.ngayKhoiHanh, after.gioKhoiHanh).isBefore(before.gioDenDuKien), "Hai chuyến trùng lịch.");
        require(before.tuyenXe.tramDen.maBenXe.equals(after.tuyenXe.tramKhoiHanh.maBenXe),
            "Bến nối chuyến không khớp; cần bố trí di chuyển hoặc đổi xe/nhân viên.");
    }

    public static void vehicleConnection(ChuyenXe before, ChuyenXe after) {
        connection(before, after);
        require(!LocalDateTime.of(after.ngayKhoiHanh, after.gioKhoiHanh).isBefore(before.gioDenDuKien.plusMinutes(vehicleTurnaroundMinutes())), "Xe chưa đủ thời gian chuẩn bị.");
    }

    public static void validateVehicleSchedule(EntityManager em, ChuyenXe trip, String vehicleId) {
        trip = (ChuyenXe) Hibernate.unproxy(trip);
        trip.tuyenXe = (TuyenXe) Hibernate.unproxy(trip.tuyenXe);
        ChuyenXe previous = null, next = null;
        LocalDateTime start = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);
        String jpql = """
            select c from ChuyenXe c
            where c.xe.maXe = :vehicle and c.maChuyen <> :id
              and c.trangThai in (:sale, :running)
            """;
        List<ChuyenXe> scheduledTrips = em.createQuery(jpql, ChuyenXe.class)
            .setParameter("vehicle", vehicleId)
            .setParameter("id", trip.maChuyen)
            .setParameter("sale", TrangThaiChuyen.DANG_MO_BAN)
            .setParameter("running", TrangThaiChuyen.DANG_CHAY)
            .getResultList();
        for (ChuyenXe other : scheduledTrips) {
            LocalDateTime otherStart = LocalDateTime.of(other.ngayKhoiHanh, other.gioKhoiHanh);
            require(!start.isBefore(other.gioDenDuKien) || !otherStart.isBefore(trip.gioDenDuKien), "Xe đã có chuyến trùng lịch.");
            boolean nearestPrevious = !start.isBefore(other.gioDenDuKien)
                && (previous == null || other.gioDenDuKien.isAfter(previous.gioDenDuKien));
            boolean nearestNext = !otherStart.isBefore(trip.gioDenDuKien)
                && (next == null || otherStart.isBefore(LocalDateTime.of(next.ngayKhoiHanh, next.gioKhoiHanh)));
            if (nearestPrevious) {
                previous = other;
            }
            if (nearestNext) {
                next = other;
            }
        }
        if (previous != null) {
            vehicleConnection(previous, trip);
        }
        if (next != null) {
            vehicleConnection(trip, next);
        }
    }

    public static boolean needsDrivingPlan(ChuyenXe trip) {
        LocalDateTime departure = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);
        Duration travelTime = Duration.between(departure, trip.gioDenDuKien);
        return travelTime.compareTo(DRIVING_PLAN_THRESHOLD) > 0;
    }

    public static String reviewPrefix(ChuyenXe trip) {
        var format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String departure = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh).format(format);
        String arrival = trip.gioDenDuKien.format(format);
        String vehicleId = trip.xe == null ? "NONE" : trip.xe.maXe;
        return "[T11_PLAN:" + departure + "/" + arrival + "/" + vehicleId + ":";
    }

    public static void readyForOperation(EntityManager em, ChuyenXe trip) {
        trip = (ChuyenXe) Hibernate.unproxy(trip);
        validateTrip(em, trip);
        List<PhanCongChuyenXe> crew = em.createQuery("select p from PhanCongChuyenXe p where p.chuyenXe=:trip", PhanCongChuyenXe.class).setParameter("trip", trip).getResultList();
        int mainDrivers = 0;
        boolean hasAssistant = false;
        boolean hasReliefDriver = false;
        for (PhanCongChuyenXe assignment : crew) {
            if (assignment.vaiTro == VaiTroChuyen.TAI_XE_CHINH) {
                mainDrivers++;
            }
            if (assignment.vaiTro == VaiTroChuyen.PHU_XE) {
                hasAssistant = true;
            }
            if (assignment.vaiTro == VaiTroChuyen.TAI_XE_PHU) {
                hasReliefDriver = true;
            }
        }
        require(mainDrivers == 1, "Cần đúng một tài xế chính.");
        require(hasAssistant, "Cần ít nhất một phụ xe.");
        if (needsDrivingPlan(trip)) {
            require(hasReliefDriver, "Chuyến trên 4 giờ cần tài xế phụ.");
            String prefix = reviewPrefix(trip);
            boolean hasReviewedPlan = false;
            for (PhanCongChuyenXe assignment : crew) {
                if (assignment.ghiChu != null && assignment.ghiChu.startsWith(prefix)) {
                    hasReviewedPlan = true;
                    break;
                }
            }
            require(hasReviewedPlan, "Chuyến dài cần người điều phối xác nhận kế hoạch lái/nghỉ; hệ thống chưa tự kiểm chứng giờ nghỉ.");
        }
    }
    /** Checks current assignments; T12 invokes readyForOperation before opening sales. */
    public static void validateTrip(EntityManager em, ChuyenXe trip) {
        trip = (ChuyenXe) Hibernate.unproxy(trip);
        for (PhanCongChuyenXe assignment : em.createQuery("select p from PhanCongChuyenXe p where p.chuyenXe=:trip", PhanCongChuyenXe.class)
            .setParameter("trip", trip).getResultList()) {
            if (assignment.vaiTro == VaiTroChuyen.KIEM_VE) continue; // Legacy assignments do not participate in crew validation.
            NhanVien employee = em.find(NhanVien.class, assignment.nhanVien.maNhanVien, LockModeType.PESSIMISTIC_WRITE);
            em.refresh(employee, LockModeType.PESSIMISTIC_WRITE);
            validate(em, trip, employee, assignment.vaiTro, assignment.maPhanCong);
        }
    }
}
