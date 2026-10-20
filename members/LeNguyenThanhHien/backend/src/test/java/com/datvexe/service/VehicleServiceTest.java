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
        assertThrows(DomainException.class,()->service.saveVehicle(null,vehicle("Y","51B-12345")));
        assertEquals(1,service.list("xe").size());
        assertEquals(404,assertThrows(DomainException.class,()->service.get("xe","Y")).status());
    }
    @Test void seatCrudKeepsCapacityAndRollsBackDuplicatePositions() {
        service.saveVehicle(null,vehicle("X","51B-TEST"));
        var seats=service.seats("X",false);
        String id=(String)seats.get(0).get("maGhe");
        assertThrows(DomainException.class,()->service.createSeat("X",new VehicleInputs.Ghe("EXTRA",1,null)));
        assertEquals(409,assertThrows(DomainException.class,()->service.deleteSeat("X",id)).status());
        var layout=seats.stream().map(g -> new VehicleInputs.ViTriGhe((String)g.get("maGhe"),(String)g.get("viTri"),(Integer)g.get("tang"),TrangThaiGhe.HOAT_DONG)).toList();
        var replaced=new java.util.ArrayList<>(layout);
        replaced.set(0,new VehicleInputs.ViTriGhe(null,"A01",2,TrangThaiGhe.HOAT_DONG));
        assertEquals(6,service.saveLayout("X",new VehicleInputs.SoDoGhe(replaced)).size());
        assertThrows(DomainException.class,()->service.getSeat("X",id));
        assertEquals(6,service.seats("X",false).size());
    }
    @Test void rejectsInvalidInspectionAndSeatStructure() {
        assertThrows(DomainException.class,()->service.saveVehicle(null,new VehicleInputs.Xe("X","51B","L","NX",2024,LocalDate.of(2027,1,1),LocalDate.of(2026,1,1),null)));
        service.saveVehicle(null,vehicle("X","51B"));
        assertThrows(DomainException.class,()->service.saveType("L",new VehicleInputs.LoaiXe("L","Test",10,2,null)));
        assertEquals(6,service.get("loai-xe","L").get("soChoNgoi"));
    }
}
