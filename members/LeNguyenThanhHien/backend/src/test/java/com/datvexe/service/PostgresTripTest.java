package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL transaction/locking proof on a separately configured disposable database. */
@EnabledIfSystemProperty(named="vehicle.pg.url",matches=".+")
class PostgresTripTest {
    EntityManagerFactory factory;TripService trips;VehicleService vehicles;EmployeeService employees;String prefix;LocalDate day;
    @BeforeEach void setup() {
        factory=Persistence.createEntityManagerFactory("datvexe",Map.of("jakarta.persistence.jdbc.driver","org.postgresql.Driver",
            "jakarta.persistence.jdbc.url",System.getProperty("vehicle.pg.url"),"jakarta.persistence.jdbc.user",System.getProperty("vehicle.pg.user"),
            "jakarta.persistence.jdbc.password",System.getProperty("vehicle.pg.password",""),"hibernate.hbm2ddl.auto","validate"));
        trips=new TripService(factory);vehicles=new VehicleService(factory);employees=new EmployeeService(factory);
        prefix="T12PG_"+UUID.randomUUID().toString().substring(0,8);day=LocalDate.now(TripSaleRules.ZONE).plusDays(30);
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();var owner=new NhaXe();owner.maNhaXe=id("NX");owner.tenNhaXe="T12 test";em.persist(owner);em.getTransaction().commit();
        }
        var routes=new RouteTripService(factory);routes.createTinhThanh(new Inputs.TinhThanh(id("CITY"),"T12",null));
        for(String code:List.of("S","D"))routes.createBenXe(new Inputs.BenXe(id(code),code,code,null,id("CITY"),null));
        routes.createTuyenXe(new Inputs.TuyenXe(id("R"),"T12 route",id("S"),id("D"),LocalTime.of(8,0),BigDecimal.ONE,BigDecimal.valueOf(7),BigDecimal.valueOf(100000),null,TrangThaiTuyenXe.DANG_KHAI_THAC,id("NX")));
        vehicles.saveType(null,new VehicleInputs.LoaiXe(id("L"),"T12 type",4,2,null));
        vehicles.saveVehicle(null,new VehicleInputs.Xe(id("X"),"51B-"+prefix.replace('_','-'),id("L"),id("NX"),2024,LocalDate.of(2020,1,1),day.plusDays(365),TrangThaiXe.SAN_SANG));
    }
    String id(String suffix){return prefix+"_"+suffix;}
    void draft(String suffix){trips.create(new TripInputs.Draft(id(suffix),id("R"),id("X"),day,LocalTime.of(8,0),null,null));}
    void crew(String suffix){
        for(int i=1;i<=3;i++) {
            String code=id(suffix+"E"+i);boolean assistant=i==3;
            String phone="09"+String.format(Locale.ROOT,"%08d",Math.floorMod(code.hashCode(),100000000));
            employees.save(null,new EmployeeInputs.Employee(code,"Staff "+i,phone,code+"@example.test",null,id("NX"),assistant?LoaiNhanVien.PHU_XE:LoaiNhanVien.TAI_XE,TrangThaiNhanVien.DANG_LAM_VIEC,assistant?null:"123456789012",assistant?null:day.plusDays(730)));
            vehicles.saveAssignment(null,new VehicleInputs.PhanCong(id(suffix+"P"+i),id(suffix),code,i==1?VaiTroChuyen.TAI_XE_CHINH:i==2?VaiTroChuyen.TAI_XE_PHU:VaiTroChuyen.PHU_XE,"Test plan",true));
        }
    }
    @AfterEach void cleanup(){
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("delete from PhanCongChuyenXe p where p.chuyenXe.tuyenXe.maTuyen=:route").setParameter("route",id("R")).executeUpdate();
            em.createQuery("delete from ChuyenXe c where c.tuyenXe.maTuyen=:route").setParameter("route",id("R")).executeUpdate();
            em.createQuery("delete from Ghe g where g.xe.maXe=:vehicle").setParameter("vehicle",id("X")).executeUpdate();
            em.remove(em.find(Xe.class,id("X")));em.remove(em.find(LoaiXe.class,id("L")));
            em.createQuery("delete from NhanVien n where n.nhaXe.maNhaXe=:owner").setParameter("owner",id("NX")).executeUpdate();
            em.remove(em.find(TuyenXe.class,id("R")));for(String code:List.of("S","D"))em.remove(em.find(BenXe.class,id(code)));
            em.remove(em.find(TinhThanh.class,id("CITY")));em.remove(em.find(NhaXe.class,id("NX")));em.getTransaction().commit();
        }finally{factory.close();}
    }
    @Test void realT11AssignmentCanPublishDraftAndDuplicateOpeningIsStable(){
        draft("C1");assertFalse(trips.eligibility(id("C1")).duDieuKien());crew("C1");
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.open(id("C1")).get("trangThai"));
        assertEquals(TrangThaiChuyen.DANG_MO_BAN,trips.open(id("C1")).get("trangThai"));
        assertEquals(1,new RouteTripService(factory).searchChuyenXe(id("S"),id("D"),day).size());
    }
    @Test void conflictingSimultaneousPublishSerializesUnderDatabaseLocks()throws Exception{
        draft("C1");draft("C2");crew("C1");crew("C2");CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(ExecutorService workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->compete("C1",ready,start));var second=workers.submit(()->compete("C2",ready,start));
            try{assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();assertEquals(1,first.get(30,TimeUnit.SECONDS)+second.get(30,TimeUnit.SECONDS));}
            finally{start.countDown();}
        }
        assertEquals(1L,trips.list(id("NX"),null,null,null,null,TrangThaiChuyen.DANG_MO_BAN,1,20).get("totalItems"));
        assertEquals(1L,trips.list(id("NX"),null,null,null,null,TrangThaiChuyen.NHAP,1,20).get("totalItems"));
    }
    int compete(String suffix,CountDownLatch ready,CountDownLatch start)throws InterruptedException{
        ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));try{trips.open(id(suffix));return 1;}catch(DomainException e){assertEquals(409,e.status());return 0;}
    }
    @Test void failedPublishAndUpdateRollbackOnPostgres(){
        draft("C1");crew("C1");
        try(var em=factory.createEntityManager()){em.getTransaction().begin();em.find(NhanVien.class,id("C1E1")).ngayHetHanBangLai=day.minusDays(1);em.getTransaction().commit();}
        assertThrows(DomainException.class,()->trips.open(id("C1")));assertEquals(TrangThaiChuyen.NHAP,trips.get(id("C1")).get("trangThai"));
        assertThrows(DomainException.class,()->trips.update(id("C1"),new TripInputs.Draft(id("C1"),id("R"),id("X"),day,LocalTime.of(8,0),day.atTime(7,0),null)));
        assertEquals(day.atTime(15,0),trips.get(id("C1")).get("gioDenDuKien"));
    }
}
