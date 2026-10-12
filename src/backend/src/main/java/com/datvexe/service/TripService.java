package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.TripInputs;
import com.datvexe.repository.ChuyenXeRepository;
import com.datvexe.security.*;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** T12 management uses fixed ChuyenXe attributes and T10/T11 helpers in one transaction. */
public final class TripService {
    private final EntityManagerFactory factory;
    private final Clock clock;
    public TripService(EntityManagerFactory factory) {this(factory,Clock.system(TripSaleRules.ZONE));}
    public TripService(EntityManagerFactory factory,Clock clock) {this.factory=factory;this.clock=clock;}
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TripSaleRules.ZONE);}
    private <T> T tx(Function<EntityManager,T> action) {
        try(var em=factory.createEntityManager()) {
            var transaction=em.getTransaction();transaction.begin();
            try {AccessPolicy.guard(em);T result=action.apply(em);transaction.commit();return result;}
            catch(RuntimeException error) {if(transaction.isActive())transaction.rollback();throw error;}
        }
    }
    private static String id(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_-]{1,40}")) throw new DomainException(400,"Mã phải gồm chữ không dấu, số, gạch ngang hoặc gạch dưới, tối đa 40 ký tự.");
        return value;
    }
    private static String optional(String value) {return value==null || value.isBlank()?null:id(value);}
    private static void require(boolean condition,String message) {if(!condition)throw new DomainException(409,message);}
    private static void scope(EntityManager em,NhaXe owner) {
        var context=RequestIdentity.get();
        if(context!=null) {
            var caller=AccessControl.authenticated(em,context.user(),Instant.now());
            AccessControl.operator(caller,owner);
        }
    }
    /** Operator lock serializes opening/mutations for two independent users in the same fleet. */
    private static ChuyenXe locked(EntityManager em,String code) {
        ChuyenXe found=em.find(ChuyenXe.class,id(code));
        if(found==null) throw new DomainException(404,"Không tìm thấy chuyến.");
        found=(ChuyenXe)org.hibernate.Hibernate.unproxy(found);
        if(found.tuyenXe.nhaXe!=null) AccessControl.resource(em,NhaXe.class,found.tuyenXe.nhaXe.maNhaXe);
        scope(em,found.tuyenXe.nhaXe);
        return new ChuyenXeRepository(em).lock(code);
    }
    public Map<String,Object> create(TripInputs.Draft input) {return tx(em->{
        if(input==null)throw new DomainException(400,"Thiếu thông tin chuyến.");
        String code=id(input.maChuyen());
        if(em.find(ChuyenXe.class,code)!=null)throw new DomainException(409,"Mã chuyến đã tồn tại.");
        var trip=new ChuyenXe();trip.maChuyen=code;trip.trangThai=TrangThaiChuyen.NHAP;
        apply(em,trip,input,false);em.persist(trip);em.flush();return view(em,trip);
    });}
    private void apply(EntityManager em,ChuyenXe trip,TripInputs.Draft input,boolean updating) {
        if(input==null)throw new DomainException(400,"Thiếu thông tin chuyến.");
        if(updating && input.maChuyen()!=null && !trip.maChuyen.equals(input.maChuyen()))throw new DomainException(400,"Không được đổi mã chuyến.");
        var route=AccessControl.resource(em,TuyenXe.class,id(input.maTuyen()));
        require(route.nhaXe!=null,"Tuyến chưa có nhà xe vận hành.");
        var owner=AccessControl.resource(em,NhaXe.class,route.nhaXe.maNhaXe);scope(em,owner);
        if(updating && trip.tuyenXe.nhaXe!=null)require(trip.tuyenXe.nhaXe.maNhaXe.equals(owner.maNhaXe),"Không chuyển chuyến sang nhà xe khác.");
        if(input.ngayKhoiHanh()==null)throw new DomainException(400,"Vui lòng nhập ngày khởi hành.");
        LocalTime departure=input.gioKhoiHanh()==null?route.thoiGianKhoiHanh:input.gioKhoiHanh();
        if(departure==null)throw new DomainException(400,"Vui lòng nhập giờ khởi hành.");
        LocalDateTime start=LocalDateTime.of(input.ngayKhoiHanh(),departure),arrival=input.gioDenDuKien();
        if(arrival==null) {
            if(route.thoiGianDuKien==null || route.thoiGianDuKien.signum()<=0)throw new DomainException(400,"Tuyến chưa có thời lượng hợp lệ.");
            try {arrival=start.plusMinutes(route.thoiGianDuKien.multiply(BigDecimal.valueOf(60)).setScale(0,RoundingMode.HALF_UP).longValueExact());}
            catch(ArithmeticException | DateTimeException error) {throw new DomainException(400,"Thời lượng tuyến vượt giới hạn.");}
        }
        if(!arrival.isAfter(start))throw new DomainException(400,"Ngày giờ đến phải sau khởi hành.");
        if(!TripSaleRules.validPrice(route.giaCoBan))throw new DomainException(400,"Giá vé phải là số nguyên VND không âm, tối đa 12 chữ số.");
        if(input.giaVeDuKien()!=null && (input.giaVeDuKien().signum()<0 || input.giaVeDuKien().compareTo(route.giaCoBan)!=0))
            throw new DomainException(400,"Giá dự kiến phải không âm và khớp giá tuyến; chuyến không lưu giá riêng.");
        Xe vehicle=input.maXe()==null || input.maXe().isBlank()?null:AccessControl.resource(em,Xe.class,id(input.maXe()));
        if(vehicle!=null) {scope(em,vehicle.nhaXe);require(vehicle.nhaXe!=null && vehicle.nhaXe.maNhaXe.equals(owner.maNhaXe),"Xe không thuộc nhà xe của tuyến.");}
        trip.tuyenXe=route;trip.xe=vehicle;trip.ngayKhoiHanh=input.ngayKhoiHanh();trip.gioKhoiHanh=departure;trip.gioDenDuKien=arrival;
        if(updating) {
            if(vehicle!=null)AssignmentRules.validateVehicleSchedule(em,trip,vehicle.maXe);
            AssignmentRules.validateTrip(em,trip);
        }
    }
    public Map<String,Object> update(String code,TripInputs.Draft input) {return tx(em->{
        var trip=locked(em,code);require(TripSaleRules.draft(trip),"Chỉ sửa chuyến nháp hoặc chuyến cũ chưa mở bán.");
        require(!VehicleRules.hasTicketsOrHolds(em,trip),"Chuyến đã có giữ chỗ/vé; không sửa hành trình hoặc lịch đã cam kết.");
        apply(em,trip,input,true);em.flush();return view(em,trip);
    });}
    public Map<String,Object> get(String code) {return tx(em->view(em,locked(em,code)));}
    public Map<String,Object> list(String owner,String route,String vehicle,LocalDate from,LocalDate to,TrangThaiChuyen state,int page,int size) {return tx(em->{
        if(page<1 || size<1 || size>100 || ((long)page-1)*size>Integer.MAX_VALUE)throw new DomainException(400,"Phân trang không hợp lệ.");
        if(from!=null && to!=null && from.isAfter(to))throw new DomainException(400,"Khoảng ngày không hợp lệ.");
        String scope=RequestIdentity.operatorId(),requested=optional(owner);
        if(scope!=null && requested!=null && !scope.equals(requested))throw AccessControl.denied();
        var filters=new ChuyenXeRepository.Filters(scope==null?requested:scope,optional(route),optional(vehicle),from,to,state);
        var repo=new ChuyenXeRepository(em);long total=repo.count(filters);
        var values=repo.search(filters,(page-1)*size,size).stream().map(c->view(em,c)).toList();
        return Map.of("items",values,"page",page,"size",size,"totalItems",total,"totalPages",(total+size-1)/size);
    });}
    public TripSaleRules.Result eligibility(String code) {return tx(em->TripSaleRules.inspect(em,locked(em,code),now()));}
    public Map<String,Object> open(String code) {return tx(em->{
        var trip=locked(em,code);TripSaleRules.ensureOpenable(em,trip,now());
        require(now().isBefore(TripSaleRules.cutoff(trip)),"Đã đến thời điểm ngừng nhận đặt vé trong khi kiểm tra điều kiện.");
        if(trip.trangThai!=TrangThaiChuyen.DANG_MO_BAN) {
            require(TripSaleRules.draft(trip),"Chuyến không thể chuyển sang mở bán.");trip.trangThai=TrangThaiChuyen.DANG_MO_BAN;
        }
        em.flush();return view(em,trip);
    });}
    public Map<String,Object> cancel(String code) {return tx(em->{
        var trip=locked(em,code);require(TripSaleRules.scheduled(trip),"Không hủy chuyến đã chạy, hoàn tất hoặc đã hủy.");
        require(!VehicleRules.hasTicketsOrHolds(em,trip),"Chuyến có giữ chỗ/vé; cần quy trình vận hành, không tự hủy vé.");
        trip.trangThai=TrangThaiChuyen.DA_HUY;return view(em,trip);
    });}
    public void delete(String code) {tx(em->{
        var trip=locked(em,code);require(TripSaleRules.draft(trip),"Chỉ xóa cứng chuyến nháp chưa được sử dụng.");
        require(!VehicleRules.hasTicketsOrHolds(em,trip) && trip.danhSachDanhGia.isEmpty(),"Chuyến có dữ liệu liên quan; không xóa cứng.");
        em.remove(trip);return null;
    });}
    private Map<String,Object> view(EntityManager em,ChuyenXe trip) {
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("maChuyen",trip.maChuyen);value.put("maTuyen",trip.tuyenXe.maTuyen);value.put("tenTuyen",trip.tuyenXe.tenTuyen);
        value.put("maNhaXe",trip.tuyenXe.nhaXe==null?null:trip.tuyenXe.nhaXe.maNhaXe);
        value.put("tenNhaXe",trip.tuyenXe.nhaXe==null?null:trip.tuyenXe.nhaXe.tenNhaXe);
        value.put("maXe",trip.xe==null?null:trip.xe.maXe);value.put("bienSo",trip.xe==null?null:trip.xe.bienSo);
        value.put("ngayKhoiHanh",trip.ngayKhoiHanh);value.put("gioKhoiHanh",trip.gioKhoiHanh);value.put("gioDenDuKien",trip.gioDenDuKien);
        value.put("giaVeDuKien",trip.tuyenXe.giaCoBan==null?null:trip.tuyenXe.giaCoBan.stripTrailingZeros().toPlainString());value.put("trangThai",trip.trangThai);
        value.put("ngungNhanDatVeLuc",TripSaleRules.cutoff(trip).atZone(TripSaleRules.ZONE).toOffsetDateTime());value.put("phutDungNhanDatVe",TripSaleRules.cutoffMinutes());
        value.put("serverTime",now().atZone(TripSaleRules.ZONE).toOffsetDateTime());
        boolean expired=!now().isBefore(TripSaleRules.cutoff(trip));
        value.put("daHetHanDatVe",expired);
        value.put("coTheDatVe",trip.trangThai==TrangThaiChuyen.DANG_MO_BAN && !expired && TripSaleRules.inspect(em,trip,now()).duDieuKien());
        value.put("coTheSua",TripSaleRules.draft(trip) && !VehicleRules.hasTicketsOrHolds(em,trip));
        value.put("coTheXoa",TripSaleRules.draft(trip) && !VehicleRules.hasTicketsOrHolds(em,trip) && trip.danhSachDanhGia.isEmpty());
        value.put("coTheHuy",TripSaleRules.scheduled(trip) && !VehicleRules.hasTicketsOrHolds(em,trip));
        var crew=em.createQuery("select p from PhanCongChuyenXe p where p.chuyenXe=:trip order by p.maPhanCong",PhanCongChuyenXe.class).setParameter("trip",trip).getResultList();
        value.put("nhanVienPhanCong",crew.stream().map(p->{
            Map<String,Object> item=new LinkedHashMap<>();item.put("maPhanCong",p.maPhanCong);item.put("maNhanVien",p.nhanVien.maNhanVien);
            item.put("hoTen",p.nhanVien.hoTen);item.put("vaiTro",p.vaiTro);item.put("trangThai",p.nhanVien.trangThai);
            item.put("ngayHetHanBangLai",p.nhanVien.ngayHetHanBangLai);item.put("ghiChu",p.ghiChu);return item;
        }).toList());return value;
    }
}
