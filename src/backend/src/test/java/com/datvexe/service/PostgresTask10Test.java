package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.VehicleInputs;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses only disposable fixtures in the explicitly selected PostgreSQL test database. */
@EnabledIfSystemProperty(named="vehicle.pg.url", matches=".+")
class PostgresTask10Test {
    EntityManagerFactory factory;
    VehicleService vehicles;
    String prefix;
    @BeforeEach void setup() {
        factory=Persistence.createEntityManagerFactory("datvexe",Map.of(
            "jakarta.persistence.jdbc.driver","org.postgresql.Driver",
            "jakarta.persistence.jdbc.url",System.getProperty("vehicle.pg.url"),
            "jakarta.persistence.jdbc.user",System.getProperty("vehicle.pg.user","vehicle_test"),
            "jakarta.persistence.jdbc.password",System.getProperty("vehicle.pg.password",""),
            "hibernate.hbm2ddl.auto","validate"));
        vehicles=new VehicleService(factory);prefix="T10PG_"+UUID.randomUUID().toString().substring(0,8);
        for(String id:List.of("L1","L2")) vehicles.saveType(null,new VehicleInputs.LoaiXe(prefix+id,"T10 PostgreSQL",4,2,null));
    }
    @AfterEach void cleanup() {
        try {
            for(String id:List.of("X1","X2")) {
                if(vehicles.list("xe").stream().anyMatch(x -> (prefix+id).equals(x.get("maXe")))) vehicles.delete("xe",prefix+id);
            }
            for(String id:List.of("L1","L2")) vehicles.delete("loai-xe",prefix+id);
        } finally {factory.close();}
    }
    VehicleInputs.Xe input(String id,String type) {
        return new VehicleInputs.Xe(prefix+id,"51B-"+prefix.replace('_','-'),prefix+type,type.equals("L1")?"DEMO_NX":"DEMO_NX_MORE",2024,
            LocalDate.of(2020,1,1),LocalDate.of(2100,1,1),TrangThaiXe.SAN_SANG);
    }
    List<VehicleInputs.ViTriGhe> layout() {
        return vehicles.seats(prefix+"X1",false).stream().map(g -> new VehicleInputs.ViTriGhe(
            (String)g.get("maGhe"),(String)g.get("viTri"),(Integer)g.get("tang"),TrangThaiGhe.valueOf(g.get("trangThai").toString()))).toList();
    }
    @Test void fullLayoutSwapsLabelsAndRejectsBadLastEntryAtomically() {
        vehicles.saveVehicle(null,input("X1","L1"));var old=layout();var swap=new ArrayList<>(old);
        var a=old.get(0);var b=old.get(1);
        swap.set(0,new VehicleInputs.ViTriGhe(a.maGhe(),b.viTri(),a.tang(),a.trangThai()));
        swap.set(1,new VehicleInputs.ViTriGhe(b.maGhe(),a.viTri(),b.tang(),b.trangThai()));
        vehicles.saveLayout(prefix+"X1",new VehicleInputs.SoDoGhe(swap));
        assertEquals(b.viTri(),vehicles.getSeat(prefix+"X1",a.maGhe()).get("viTri"));
        var saved=layout();var invalid=new ArrayList<>(saved);
        invalid.set(0,new VehicleInputs.ViTriGhe(saved.get(0).maGhe(),"CHANGED",1,TrangThaiGhe.BAO_TRI));
        invalid.set(3,new VehicleInputs.ViTriGhe(saved.get(3).maGhe(),"LAST",3,TrangThaiGhe.HOAT_DONG));
        assertEquals(400,assertThrows(DomainException.class,()->vehicles.saveLayout(prefix+"X1",new VehicleInputs.SoDoGhe(invalid))).status());
        assertEquals(saved,layout());
        assertThrows(DomainException.class,()->vehicles.saveType(prefix+"L1",new VehicleInputs.LoaiXe(prefix+"L1","Changed",6,2,null)));
        assertEquals(saved,layout());
        assertEquals(4,vehicles.get("xe",prefix+"X1").get("soChoNgoi"));
    }
    @Test void concurrentSamePlateAcrossTypesCreatesOnlyOneCompleteVehicle() throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(ExecutorService workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->createTogether("X1","L1",ready,start));
            var second=workers.submit(()->createTogether("X2","L2",ready,start));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertEquals(1,first.get(30,TimeUnit.SECONDS)+second.get(30,TimeUnit.SECONDS));
            var created=vehicles.list("xe").stream().filter(x -> x.get("maXe").toString().startsWith(prefix)).toList();
            assertEquals(1,created.size());assertEquals(true,created.getFirst().get("soDoHopLe"));
            try(var em=factory.createEntityManager()) {
                assertEquals(4,em.createQuery("select count(g) from Ghe g where g.xe.maXe like :prefix",Long.class)
                    .setParameter("prefix",prefix+"%").getSingleResult());
            }
        } finally {start.countDown();}
    }
    int createTogether(String id,String type,CountDownLatch ready,CountDownLatch start) throws InterruptedException {
        ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));
        try {vehicles.saveVehicle(null,input(id,type));return 1;}
        catch(DomainException e) {assertEquals(409,e.status());return 0;}
        catch(PersistenceException e) {
            boolean unique=false;
            for(Throwable cause=e;cause!=null;cause=cause.getCause())
                if(cause instanceof org.hibernate.exception.ConstraintViolationException violation && "23505".equals(violation.getSQLState())) unique=true;
            assertTrue(unique,"Only a PostgreSQL unique conflict is expected");return 0;
        }
    }
}
