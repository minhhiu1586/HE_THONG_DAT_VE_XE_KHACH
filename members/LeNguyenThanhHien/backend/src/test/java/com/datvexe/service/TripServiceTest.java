package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.AuthSession;
import com.datvexe.domain.States.*;
import com.datvexe.dto.*;
import com.datvexe.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class TripServiceTest {
    static final LocalDate DAY=LocalDate.of(2030,1,1);
    EntityManagerFactory factory;TripService trips;VehicleService vehicles;AssignmentService assignments;EmployeeService employees;
    @BeforeEach void setup() {
        factory=Persistence.createEntityManagerFactory("datvexe",Map.of("jakarta.persistence.jdbc.driver","org.h2.Driver",
            "jakarta.persistence.jdbc.url","jdbc:h2:mem:t12;MODE=PostgreSQL","jakarta.persistence.jdbc.user","sa",
            "jakarta.persistence.jdbc.password","","hibernate.hbm2ddl.auto","create-drop"));
        trips=new TripService(factory,clock(DAY.minusDays(1).atStartOfDay()));vehicles=new VehicleService(factory);assignments=new AssignmentService(factory);employees=new EmployeeService(factory);
        tx(em->{
            em.createNativeQuery("create table trip_lifecycle_history(id varchar(40) primary key,ma_chuyen varchar(40),trang_thai_cu varchar(30),trang_thai_moi varchar(30),ma_nguoi_thao_tac varchar(40),vai_tro varchar(30),ly_do varchar(1000),created_at timestamp with time zone)").executeUpdate();
            em.createNativeQuery("create table trip_cancellation_work(ma_don varchar(40),ma_chuyen varchar(40),trang_thai varchar(24),ly_do varchar(1000),last_error varchar(120),created_at timestamp with time zone,updated_at timestamp with time zone,primary key(ma_don,ma_chuyen))").executeUpdate();
            em.createNativeQuery("create table trip_cancellation_notification(ma_don varchar(40),ma_chuyen varchar(40),status varchar(20),primary key(ma_don,ma_chuyen))").executeUpdate();
            for(String code:List.of("A","B")){var n=new NhaXe();n.maNhaXe=code;n.tenNhaXe=code;em.persist(n);}
            for(String code:List.of("AD","NX","KH")) {
                var user=new NguoiDung();user.maNguoiDung=code;user.hoTen=code;user.email=code+"@example.test";user.soDienThoai=code;user.matKhau="not-a-login";
                user.trangThai=TrangThaiTaiKhoan.HOAT_DONG;user.loaiTaiKhoan=code.equals("AD")?LoaiTaiKhoan.ADMIN:code.equals("NX")?LoaiTaiKhoan.NHA_XE:LoaiTaiKhoan.KHACH_HANG;
                if(code.equals("NX"))user.nhaXe=em.find(NhaXe.class,"A");em.persist(user);
                var session=new AuthSession();session.tokenHash=code;session.csrfToken=code;session.nguoiDung=user;
                session.taoLuc=Instant.now();session.hetHan=Instant.now().plusSeconds(3600);em.persist(session);
            }
        });
        var routes=new RouteTripService(factory);routes.createTinhThanh(new Inputs.TinhThanh("CITY","City",null));
        for(String code:List.of("S","D"))routes.createBenXe(new Inputs.BenXe(code,code,code,null,"CITY",null));
        for(String code:List.of("R","RETURN","FOREIGN"))routes.createTuyenXe(new Inputs.TuyenXe(code,code,code.equals("RETURN")?"D":"S",code.equals("RETURN")?"S":"D",LocalTime.of(8,0),BigDecimal.ONE,new BigDecimal("7"),new BigDecimal("100000"),null,TrangThaiTuyenXe.DANG_KHAI_THAC,code.equals("FOREIGN")?"B":"A"));
        vehicles.saveType(null,new VehicleInputs.LoaiXe("L","Four seats",4,2,null));
        for(String code:List.of("X","Y"))vehicles.saveVehicle(null,new VehicleInputs.Xe(code,"51B-"+code,"L",code.equals("X")?"A":"B",2024,LocalDate.of(2020,1,1),LocalDate.of(2100,1,1),TrangThaiXe.SAN_SANG));
    }
    @AfterEach void close(){RequestIdentity.clear();factory.close();}
    static Clock clock(LocalDateTime now){return Clock.fixed(now.atZone(TripSaleRules.ZONE).toInstant(),TripSaleRules.ZONE);}
    void tx(Consumer<EntityManager> work){try(var em=factory.createEntityManager()){em.getTransaction().begin();work.accept(em);em.getTransaction().commit();}}
    TripInputs.Draft input(String code,String route,String vehicle,LocalTime time){return new TripInputs.Draft(code,route,vehicle,DAY,time,null,null);}
    void crew(String code) {
        for(int i=1;i<=3;i++) {
            String id=code+"E"+i;boolean assistant=i==3;
            String phone="09"+String.format(Locale.ROOT,"%08d",Math.floorMod(id.hashCode(),100000000));
            employees.save(null,new EmployeeInputs.Employee(id,"Staff "+id,phone,id+"@example.test",null,"A",assistant?LoaiNhanVien.PHU_XE:LoaiNhanVien.TAI_XE,TrangThaiNhanVien.DANG_LAM_VIEC,assistant?null:"123456789012",assistant?null:LocalDate.of(2100,1,1)));
            assignments.save(null,new AssignmentInputs.PhanCong(code+"P"+i,code,id,i==1?VaiTroChuyen.TAI_XE_CHINH:i==2?VaiTroChuyen.TAI_XE_PHU:VaiTroChuyen.PHU_XE,"Kế hoạch kiểm thử",true));
        }
    }
    void ready(String code){trips.create(input(code,"R","X",LocalTime.of(8,0)));crew(code);}
    void rejected(String code,String key){
        var report=trips.eligibility(code);assertFalse(report.duDieuKien());assertTrue(report.dieuKien().stream().anyMatch(c->c.maDieuKien().equals(key)&&!c.dat()));
        assertEquals(409,assertThrows(DomainException.class,()->trips.open(code)).status());
        assertNotEquals(TrangThaiChuyen.DANG_MO_BAN,trips.get(code).get("trangThai"));
    }
    @Test void eligibilityRefreshesRouteAndStationAlreadyLoadedInPersistenceContext() {
        ready("C");
        tx(em->{
            var trip=em.find(ChuyenXe.class,"C");
            assertEquals(TrangThaiBenXe.HOAT_DONG,trip.tuyenXe.tramDen.trangThai);
            em.createNativeQuery("update ben_xe set trang_thai='TAM_NGUNG' where ma_ben_xe='D'").executeUpdate();
            var report=TripSaleRules.inspect(em,trip,DAY.minusDays(1).atStartOfDay());
            assertFalse(report.duDieuKien());
            assertTrue(report.dieuKien().stream().anyMatch(c->c.maDieuKien().equals("TUYEN_BEN")&&!c.dat()));
        });
    }
    @Test void draftCanLackVehicleAndCrewButCannotBeSearchedOrSold() {
        var draft=trips.create(input("C","R",null,LocalTime.of(22,0)));
        assertEquals(TrangThaiChuyen.NHAP,draft.get("trangThai"));assertEquals(DAY.plusDays(1).atTime(5,0),draft.get("gioDenDuKien"));
        assertEquals("100000",draft.get("giaVeDuKien"));assertEquals(List.of(),draft.get("nhanVienPhanCong"));
        assertTrue(new RouteTripService(factory).searchChuyenXe("S","D",DAY).isEmpty());rejected("C","XE_DANG_KIEM");
        tx(em->assertThrows(DomainException.class,()->TripSaleRules.ensureSellable(em,em.find(ChuyenXe.class,"C"),DAY.minusDays(1).atStartOfDay())));
    }
    @Test void invalidReferencesPricesAndArrivalRollbackWithoutChangingRoute() {
        assertEquals(404,assertThrows(DomainException.class,()->trips.create(input("C","MISSING","X",LocalTime.NOON))).status());
        assertEquals(404,assertThrows(DomainException.class,()->trips.create(input("C","R","MISSING",LocalTime.NOON))).status());
        assertEquals(400,assertThrows(DomainException.class,()->trips.create(new TripInputs.Draft("C","R","X",DAY,LocalTime.NOON,null,BigDecimal.valueOf(-1)))).status());
        assertEquals(400,assertThrows(DomainException.class,()->trips.create(new TripInputs.Draft("C","R","X",DAY,LocalTime.NOON,DAY.atTime(11,59),null))).status());
        assertEquals(400,assertThrows(DomainException.class,()->trips.create(new TripInputs.Draft("C","R","X",DAY,LocalTime.NOON,DAY.atTime(12,0),null))).status());
        assertEquals(0L,trips.list(null,null,null,null,null,null,1,20).get("totalItems"));
        trips.create(input("C","R","X",LocalTime.NOON));
        assertEquals(409,assertThrows(DomainException.class,()->trips.create(input("C","R","X",LocalTime.NOON))).status());
        tx(em->assertEquals(new BigDecimal("100000.00"),em.find(TuyenXe.class,"R").giaCoBan));
    }
    @Test void actualT11AssignmentFlowAndIdempotentOpening() {
        trips.create(input("C","R","X",LocalTime.of(8,0)));rejected("C","NHAN_SU");crew("C");
        assertTrue(trips.eligibility("C").duDieuKien());assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.open("C").get("trangThai"));
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.open("C").get("trangThai"));
        assertEquals(3,((List<?>)trips.get("C").get("nhanVienPhanCong")).size());
        assertEquals(List.of("C"),new RouteTripService(factory).searchChuyenXe("S","D",DAY).stream().map(c->c.get("maChuyen")).toList());
        assertEquals(409,assertThrows(DomainException.class,()->trips.update("C",input("C","R","X",LocalTime.NOON))).status());
    }
    @Test void fareResponseIsVndStringAndFractionalRouteFareCannotCreateDraft() {
        var value=trips.create(input("C","R","X",LocalTime.NOON));assertEquals("100000",value.get("giaVeDuKien"));
        tx(em->em.find(TuyenXe.class,"R").giaCoBan=new BigDecimal("100000.50"));
        assertEquals(400,assertThrows(DomainException.class,()->trips.create(input("INVALID","R","X",LocalTime.NOON))).status());
        assertFalse(trips.eligibility("C").duDieuKien());
    }
    @Test void routeTerminalAndOperatorAreRecheckedAtOpening() {
        ready("C");tx(em->em.find(TuyenXe.class,"R").trangThai=TrangThaiTuyenXe.NGUNG_KHAI_THAC);rejected("C","TUYEN_BEN");
        tx(em->{em.find(TuyenXe.class,"R").trangThai=TrangThaiTuyenXe.DANG_KHAI_THAC;em.find(BenXe.class,"D").trangThai=TrangThaiBenXe.BAO_TRI;});rejected("C","TUYEN_BEN");
        tx(em->{em.find(BenXe.class,"D").trangThai=TrangThaiBenXe.HOAT_DONG;em.find(NhaXe.class,"A").trangThai=TrangThaiNhaXe.TAM_NGUNG;});rejected("C","NHA_XE");
    }
    @Test void vehicleMaintenanceInspectionAndInvalidLayoutBlockOpening() {
        ready("C");tx(em->em.find(Xe.class,"X").trangThai=TrangThaiXe.BAO_TRI);rejected("C","XE_DANG_KIEM");
        tx(em->{em.find(Xe.class,"X").trangThai=TrangThaiXe.SAN_SANG;em.find(Xe.class,"X").hanDangKiem=DAY.minusDays(1);});rejected("C","XE_DANG_KIEM");
        tx(em->{em.find(Xe.class,"X").hanDangKiem=DAY;em.find(Xe.class,"X").danhSachGhe.getFirst().tang=3;});rejected("C","XE_DANG_KIEM");
    }
    @Test void expiredLicenseInactiveOrWrongRoleStaffAndStalePlanAreRejected() {
        ready("C");tx(em->em.find(NhanVien.class,"CE1").ngayHetHanBangLai=DAY.minusDays(1));rejected("C","NHAN_SU");
        tx(em->{em.find(NhanVien.class,"CE1").ngayHetHanBangLai=DAY;em.find(NhanVien.class,"CE1").loaiNhanVien=LoaiNhanVien.DIEU_HANH;});rejected("C","NHAN_SU");
        tx(em->{em.find(NhanVien.class,"CE1").loaiNhanVien=LoaiNhanVien.TAI_XE;em.find(NhanVien.class,"CE1").trangThai=TrangThaiNhanVien.TAM_NGHI;});rejected("C","NHAN_SU");
        tx(em->{em.find(NhanVien.class,"CE1").trangThai=TrangThaiNhanVien.DANG_LAM_VIEC;em.find(PhanCongChuyenXe.class,"CP1").ghiChu=null;em.find(PhanCongChuyenXe.class,"CP2").ghiChu=null;em.find(PhanCongChuyenXe.class,"CP3").ghiChu=null;});rejected("C","NHAN_SU");
    }
    @Test void cutoffIsExclusiveAndAppliesToOpenedTripsAndBookingHelper() {
        ready("C");var limit=DAY.atTime(7,0);
        var before=new TripService(factory,clock(limit.minusSeconds(1)));assertTrue(before.eligibility("C").duDieuKien());before.open("C");
        for(var now:List.of(limit,limit.plusSeconds(1))) {
            var after=new TripService(factory,clock(now));assertFalse(after.eligibility("C").duDieuKien());
            assertEquals(false,after.get("C").get("coTheDatVe"));assertEquals(true,after.get("C").get("daHetHanDatVe"));
            assertEquals(409,assertThrows(DomainException.class,()->after.open("C")).status());
            tx(em->assertThrows(DomainException.class,()->TripSaleRules.ensureSellable(em,em.find(ChuyenXe.class,"C"),now)));
        }
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.get("C").get("trangThai"));
    }
    @Test void cutoffReachedDuringValidationStillRollsBackOpening() {
        ready("C");var limit=DAY.atTime(7,0);var calls=new java.util.concurrent.atomic.AtomicInteger();
        Clock advancing=new Clock(){
            @Override public ZoneId getZone(){return TripSaleRules.ZONE;}
            @Override public Clock withZone(ZoneId zone){return this;}
            @Override public Instant instant(){return limit.minusSeconds(1).plusSeconds(calls.getAndIncrement()).atZone(getZone()).toInstant();}
        };
        assertEquals(409,assertThrows(DomainException.class,()->new TripService(factory,advancing).open("C")).status());
        assertEquals(TrangThaiChuyen.NHAP,trips.get("C").get("trangThai"));
    }
    @Test void simultaneousDraftsCanExistButOnlyOneConflictingTripCanOpen() throws Exception {
        ready("C1");ready("C2");CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->openTogether("C1",ready,start));var second=pool.submit(()->openTogether("C2",ready,start));
            try {assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();assertEquals(1,first.get(30,TimeUnit.SECONDS)+second.get(30,TimeUnit.SECONDS));}
            finally{start.countDown();}
        }
        assertEquals(1L,trips.list(null,null,null,null,null,TrangThaiChuyen.DANG_MO_BAN,1,20).get("totalItems"));
        assertEquals(1L,trips.list(null,null,null,null,null,TrangThaiChuyen.NHAP,1,20).get("totalItems"));
    }
    int openTogether(String code,CountDownLatch ready,CountDownLatch start)throws InterruptedException {
        ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));try{trips.open(code);return 1;}catch(DomainException e){assertEquals(409,e.status());return 0;}
    }
    @Test void draftTimeUpdatesRecheckVehicleAndEmployeeSchedulesAndRollback() {
        ready("C1");trips.open("C1");trips.create(input("C2","RETURN","X",LocalTime.of(16,0)));
        assertEquals(409,assertThrows(DomainException.class,()->trips.update("C2",input("C2","RETURN","X",LocalTime.of(10,0)))).status());
        assertEquals(LocalTime.of(16,0),trips.get("C2").get("gioKhoiHanh"));
        trips.create(input("C3","RETURN",null,LocalTime.of(16,0)));
        assignments.save(null,new AssignmentInputs.PhanCong("OTHER","C3","C1E1",VaiTroChuyen.TAI_XE_CHINH,null,true));
        assertEquals(409,assertThrows(DomainException.class,()->trips.update("C3",input("C3","RETURN",null,LocalTime.of(14,0)))).status());
        assertEquals(LocalTime.of(16,0),trips.get("C3").get("gioKhoiHanh"));
    }
    @Test void publishedTicketAndHoldHistoryCannotBeDeletedCancelledOrMoved() {
        ready("C");trips.open("C");String seat=vehicles.seats("X",false).getFirst().get("maGhe").toString();
        tx(em->{var order=new DonDatVe();order.maDon="O";order.nguoiDat=em.find(NguoiDung.class,"KH");order.ngayDat=DAY.atStartOfDay();order.khachDatTen="Customer";order.khachDatSoDienThoai="0901234567";em.persist(order);
            var ticket=new VeXe();ticket.maVe="V";ticket.donDatVe=order;ticket.chuyenXe=em.find(ChuyenXe.class,"C");ticket.ghe=em.find(Ghe.class,seat);ticket.hoTenKhachHang="Customer";ticket.soDienThoaiKhachHang="0901234567";ticket.giaVe=BigDecimal.TEN;em.persist(ticket);
            var hold=new GiuCho();hold.maGiuCho="H";hold.chuyenXe=ticket.chuyenXe;hold.ghe=ticket.ghe;hold.donDatVe=order;hold.ngayDat=DAY.atStartOfDay();hold.thoiDiemBatDau=hold.ngayDat;hold.thoiDiemHetHan=hold.ngayDat.plusMinutes(15);em.persist(hold);});
        assertThrows(DomainException.class,()->trips.cancel("C"));assertThrows(DomainException.class,()->trips.delete("C"));
        assertThrows(DomainException.class,()->trips.update("C",input("C","RETURN","Y",LocalTime.NOON)));
        assertThrows(DomainException.class,()->vehicles.updateSeat("X",seat,new VehicleInputs.Ghe("RENAMED",1,TrangThaiGhe.HOAT_DONG)));
        tx(em->{assertEquals(TrangThaiVe.DA_XUAT_VE,em.find(VeXe.class,"V").trangThai);assertEquals("X",em.find(ChuyenXe.class,"C").xe.maXe);assertNotNull(em.find(GiuCho.class,"H"));});
    }
    @Test void legacyScheduledRowsRemainUnpublishedAndDoNotReserveSaleSchedule() {
        ready("C");new RouteTripService(factory).createChuyenXe(new Inputs.ChuyenXe("OLD","R",DAY.plusDays(1),LocalTime.of(8,0),"X"));
        assertEquals(TrangThaiChuyen.CHUA_KHOI_HANH,trips.get("OLD").get("trangThai"));
        assertTrue(new RouteTripService(factory).searchChuyenXe("S","D",DAY.plusDays(1)).isEmpty());
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.open("C").get("trangThai"));
        assertEquals(TrangThaiChuyen.CHUA_KHOI_HANH,trips.get("OLD").get("trangThai"));
    }
    @Test void publishedTripsLosingCrewDisappearAndCannotAcceptDirectBooking() {
        ready("C");trips.open("C");tx(em->em.remove(em.find(PhanCongChuyenXe.class,"CP3")));
        assertTrue(new RouteTripService(factory).searchChuyenXe("S","D",DAY).isEmpty());
        tx(em->assertThrows(DomainException.class,()->AccessControl.saleAllowed(em,"C")));
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.get("C").get("trangThai"));
    }
    @Test void authenticatedScopeIsEnforcedForManagementNotClientIds() throws Exception {
        trips.create(input("A1","R","X",LocalTime.NOON));trips.create(input("B1","FOREIGN","Y",LocalTime.NOON));
        context("NX","GET","/api/quan-ly/chuyen-xe",null);assertEquals(1L,trips.list(null,null,null,null,null,null,1,20).get("totalItems"));
        assertEquals(403,assertThrows(DomainException.class,()->trips.list("B",null,null,null,null,null,1,20)).status());
        context("NX","POST","/api/quan-ly/chuyen-xe","{\"maChuyen\":\"A2\",\"maTuyen\":\"R\",\"maXe\":\"X\"}");
        assertEquals("A",trips.create(input("A2","R","X",LocalTime.NOON)).get("maNhaXe"));
        context("NX","PUT","/api/quan-ly/chuyen-xe/B1","{}");assertEquals(403,assertThrows(DomainException.class,()->trips.update("B1",input("B1","FOREIGN","Y",LocalTime.NOON))).status());
        context("KH","POST","/api/quan-ly/chuyen-xe/A1/mo-ban",null);assertEquals(403,assertThrows(DomainException.class,()->trips.open("A1")).status());
        RequestIdentity.set(new RequestIdentity.Context(null,"GET","/api/quan-ly/chuyen-xe",null));assertEquals(401,assertThrows(DomainException.class,()->trips.get("A1")).status());
    }

    @Test void lifecycleRejectsInvalidStatesAndDoesNotDuplicateHistory() throws Exception {
        ready("C");trips.open("C");
        context("AD","POST","/api/quan-ly/chuyen-xe/C/bat-dau",null);
        assertEquals(TrangThaiChuyen.DANG_CHAY,trips.transition("C",TrangThaiChuyen.DANG_CHAY,null).get("trangThai"));
        assertEquals(409,assertThrows(DomainException.class,()->trips.transition("C",TrangThaiChuyen.DANG_CHAY,null)).status());
        context("AD","POST","/api/quan-ly/chuyen-xe/C/hoan-thanh",null);
        assertEquals(TrangThaiChuyen.HOAN_TAT,trips.transition("C",TrangThaiChuyen.HOAN_TAT,null).get("trangThai"));
        assertEquals(409,assertThrows(DomainException.class,()->trips.transition("C",TrangThaiChuyen.HOAN_TAT,null)).status());
        tx(em->assertEquals(2L,((Number)em.createNativeQuery("select count(*) from trip_lifecycle_history").getSingleResult()).longValue()));
    }
    @Test void lifecycleRechecksVehicleAndRejectsCustomer() throws Exception {
        ready("C");trips.open("C");
        context("KH","POST","/api/chuyen-xe/C/bat-dau",null);
        assertEquals(403,assertThrows(DomainException.class,()->trips.transition("C",TrangThaiChuyen.DANG_CHAY,null)).status());
        RequestIdentity.clear();tx(em->em.find(Xe.class,"X").trangThai=TrangThaiXe.BAO_TRI);
        assertEquals(409,assertThrows(DomainException.class,()->trips.transition("C",TrangThaiChuyen.DANG_CHAY,null)).status());
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.get("C").get("trangThai"));
    }
    @Test void cancellingReleasesHoldsWithoutAnOrderAndRequiresReason() {
        ready("C");trips.open("C");
        tx(em->{var h=new GiuCho();h.maGiuCho="H";h.chuyenXe=em.find(ChuyenXe.class,"C");h.ghe=em.find(Xe.class,"X").danhSachGhe.getFirst();h.ngayDat=DAY.minusDays(1).atStartOfDay();h.thoiDiemBatDau=h.ngayDat;h.thoiDiemHetHan=DAY.atTime(7,0);em.persist(h);});
        assertEquals(400,assertThrows(DomainException.class,()->trips.cancel("C"," ")).status());
        assertEquals(TrangThaiChuyen.DA_HUY,trips.cancel("C","Xe gặp sự cố").get("trangThai"));
        tx(em->assertEquals(TrangThaiGiuCho.DA_HUY,em.find(GiuCho.class,"H").trangThai));
        assertEquals(409,assertThrows(DomainException.class,()->trips.cancel("C","Xe gặp sự cố")).status());
        assertEquals(409,assertThrows(DomainException.class,()->trips.transition("C",TrangThaiChuyen.DANG_CHAY,null)).status());
        assertEquals(409,assertThrows(DomainException.class,()->trips.open("C")).status());
    }
    @Test void assignedDriverCanOperateButCannotCancelOrOperateAnotherTrip() {
        ready("C");trips.open("C");
        trips.create(input("OTHER","RETURN",null,LocalTime.of(16,0)));
        tx(em->{var u=new NguoiDung();u.maNguoiDung="DRIVER";u.hoTen="Tài xế";u.email="driver@test.local";u.soDienThoai="0999000011";u.matKhau="test";u.loaiTaiKhoan=LoaiTaiKhoan.NHAN_VIEN;u.trangThai=TrangThaiTaiKhoan.HOAT_DONG;u.nhanVien=em.find(NhanVien.class,"CE1");em.persist(u);
            var auth=new AuthSession();auth.tokenHash="DRIVER";auth.csrfToken="DRIVER";auth.nguoiDung=u;auth.taoLuc=Instant.now();auth.hetHan=Instant.now().plusSeconds(3600);em.persist(auth);});
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser("DRIVER",LoaiTaiKhoan.NHAN_VIEN,null,"CE1","DRIVER"),"POST","/api/chuyen-xe/OTHER/bat-dau",null));
        assertEquals(403,assertThrows(DomainException.class,()->trips.transition("OTHER",TrangThaiChuyen.DANG_CHAY,null)).status());
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser("DRIVER",LoaiTaiKhoan.NHAN_VIEN,null,"CE1","DRIVER"),"POST","/api/chuyen-xe/C/bat-dau",null));
        assertEquals(TrangThaiChuyen.DANG_CHAY,trips.transition("C",TrangThaiChuyen.DANG_CHAY,null).get("trangThai"));
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser("DRIVER",LoaiTaiKhoan.NHAN_VIEN,null,"CE1","DRIVER"),"POST","/api/chuyen-xe/C/huy",null));
        assertEquals(403,assertThrows(DomainException.class,()->trips.cancel("C","Sự cố")).status());
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser("DRIVER",LoaiTaiKhoan.NHAN_VIEN,null,"CE1","DRIVER"),"POST","/api/chuyen-xe/C/hoan-thanh",null));
        assertEquals(TrangThaiChuyen.HOAN_TAT,trips.transition("C",TrangThaiChuyen.HOAN_TAT,null).get("trangThai"));
    }
    @Test void completingDoesNotMarkUninspectedPassengersAsBoarded() {
        ready("C");trips.open("C");
        tx(em->{var o=new DonDatVe();o.maDon="O";o.nguoiDat=em.find(NguoiDung.class,"KH");o.ngayDat=DAY.atStartOfDay();o.khachDatTen="Khách";o.khachDatSoDienThoai="0999000012";em.persist(o);
            var seats=em.find(Xe.class,"X").danhSachGhe;
            for(int n=0;n<2;n++){var v=new VeXe();v.maVe="V"+n;v.donDatVe=o;v.chuyenXe=em.find(ChuyenXe.class,"C");v.ghe=seats.get(n);v.hoTenKhachHang="Khách";v.soDienThoaiKhachHang="0999000012";v.giaVe=BigDecimal.TEN;v.trangThai=n==0?TrangThaiVe.DA_LEN_XE:TrangThaiVe.DA_XUAT_VE;em.persist(v);}});
        trips.transition("C",TrangThaiChuyen.DANG_CHAY,null);trips.transition("C",TrangThaiChuyen.HOAN_TAT,null);
        tx(em->{assertEquals(TrangThaiVe.DA_LEN_XE,em.find(VeXe.class,"V0").trangThai);assertEquals(TrangThaiVe.DA_XUAT_VE,em.find(VeXe.class,"V1").trangThai);});
    }
    void context(String id,String method,String path,String body)throws Exception {
        var role=id.equals("NX")?LoaiTaiKhoan.NHA_XE:id.equals("KH")?LoaiTaiKhoan.KHACH_HANG:LoaiTaiKhoan.ADMIN;
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser(id,role,id.equals("NX")?"A":null,null,id),method,path,body==null?null:new ObjectMapper().readTree(body)));
    }
}
