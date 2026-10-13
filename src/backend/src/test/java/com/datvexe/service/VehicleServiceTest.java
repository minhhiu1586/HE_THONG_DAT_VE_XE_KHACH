package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.VehicleInputs;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VehicleServiceTest {
    private EntityManagerFactory factory;
    private VehicleService service;
    @BeforeEach void setup() {
        factory=Persistence.createEntityManagerFactory("datvexe",Map.of(
            "jakarta.persistence.jdbc.driver","org.h2.Driver",
            "jakarta.persistence.jdbc.url","jdbc:h2:mem:vehicle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "jakarta.persistence.jdbc.user","sa","jakarta.persistence.jdbc.password","",
            "hibernate.hbm2ddl.auto","create-drop"));
        service=new VehicleService(factory);
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin(); NhaXe owner=new NhaXe(); owner.maNhaXe="NX"; owner.tenNhaXe="Demo";
            em.persist(owner); em.getTransaction().commit();
        }
        service.saveType(null,new VehicleInputs.LoaiXe("L","Giường nằm",6,2,null));
    }
    @AfterEach void close() { factory.close(); }
    private VehicleInputs.Xe vehicle(String id,String plate) {
        return new VehicleInputs.Xe(id,plate,"L","NX",2024,LocalDate.of(2026,1,1),LocalDate.of(2027,1,1),TrangThaiXe.SAN_SANG);
    }
    @Test void createsVehicleAndSeatsAndProtectsUsedType() {
        service.saveVehicle(null,vehicle("X","51B-12345"));
        var seats=service.seats("X",false);
        assertEquals(6,seats.size()); assertEquals(3,seats.stream().filter(g->g.get("tang").equals(2)).count());
        assertEquals(409,assertThrows(DomainException.class,()->service.delete("loai-xe","L")).status());
        assertThrows(DomainException.class,()->service.seats("X",true));
        service.updateSeat("X",(String)seats.get(0).get("maGhe"),new VehicleInputs.Ghe("A01",1,TrangThaiGhe.BAO_TRI));
        assertTrue(service.seats("X",false).stream().anyMatch(g->g.get("viTri").equals("A01")));
        service.delete("xe","X"); service.delete("loai-xe","L");
        assertTrue(service.list("xe").isEmpty());
    }
    @Test void duplicatePlateRollsBackVehicleAndGeneratedSeats() {
        service.saveVehicle(null,vehicle("X","51B-12345"));
        assertThrows(PersistenceException.class,()->service.saveVehicle(null,vehicle("Y","51B-12345")));
        assertEquals(1,service.list("xe").size());
        assertEquals(404,assertThrows(DomainException.class,()->service.get("xe","Y")).status());
    }
    @Test void seatCrudKeepsCapacityAndRollsBackDuplicatePositions() {
        service.saveVehicle(null,vehicle("X","51B-TEST"));
        var seats=service.seats("X",false);
        String id=(String)seats.get(0).get("maGhe");
        assertThrows(DomainException.class,()->service.createSeat("X",new VehicleInputs.Ghe("EXTRA",1,null)));
        service.deleteSeat("X",id);
        assertEquals(5,service.seats("X",false).size());
        assertThrows(DomainException.class,()->service.getSeat("X",id));
        assertThrows(PersistenceException.class,()->service.createSeat("X",new VehicleInputs.Ghe("G002",1,null)));
        assertEquals(5,service.seats("X",false).size());
        var created=service.createSeat("X",new VehicleInputs.Ghe("A01",2,TrangThaiGhe.HOAT_DONG));
        assertEquals("A01",service.getSeat("X",(String)created.get("maGhe")).get("viTri"));
        assertEquals(6,service.seats("X",false).size());
    }
    @Test void rejectsInvalidInspectionAndSeatStructure() {
        assertThrows(DomainException.class,()->service.saveVehicle(null,new VehicleInputs.Xe("X","51B","L","NX",2024,LocalDate.of(2027,1,1),LocalDate.of(2026,1,1),null)));
        service.saveVehicle(null,vehicle("X","51B"));
        assertThrows(DomainException.class,()->service.saveType("L",new VehicleInputs.LoaiXe("L","Test",10,2,null)));
        assertEquals(6,service.get("loai-xe","L").get("soChoNgoi"));
    }
    @Test void assignmentsRejectExpiredLicenseWrongRoleAndOverlappingTrips() {
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();
            TinhThanh city=new TinhThanh(); city.maTinhThanh="T"; city.tenTinhThanh="City"; em.persist(city);
            BenXe a=new BenXe(); a.maBenXe="A"; a.tenBenXe="A"; a.diaChi="A"; a.tinhThanh=city; em.persist(a);
            BenXe b=new BenXe(); b.maBenXe="B"; b.tenBenXe="B"; b.diaChi="B"; b.tinhThanh=city; em.persist(b);
            TuyenXe route=new TuyenXe(); route.maTuyen="R"; route.tenTuyen="Route"; route.tramKhoiHanh=a; route.tramDen=b;
            route.giaCoBan=java.math.BigDecimal.TEN; route.nhaXe=em.find(NhaXe.class,"NX"); em.persist(route);
            for(String id:new String[]{"C1","C2"}) {
                ChuyenXe c=new ChuyenXe(); c.maChuyen=id; c.tuyenXe=route;
                c.ngayKhoiHanh=LocalDate.of(2030,1,1); c.gioKhoiHanh=java.time.LocalTime.of(8,0);
                c.gioDenDuKien=c.ngayKhoiHanh.atTime(12,0); em.persist(c);
            }
            NhanVien n=new NhanVien(); n.maNhanVien="N"; n.hoTen="Driver"; n.email="driver@test.local";
            n.soDienThoai="0901234567"; n.nhaXe=route.nhaXe; n.loaiNhanVien=LoaiNhanVien.TAI_XE;
            n.soBangLai="DEMO-LICENSE"; n.ngayHetHanBangLai=LocalDate.of(2029,1,1); em.persist(n); em.getTransaction().commit();
        }
        var input=new VehicleInputs.PhanCong("P1","C1","N",VaiTroChuyen.TAI_XE_CHINH);
        assertThrows(DomainException.class,()->service.saveAssignment(null,input));
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin(); em.find(NhanVien.class,"N").ngayHetHanBangLai=LocalDate.of(2031,1,1); em.getTransaction().commit();
        }
        assertThrows(DomainException.class,()->service.saveAssignment(null,new VehicleInputs.PhanCong("P1","C1","N",VaiTroChuyen.PHU_XE)));
        service.saveAssignment(null,input);
        assertEquals("N",service.saveAssignment("P1",new VehicleInputs.PhanCong("P1","C1","N",VaiTroChuyen.TAI_XE_PHU,"Cập nhật vai trò")).get("maNhanVien"));
        assertEquals("Cập nhật vai trò",service.get("phan-cong","P1").get("ghiChu"));
        assertThrows(DomainException.class,()->service.saveAssignment(null,new VehicleInputs.PhanCong("P2","C2","N",VaiTroChuyen.TAI_XE_CHINH)));
        assertEquals(1,service.list("phan-cong").size());
        service.delete("phan-cong","P1"); assertTrue(service.list("phan-cong").isEmpty());
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();
            NhaXe otherOwner=new NhaXe(); otherOwner.maNhaXe="NX2"; otherOwner.tenNhaXe="Demo 2";
            em.persist(otherOwner);
            em.find(NhanVien.class,"N").nhaXe=otherOwner;
            em.getTransaction().commit();
        }
        assertEquals(409,assertThrows(DomainException.class,()->service.saveAssignment(null,input)).status());
    }
}
