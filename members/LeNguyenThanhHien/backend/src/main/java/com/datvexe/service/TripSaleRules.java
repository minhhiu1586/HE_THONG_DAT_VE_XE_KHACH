package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.security.AccessControl;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

/** T12 authoritative sale eligibility. Callers hold the operator/trip locks in their transaction. */
public final class TripSaleRules {
    private TripSaleRules() {}
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public record Check(String maDieuKien,String tenDieuKien,boolean dat,String lyDo) {}
    public record Result(boolean duDieuKien,OffsetDateTime ngungNhanDatVeLuc,OffsetDateTime serverTime,List<Check> dieuKien) {}
    public static long cutoffMinutes() {
        long minutes=Long.parseLong(System.getProperty("trip.booking.cutoff.minutes",
            System.getenv().getOrDefault("TRIP_BOOKING_CUTOFF_MINUTES","60")));
        if(minutes<0) throw new IllegalStateException("TRIP_BOOKING_CUTOFF_MINUTES must be nonnegative");
        return minutes;
    }
    public static boolean draft(ChuyenXe trip) {
        return trip.trangThai==TrangThaiChuyen.NHAP || trip.trangThai==TrangThaiChuyen.CHUA_KHOI_HANH;
    }
    public static boolean scheduled(ChuyenXe trip) {
        return draft(trip) || trip.trangThai==TrangThaiChuyen.DANG_MO_BAN;
    }
    public static LocalDateTime start(ChuyenXe trip) { return LocalDateTime.of(trip.ngayKhoiHanh,trip.gioKhoiHanh); }
    public static LocalDateTime cutoff(ChuyenXe trip) { return start(trip).minusMinutes(cutoffMinutes()); }
    public static boolean validPrice(BigDecimal price) {
        if(price==null || price.signum()<0)return false;
        var value=price.stripTrailingZeros();return value.scale()<=0 && value.precision()-value.scale()<=12;
    }
    private static void require(boolean valid,String message) { if(!valid) throw new DomainException(409,message); }
    private static void check(List<Check> checks,String code,String name,Runnable action) {
        try {action.run();checks.add(new Check(code,name,true,null));}
        catch(DomainException error) {checks.add(new Check(code,name,false,error.getMessage()));}
    }
    public static Result inspect(EntityManager em,ChuyenXe trip,LocalDateTime now) {
        // Reload related rows after waiting for locks: the persistence context may
        // still contain route/station values loaded before another transaction committed.
        if(trip.tuyenXe.nhaXe!=null)AccessControl.resource(em,NhaXe.class,trip.tuyenXe.nhaXe.maNhaXe);
        trip.tuyenXe=AccessControl.resource(em,TuyenXe.class,trip.tuyenXe.maTuyen);
        var stations=new TreeMap<String,BenXe>();
        if(trip.tuyenXe.tramKhoiHanh!=null)stations.put(trip.tuyenXe.tramKhoiHanh.maBenXe,trip.tuyenXe.tramKhoiHanh);
        if(trip.tuyenXe.tramDen!=null)stations.put(trip.tuyenXe.tramDen.maBenXe,trip.tuyenXe.tramDen);
        stations.replaceAll((id,station)->AccessControl.resource(em,BenXe.class,id));
        if(trip.tuyenXe.tramKhoiHanh!=null)trip.tuyenXe.tramKhoiHanh=stations.get(trip.tuyenXe.tramKhoiHanh.maBenXe);
        if(trip.tuyenXe.tramDen!=null)trip.tuyenXe.tramDen=stations.get(trip.tuyenXe.tramDen.maBenXe);
        List<Check> checks=new ArrayList<>();
        check(checks,"TRANG_THAI","Trạng thái chuyến",()->require(scheduled(trip),"Chuyến đã chạy, hoàn tất hoặc hủy; không thể mở bán."));
        check(checks,"HAN_DAT_VE","Thời hạn nhận đặt vé",()->require(now.isBefore(cutoff(trip)),"Đã đến thời điểm ngừng nhận đặt vé."));
        check(checks,"LICH_TRINH","Ngày giờ và giá vé",()->{
            require(trip.gioDenDuKien!=null && trip.gioDenDuKien.isAfter(start(trip)),"Giờ đến phải sau khởi hành.");
            require(validPrice(trip.tuyenXe.giaCoBan),"Giá vé phải là số nguyên VND không âm, tối đa 12 chữ số.");
        });
        check(checks,"TUYEN_BEN","Tuyến và bến xe",()->{
            var route=trip.tuyenXe;
            require(route.trangThai==TrangThaiTuyenXe.DANG_KHAI_THAC,"Tuyến chưa được khai thác.");
            require(route.tramKhoiHanh!=null && route.tramDen!=null && !route.tramKhoiHanh.maBenXe.equals(route.tramDen.maBenXe),"Bến đầu/cuối không hợp lệ.");
            require(route.tramKhoiHanh.trangThai==TrangThaiBenXe.HOAT_DONG && route.tramDen.trangThai==TrangThaiBenXe.HOAT_DONG,"Bến khởi hành hoặc bến đến không hoạt động.");
        });
        check(checks,"NHA_XE","Nhà xe vận hành",()->{
            require(trip.tuyenXe.nhaXe!=null,"Chuyến chưa có nhà xe vận hành.");
            AccessControl.businessAllowed(em,trip.tuyenXe.nhaXe);
        });
        check(checks,"XE_DANG_KIEM","Xe, đăng kiểm và sơ đồ ghế",()->{
            require(trip.xe!=null,"Chuyến chưa được gán xe.");VehicleRules.ready(em,trip.xe.maXe,trip);
        });
        check(checks,"LICH_XE","Lịch vận hành và thời gian chuẩn bị xe",()->{
            require(trip.xe!=null,"Chuyến chưa được gán xe.");AssignmentRules.validateVehicleSchedule(em,trip,trip.xe.maXe);
        });
        check(checks,"NHAN_SU","Phân công, bằng lái và lịch nhân viên",()->AssignmentRules.readyForOperation(em,trip));
        return new Result(checks.stream().allMatch(Check::dat),cutoff(trip).atZone(ZONE).toOffsetDateTime(),now.atZone(ZONE).toOffsetDateTime(),List.copyOf(checks));
    }
    public static void ensureOpenable(EntityManager em,ChuyenXe trip,LocalDateTime now) {
        var result=inspect(em,trip,now);
        if(!result.duDieuKien()) throw new DomainException(409,result.dieuKien().stream().filter(c->!c.dat()).map(Check::lyDo).findFirst().orElse("Chuyến chưa đủ điều kiện mở bán."));
    }
    /** Booking/holding modules call this inside their write transaction, using server time. */
    public static void ensureSellable(EntityManager em,ChuyenXe trip,LocalDateTime now) {
        require(trip.trangThai==TrangThaiChuyen.DANG_MO_BAN,"Chuyến chưa mở bán hoặc đã kết thúc nhận đặt vé.");
        ensureOpenable(em,trip,now);
    }
}
