package com.datvexe.service;

import com.datvexe.dto.VehicleInputs;
import com.datvexe.dto.AssignmentInputs;
import com.datvexe.domain.States.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Run only against a disposable database loaded with schema 001, 002 and demo seed. */
@EnabledIfSystemProperty(named="vehicle.pg.url", matches=".+")
class PostgresVehicleTest {
    @BeforeEach void prepareReturnRouteForNewT11ConnectionRule() {
        try(var factory=factory();var em=factory.createEntityManager()) {
            em.getTransaction().begin();var trip=em.find(com.datvexe.domain.Entities.ChuyenXe.class,"DEMO_SAU");
            var original=em.find(com.datvexe.domain.Entities.TuyenXe.class,"DEMO_TUYEN");
            var route=new com.datvexe.domain.Entities.TuyenXe();route.maTuyen="TEST_PG_RETURN";route.tenTuyen="Return fixture";
            route.tramKhoiHanh=original.tramDen;route.tramDen=original.tramKhoiHanh;route.nhaXe=original.nhaXe;
            route.giaCoBan=original.giaCoBan;route.thoiGianDuKien=original.thoiGianDuKien;route.trangThai=TrangThaiTuyenXe.DANG_KHAI_THAC;
            em.persist(route);trip.tuyenXe=route;em.getTransaction().commit();
        }
    }
    @AfterEach void restoreSeedRoute() {
        try(var factory=factory();var em=factory.createEntityManager()) {
            em.getTransaction().begin();em.find(com.datvexe.domain.Entities.ChuyenXe.class,"DEMO_SAU").tuyenXe=em.find(com.datvexe.domain.Entities.TuyenXe.class,"DEMO_TUYEN");
            em.remove(em.find(com.datvexe.domain.Entities.TuyenXe.class,"TEST_PG_RETURN"));em.getTransaction().commit();
        }
    }
    private EntityManagerFactory factory() {
        return Persistence.createEntityManagerFactory("datvexe",Map.of(
            "jakarta.persistence.jdbc.driver","org.postgresql.Driver",
            "jakarta.persistence.jdbc.url",System.getProperty("vehicle.pg.url"),
            "jakarta.persistence.jdbc.user",System.getProperty("vehicle.pg.user","vehicle_test"),
            "jakarta.persistence.jdbc.password",System.getProperty("vehicle.pg.password",""),
            "hibernate.hbm2ddl.auto","validate"));
    }
    @Test void validatesSchemaSeedRepositoriesAndAssignmentConditions() {
        try(EntityManagerFactory factory=factory()) {
            VehicleService vehicles=new VehicleService(factory);
            AssignmentService assignments=new AssignmentService(factory);
            assertEquals(29,vehicles.seats("DEMO_XE",false).size());
            assertEquals(40,vehicles.seats("DEMO_XE2",false).size());
            assertEquals(20,vehicles.seats("DEMO_XE2",false).stream().filter(g->g.get("tang").equals(2)).count());
            long assignmentsBefore=assignments.list().size();
            assertTrue(assignmentsBefore>=3);
            assertEquals(409,assertThrows(DomainException.class,()->vehicles.vehicleEligibility("DEMO_XE","DEMO_TRUNG")).status());
            assertThrows(DomainException.class,()->vehicles.vehicleEligibility("DEMO_BAOTRI","DEMO_TRUNG"));
            assertThrows(DomainException.class,()->vehicles.vehicleEligibility("DEMO_HETHAN","DEMO_TRUNG"));
            assertEquals(true,vehicles.vehicleEligibility("DEMO_XE2","DEMO_SAU").get("duDieuKien"));
            assertThrows(DomainException.class,()->assignments.save(null,new AssignmentInputs.PhanCong("TEST_OVERLAP","DEMO_TRUNG","DEMO_TX",VaiTroChuyen.TAI_XE_CHINH)));
            assertThrows(DomainException.class,()->assignments.save(null,new AssignmentInputs.PhanCong("TEST_EXPIRED","DEMO_TRUNG","DEMO_TXHH",VaiTroChuyen.TAI_XE_PHU)));
            assertThrows(DomainException.class,()->assignments.save(null,new AssignmentInputs.PhanCong("TEST_LEAVE","DEMO_TRUNG","DEMO_TXNGHI",VaiTroChuyen.TAI_XE_CHINH)));
            assertThrows(DomainException.class,()->assignments.save(null,new AssignmentInputs.PhanCong("TEST_ROLE","DEMO_TRUNG","DEMO_PX",VaiTroChuyen.TAI_XE_CHINH)));
            var saved=assignments.save(null,new AssignmentInputs.PhanCong("TEST_OK","DEMO_SAU","DEMO_TX",VaiTroChuyen.TAI_XE_CHINH,"Kiểm thử PostgreSQL",true));
            try {
                assertTrue(saved.get("ghiChu").toString().startsWith("[T11_PLAN:"));
                assertTrue(saved.get("ghiChu").toString().endsWith("Kiểm thử PostgreSQL"));
                assertEquals("DEMO_SAU",assignments.get("TEST_OK").get("maChuyen"));
            } finally {
                assignments.delete("TEST_OK");
            }
            assertEquals(assignmentsBefore,assignments.list().size());
        }
    }
    @Test void concurrentAssignmentsCannotDoubleBookDriver() throws Exception {
        try(EntityManagerFactory factory=factory(); ExecutorService workers=Executors.newFixedThreadPool(2)) {
            VehicleService vehicles=new VehicleService(factory);
            AssignmentService assignments=new AssignmentService(factory);
            CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
            String[] ids={"TEST_CONCURRENT_1","TEST_CONCURRENT_2"};
            try {
                var first=workers.submit(()->assignTogether(assignments,ids[0],ready,start));
                var second=workers.submit(()->assignTogether(assignments,ids[1],ready,start));
                assertTrue(ready.await(10,TimeUnit.SECONDS));
                start.countDown();
                assertEquals(1,first.get(30,TimeUnit.SECONDS)+second.get(30,TimeUnit.SECONDS));
                assertEquals(1,assignments.list().stream()
                    .filter(p->p.get("maPhanCong").toString().startsWith("TEST_CONCURRENT_")).count());
            } finally {
                start.countDown();
                workers.shutdown();
                if(!workers.awaitTermination(35,TimeUnit.SECONDS)) workers.shutdownNow();
                for(String id:ids) {
                    if(assignments.list().stream().anyMatch(p->id.equals(p.get("maPhanCong"))))
                        assignments.delete(id);
                }
            }
        }
    }
    private int assignTogether(AssignmentService assignments,String id,CountDownLatch ready,CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            assignments.save(null,new AssignmentInputs.PhanCong(id,"DEMO_SAU","DEMO_TX",VaiTroChuyen.TAI_XE_CHINH,null,true));
            return 1;
        } catch(DomainException e) {
            assertEquals(409,e.status());
            return 0;
        }
    }
}
