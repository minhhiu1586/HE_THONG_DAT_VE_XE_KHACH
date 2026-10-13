package com.datvexe.service;

import com.datvexe.dto.VehicleInputs;
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
            assertEquals(29,vehicles.seats("DEMO_XE",false).size());
            assertEquals(40,vehicles.seats("DEMO_XE2",false).size());
            assertEquals(20,vehicles.seats("DEMO_XE2",false).stream().filter(g->g.get("tang").equals(2)).count());
            long assignmentsBefore=vehicles.list("phan-cong").size();
            assertTrue(assignmentsBefore>=3);
            assertEquals(409,assertThrows(DomainException.class,()->vehicles.vehicleEligibility("DEMO_XE","DEMO_TRUNG")).status());
            assertThrows(DomainException.class,()->vehicles.vehicleEligibility("DEMO_BAOTRI","DEMO_TRUNG"));
            assertThrows(DomainException.class,()->vehicles.vehicleEligibility("DEMO_HETHAN","DEMO_TRUNG"));
            assertEquals(true,vehicles.vehicleEligibility("DEMO_XE2","DEMO_SAU").get("duDieuKien"));
            assertThrows(DomainException.class,()->vehicles.saveAssignment(null,new VehicleInputs.PhanCong("TEST_OVERLAP","DEMO_TRUNG","DEMO_TX",VaiTroChuyen.TAI_XE_CHINH)));
            assertThrows(DomainException.class,()->vehicles.saveAssignment(null,new VehicleInputs.PhanCong("TEST_EXPIRED","DEMO_TRUNG","DEMO_TXHH",VaiTroChuyen.TAI_XE_PHU)));
            assertThrows(DomainException.class,()->vehicles.saveAssignment(null,new VehicleInputs.PhanCong("TEST_LEAVE","DEMO_TRUNG","DEMO_TXNGHI",VaiTroChuyen.TAI_XE_CHINH)));
            assertThrows(DomainException.class,()->vehicles.saveAssignment(null,new VehicleInputs.PhanCong("TEST_ROLE","DEMO_TRUNG","DEMO_PX",VaiTroChuyen.TAI_XE_CHINH)));
            var saved=vehicles.saveAssignment(null,new VehicleInputs.PhanCong("TEST_OK","DEMO_SAU","DEMO_TX",VaiTroChuyen.TAI_XE_CHINH,"Kiểm thử PostgreSQL"));
            try {
                assertEquals("Kiểm thử PostgreSQL",saved.get("ghiChu"));
                assertEquals("DEMO_SAU",vehicles.get("phan-cong","TEST_OK").get("maChuyen"));
            } finally {
                vehicles.delete("phan-cong","TEST_OK");
            }
            assertEquals(assignmentsBefore,vehicles.list("phan-cong").size());
        }
    }
    @Test void concurrentAssignmentsCannotDoubleBookDriver() throws Exception {
        try(EntityManagerFactory factory=factory(); ExecutorService workers=Executors.newFixedThreadPool(2)) {
            VehicleService vehicles=new VehicleService(factory);
            CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
            String[] ids={"TEST_CONCURRENT_1","TEST_CONCURRENT_2"};
            try {
                var first=workers.submit(()->assignTogether(vehicles,ids[0],ready,start));
                var second=workers.submit(()->assignTogether(vehicles,ids[1],ready,start));
                assertTrue(ready.await(10,TimeUnit.SECONDS));
                start.countDown();
                assertEquals(1,first.get(30,TimeUnit.SECONDS)+second.get(30,TimeUnit.SECONDS));
                assertEquals(1,vehicles.list("phan-cong").stream()
                    .filter(p->p.get("maPhanCong").toString().startsWith("TEST_CONCURRENT_")).count());
            } finally {
                start.countDown();
                workers.shutdown();
                if(!workers.awaitTermination(35,TimeUnit.SECONDS)) workers.shutdownNow();
                for(String id:ids) {
                    if(vehicles.list("phan-cong").stream().anyMatch(p->id.equals(p.get("maPhanCong"))))
                        vehicles.delete("phan-cong",id);
                }
            }
        }
    }
    private int assignTogether(VehicleService vehicles,String id,CountDownLatch ready,CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            vehicles.saveAssignment(null,new VehicleInputs.PhanCong(id,"DEMO_SAU","DEMO_TX",VaiTroChuyen.TAI_XE_CHINH));
            return 1;
        } catch(DomainException e) {
            assertEquals(409,e.status());
            return 0;
        }
    }
}
