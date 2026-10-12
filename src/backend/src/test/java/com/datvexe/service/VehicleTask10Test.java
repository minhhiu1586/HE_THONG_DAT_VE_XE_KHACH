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
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class VehicleTask10Test {
    EntityManagerFactory factory; VehicleService vehicles; RouteTripService trips;
    static final LocalDate DAY=LocalDate.of(2030,1,1);
    @BeforeEach void setup() {
        factory=Persistence.createEntityManagerFactory("datvexe",Map.of("jakarta.persistence.jdbc.driver","org.h2.Driver",
            "jakarta.persistence.jdbc.url","jdbc:h2:mem:t10;MODE=PostgreSQL","jakarta.persistence.jdbc.user","sa",
            "jakarta.persistence.jdbc.password","","hibernate.hbm2ddl.auto","create-drop"));
        vehicles=new VehicleService(factory);trips=new RouteTripService(factory);
        tx(em -> {
            for(String id:List.of("A","B")) {var n=new NhaXe();n.maNhaXe=id;n.tenNhaXe=id;em.persist(n);}
            for(String id:List.of("NX","AD","KH")) {
                var n=new NguoiDung();n.maNguoiDung=id;n.hoTen=id;n.email=id+"@test.local";n.soDienThoai=id;n.matKhau="not-a-login";
                n.trangThai=TrangThaiTaiKhoan.HOAT_DONG;n.loaiTaiKhoan=id.equals("NX")?LoaiTaiKhoan.NHA_XE:id.equals("AD")?LoaiTaiKhoan.ADMIN:LoaiTaiKhoan.KHACH_HANG;
                if(id.equals("NX"))n.nhaXe=em.find(NhaXe.class,"A");em.persist(n);
                var session=new AuthSession();session.tokenHash=id;session.nguoiDung=n;session.csrfToken=id;session.taoLuc=Instant.now();session.hetHan=Instant.now().plusSeconds(3600);em.persist(session);
            }
        });
        vehicles.saveType(null,new VehicleInputs.LoaiXe("L","Hai tầng",4,2,null));
        trips.createTinhThanh(new Inputs.TinhThanh("CITY","City",null));
        trips.createBenXe(new Inputs.BenXe("S","S","S",null,"CITY",null));
        trips.createBenXe(new Inputs.BenXe("D","D","D",null,"CITY",null));
        trips.createTuyenXe(new Inputs.TuyenXe("R","Route","S","D",LocalTime.of(8,0),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.TEN,null,TrangThaiTuyenXe.DANG_KHAI_THAC,"A"));
    }
    @AfterEach void close() {RequestIdentity.clear();factory.close();}
    void tx(Consumer<EntityManager> task) {try(var em=factory.createEntityManager()){em.getTransaction().begin();task.accept(em);em.getTransaction().commit();}}
    VehicleInputs.Xe input(String id,String plate,String owner,LocalDate expiry,TrangThaiXe state) {
        return new VehicleInputs.Xe(id,plate,"L",owner,2024,LocalDate.of(2020,1,1),expiry,state,BigDecimal.valueOf(60),BigDecimal.valueOf(100),4);
    }
    void vehicle(String id,String owner) {vehicles.saveVehicle(null,input(id,"51B-"+id,owner,LocalDate.of(2100,1,1),TrangThaiXe.SAN_SANG));}
    void trip(String id,String vehicle,LocalDate day) {trips.createChuyenXe(new Inputs.ChuyenXe(id,"R",day,LocalTime.of(8,0),vehicle));}
    List<VehicleInputs.ViTriGhe> layout(String id) {
        return vehicles.seats(id,false).stream().map(g -> new VehicleInputs.ViTriGhe((String)g.get("maGhe"),(String)g.get("viTri"),(Integer)g.get("tang"),TrangThaiGhe.valueOf(g.get("trangThai").toString()))).toList();
    }
    void context(String user,String method,String path,String body) throws Exception {
        var role=user.equals("NX")?LoaiTaiKhoan.NHA_XE:user.equals("AD")?LoaiTaiKhoan.ADMIN:LoaiTaiKhoan.KHACH_HANG;
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser(user,role,user.equals("NX")?"A":null,null,user),method,path,body==null?null:new ObjectMapper().readTree(body)));
    }
    void ticket(String tripId,String seatId) {tx(em -> {
        var order=new DonDatVe();order.maDon="O";order.nguoiDat=em.find(NguoiDung.class,"KH");order.ngayDat=DAY.atStartOfDay();order.khachDatTen="Customer";order.khachDatSoDienThoai="0901234567";em.persist(order);
        var ticket=new VeXe();ticket.maVe="V";ticket.donDatVe=order;ticket.chuyenXe=em.find(ChuyenXe.class,tripId);ticket.ghe=em.find(Ghe.class,seatId);
        ticket.hoTenKhachHang="Customer";ticket.soDienThoaiKhachHang="0901234567";ticket.giaVe=BigDecimal.TEN;em.persist(ticket);
    });}
    @Test void typeAndVehicleValidationUniquePlatesAndFilters() {
        assertThrows(DomainException.class,()->vehicles.saveType(null,new VehicleInputs.LoaiXe("L","Duplicate",4,2,null)));
        assertThrows(DomainException.class,()->vehicles.saveType(null,new VehicleInputs.LoaiXe("BAD","Zero",0,1,null)));
        vehicle("X1","A");vehicle("X2","B");
        assertEquals(409,assertThrows(DomainException.class,()->vehicles.saveVehicle(null,input("X3"," 51b-X1 ","A",DAY,TrangThaiXe.SAN_SANG))).status());
        assertEquals(404,assertThrows(DomainException.class,()->vehicles.saveVehicle(null,input("X3","51B-X3","MISSING",DAY,TrangThaiXe.SAN_SANG))).status());
        var invalid=new VehicleInputs.Xe("X3","51B-X3","L","A",2024,DAY,DAY,TrangThaiXe.SAN_SANG,null,new BigDecimal("-1"),4);
        assertEquals(400,assertThrows(DomainException.class,()->vehicles.saveVehicle(null,invalid)).status());
        assertEquals(400,assertThrows(DomainException.class,()->vehicles.saveVehicle(null,new VehicleInputs.Xe("X3","51B-X3","L","A",2024,DAY,DAY,null,null,null,0))).status());
        assertEquals(1,vehicles.filtered("xe","51b-x1","L","A","SAN_SANG",null).size());
        assertEquals(1,vehicles.filtered("loai-xe","hai tầng",null,null,null,null).size());
        assertEquals(409,assertThrows(DomainException.class,()->vehicles.delete("loai-xe","L")).status());
        assertEquals(4,vehicles.seats("X1",false).size());
        assertEquals(true,vehicles.get("xe","X1").get("soDoHopLe"));
    }
    @Test void inspectionMustCoverWholeTripIncludingArrivalBoundary() {
        vehicles.saveVehicle(null,input("X1","51B-X1","A",DAY.minusDays(1),TrangThaiXe.SAN_SANG));
        assertEquals(409,assertThrows(DomainException.class,()->trip("C","X1",DAY)).status());
        vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY,TrangThaiXe.SAN_SANG));
        assertEquals(409,assertThrows(DomainException.class,()->trips.createChuyenXe(new Inputs.ChuyenXe("NIGHT","R",DAY,LocalTime.of(23,30),"X1"))).status());
        trip("C","X1",DAY);
        assertEquals(409,assertThrows(DomainException.class,()->vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY.minusDays(1),TrangThaiXe.SAN_SANG))).status());
        assertEquals("X1",trips.getChuyenXe("C").get("maXe"));
        assertEquals(TrangThaiChuyen.CHUA_KHOI_HANH,trips.getChuyenXe("C").get("trangThai"));
    }
    @Test void maintenanceAndIncompleteLegacyMapCannotBeAssigned() {
        vehicles.saveVehicle(null,input("X1","51B-X1","A",DAY,TrangThaiXe.BAO_TRI));
        assertThrows(DomainException.class,()->trip("C","X1",DAY));
        vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY,TrangThaiXe.SAN_SANG));
        tx(em -> {var x=em.find(Xe.class,"X1");var g=x.danhSachGhe.getFirst();x.danhSachGhe.remove(g);em.remove(g);});
        assertEquals(false,vehicles.get("xe","X1").get("soDoHopLe"));
        assertThrows(DomainException.class,()->trip("C","X1",DAY));
        tx(em -> em.find(Xe.class,"X1").soChoNgoi=3);
        assertThrows(DomainException.class,()->vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY,TrangThaiXe.SAN_SANG)));
        assertEquals(3,vehicles.get("xe","X1").get("soChoNgoi"));
    }
    @Test void layoutRejectsWrongCountsDuplicatesForeignIdsAndInvalidFloorsWithoutPartialSave() {
        vehicle("X1","A");vehicle("X2","A");var original=layout("X1");
        assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(original.subList(0,3))));
        var excess=new ArrayList<>(original);excess.add(new VehicleInputs.ViTriGhe(null,"EXTRA",1,TrangThaiGhe.HOAT_DONG));
        assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(excess)));
        var invalid=new ArrayList<>(original);invalid.set(3,new VehicleInputs.ViTriGhe(original.get(3).maGhe(),"CHANGED",3,TrangThaiGhe.HOAT_DONG));
        assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(invalid)));
        invalid.set(3,new VehicleInputs.ViTriGhe(original.get(0).maGhe(),"NEW",1,TrangThaiGhe.HOAT_DONG));
        assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(invalid)));
        invalid.set(3,new VehicleInputs.ViTriGhe(original.get(3).maGhe(),original.get(0).viTri().toLowerCase(),1,TrangThaiGhe.HOAT_DONG));
        assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(invalid)));
        invalid.set(3,layout("X2").getFirst());assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(invalid)));
        assertEquals(original,layout("X1"));
    }
    @Test void twoVehiclesMayUseSameLocalCodeAndLabelSwapIsAtomic() {
        vehicle("X1","A");vehicle("X2","A");
        for(String id:List.of("X1","X2")) {
            var items=new ArrayList<>(layout(id));var first=items.getFirst();items.set(0,new VehicleInputs.ViTriGhe(first.maGhe(),"A1",1,TrangThaiGhe.HOAT_DONG));
            vehicles.saveLayout(id,new VehicleInputs.SoDoGhe(items));
        }
        var items=new ArrayList<>(layout("X1"));var a=items.get(0);var b=items.get(1);
        items.set(0,new VehicleInputs.ViTriGhe(a.maGhe(),b.viTri(),a.tang(),a.trangThai()));items.set(1,new VehicleInputs.ViTriGhe(b.maGhe(),a.viTri(),b.tang(),b.trangThai()));
        vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(items));
        assertEquals(b.viTri(),vehicles.getSeat("X1",a.maGhe()).get("viTri"));
    }
    @Test void physicalMaintenanceAndTripOccupancyAreIndependent() {
        vehicle("X1","A");var items=new ArrayList<>(layout("X1"));var maintenance=items.get(3);
        items.set(3,new VehicleInputs.ViTriGhe(maintenance.maGhe(),maintenance.viTri(),maintenance.tang(),TrangThaiGhe.BAO_TRI));
        vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(items));
        trip("C1","X1",DAY);trip("C2","X1",DAY.plusDays(1));String seat=layout("X1").getFirst().maGhe();ticket("C1",seat);
        tx(em -> {
            assertEquals("DA_BAN",VehicleRules.availability(em,em.find(ChuyenXe.class,"C1"),em.find(Ghe.class,seat),DAY.atStartOfDay()));
            assertEquals("TRONG",VehicleRules.availability(em,em.find(ChuyenXe.class,"C2"),em.find(Ghe.class,seat),DAY.atStartOfDay()));
            assertThrows(DomainException.class,()->VehicleRules.seatSellable(em,"C2",maintenance.maGhe(),DAY.atStartOfDay()));
            assertNotNull(VehicleRules.seatSellable(em,"C2",seat,DAY.atStartOfDay()));
        });
        assertEquals(TrangThaiGhe.HOAT_DONG,vehicles.getSeat("X1",seat).get("trangThai"));
    }
    @Test void physicalPositionsAreNaturallySortedWithinEachFloor() {
        vehicle("X1","A");var old=layout("X1");
        var entries=List.of(new VehicleInputs.ViTriGhe(old.get(0).maGhe(),"A10",1,TrangThaiGhe.HOAT_DONG),
            new VehicleInputs.ViTriGhe(old.get(1).maGhe(),"A2",1,TrangThaiGhe.HOAT_DONG),
            new VehicleInputs.ViTriGhe(old.get(2).maGhe(),"B1",2,TrangThaiGhe.HOAT_DONG),
            new VehicleInputs.ViTriGhe(old.get(3).maGhe(),"B2",2,TrangThaiGhe.HOAT_DONG));
        vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(entries));
        assertEquals(List.of("A2","A10","B1","B2"),vehicles.seats("X1",false).stream().map(g -> g.get("viTri")).toList());
    }
    @Test void heldSeatIsUnavailableOnlyUntilExpiryOnItsTrip() {
        vehicle("X1","A");vehicle("X2","A");trip("C1","X1",DAY);trip("C2","X1",DAY.plusDays(1));String id=layout("X1").getFirst().maGhe();
        tx(em -> {var h=new GiuCho();h.maGiuCho="H";h.ghe=em.find(Ghe.class,id);h.chuyenXe=em.find(ChuyenXe.class,"C1");h.ngayDat=DAY.atStartOfDay();h.thoiDiemBatDau=h.ngayDat;h.thoiDiemHetHan=h.ngayDat.plusMinutes(15);em.persist(h);});
        tx(em -> {var c=em.find(ChuyenXe.class,"C1");var g=em.find(Ghe.class,id);
            assertEquals("DANG_GIU",VehicleRules.availability(em,c,g,DAY.atStartOfDay()));
            assertEquals("TRONG",VehicleRules.availability(em,c,g,DAY.atTime(0,15)));
            assertEquals("TRONG",VehicleRules.availability(em,em.find(ChuyenXe.class,"C2"),g,DAY.atStartOfDay()));
        });
        assertEquals(409,assertThrows(DomainException.class,()->trips.assignVehicle("C1","X2")).status());
    }
    @Test void soldTripCannotChangeVehicleOrSeatStructureOrLoseInspectionHistory() {
        vehicle("X1","A");vehicle("X2","A");trip("C1","X1",DAY);var items=layout("X1");ticket("C1",items.getFirst().maGhe());
        assertEquals(409,assertThrows(DomainException.class,()->trips.assignVehicle("C1","X2")).status());
        assertThrows(DomainException.class,()->vehicles.delete("xe","X1"));
        assertThrows(DomainException.class,()->vehicles.updateSeat("X1",items.getFirst().maGhe(),new VehicleInputs.Ghe("RENAMED",1,TrangThaiGhe.HOAT_DONG)));
        var changed=new ArrayList<>(items);changed.set(0,new VehicleInputs.ViTriGhe(items.getFirst().maGhe(),"RENAMED",2,TrangThaiGhe.HOAT_DONG));
        assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(changed)));
        assertThrows(DomainException.class,()->vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY.minusDays(1),TrangThaiXe.SAN_SANG)));
        assertEquals(items,layout("X1"));assertEquals("X1",trips.getChuyenXe("C1").get("maXe"));
        tx(em -> assertEquals(TrangThaiVe.DA_XUAT_VE,em.find(VeXe.class,"V").trangThai));
    }
    @Test void maintenanceCanEndWithoutChangingPhysicalSeatIdentity() {
        vehicles.saveVehicle(null,input("X1","51B-X1","A",DAY,TrangThaiXe.BAO_TRI));
        var original=layout("X1");var first=original.getFirst();
        vehicles.updateSeat("X1",first.maGhe(),new VehicleInputs.Ghe(first.viTri(),first.tang(),TrangThaiGhe.BAO_TRI));
        vehicles.updateSeat("X1",first.maGhe(),new VehicleInputs.Ghe(first.viTri(),first.tang(),TrangThaiGhe.HOAT_DONG));
        vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY,TrangThaiXe.SAN_SANG));
        trip("C1","X1",DAY);
        assertEquals(original,layout("X1"));
        tx(em -> assertNotNull(VehicleRules.seatSellable(em,"C1",first.maGhe(),DAY.atStartOfDay())));
    }
    @Test void registrationUpdateAfterCompletedTripPreservesTicketsAndSeatReferences() {
        vehicle("X1","A");trip("C1","X1",DAY);var original=layout("X1");ticket("C1",original.getFirst().maGhe());
        tx(em -> em.find(ChuyenXe.class,"C1").trangThai=TrangThaiChuyen.HOAN_TAT);
        vehicles.saveVehicle("X1",input("X1","51B-X1","A",DAY.minusDays(1),TrangThaiXe.BAO_TRI));
        assertEquals(original,layout("X1"));
        tx(em -> {
            var ticket=em.find(VeXe.class,"V");assertEquals(TrangThaiVe.DA_XUAT_VE,ticket.trangThai);
            assertEquals(original.getFirst().maGhe(),ticket.ghe.maGhe);
            assertEquals("X1",ticket.chuyenXe.xe.maXe);
        });
        var changed=new ArrayList<>(original);changed.set(0,new VehicleInputs.ViTriGhe(original.getFirst().maGhe(),"RENAMED",1,TrangThaiGhe.HOAT_DONG));
        assertEquals(409,assertThrows(DomainException.class,()->vehicles.saveLayout("X1",new VehicleInputs.SoDoGhe(changed))).status());
        assertEquals(original,layout("X1"));
    }
    @Test void scopeAndCatalogRolesUseAuthenticatedOperatorNotClientIds() throws Exception {
        vehicle("X1","A");vehicle("X2","B");
        context("NX","GET","/api/xe",null);assertEquals(1,vehicles.filtered("xe",null,null,null,null,null).size());
        context("NX","GET","/api/loai-xe",null);assertEquals(1,vehicles.list("loai-xe").size());
        context("NX","POST","/api/loai-xe","{}");assertEquals(403,assertThrows(AccountException.class,()->vehicles.saveType(null,new VehicleInputs.LoaiXe("BAD","B",1,1,null))).status());
        context("NX","PUT","/api/xe/X2/so-do-ghe","{}");assertEquals(403,assertThrows(AccountException.class,()->vehicles.saveLayout("X2",new VehicleInputs.SoDoGhe(List.of()))).status());
        context("NX","POST","/api/xe","{\"maXe\":\"X3\",\"maNhaXe\":\"B\"}");assertEquals(403,assertThrows(AccountException.class,()->vehicles.saveVehicle(null,input("X3","51B-X3","B",DAY,TrangThaiXe.SAN_SANG))).status());
        context("NX","POST","/api/xe","{\"maXe\":\"X3\"}");assertEquals("A",vehicles.saveVehicle(null,input("X3","51B-X3",null,DAY,TrangThaiXe.SAN_SANG)).get("maNhaXe"));
        context("KH","GET","/api/xe",null);assertEquals(403,assertThrows(AccountException.class,()->vehicles.list("xe")).status());
        RequestIdentity.set(new RequestIdentity.Context(null,"GET","/api/xe",null));assertEquals(401,assertThrows(AccountException.class,()->vehicles.list("xe")).status());
    }
}
