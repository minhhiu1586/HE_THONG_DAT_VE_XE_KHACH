package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.AssignmentInputs;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentServiceTest {
    private EntityManagerFactory factory;
    private AssignmentService assignments;

    @BeforeEach
    void setup() {
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
            "jakarta.persistence.jdbc.driver", "org.h2.Driver",
            "jakarta.persistence.jdbc.url", "jdbc:h2:mem:assignments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "jakarta.persistence.jdbc.user", "sa", "jakarta.persistence.jdbc.password", "",
            "hibernate.hbm2ddl.auto", "create-drop"));
        assignments = new AssignmentService(factory);
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            NhaXe owner = new NhaXe();
            owner.maNhaXe = "NX";
            owner.tenNhaXe = "Nhà xe";
            em.persist(owner);
            em.getTransaction().commit();
        }
    }

    @AfterEach
    void close() {
        com.datvexe.security.RequestIdentity.clear();
        factory.close();
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
        var input=new AssignmentInputs.PhanCong("P1","C1","N",VaiTroChuyen.TAI_XE_CHINH);
        assertThrows(DomainException.class,()->assignments.save(null,input));
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin(); em.find(NhanVien.class,"N").ngayHetHanBangLai=LocalDate.of(2031,1,1); em.getTransaction().commit();
        }
        assertThrows(DomainException.class,()->assignments.save(null,new AssignmentInputs.PhanCong("P1","C1","N",VaiTroChuyen.PHU_XE)));
        assignments.save(null,input);
        assertEquals("N",assignments.save("P1",new AssignmentInputs.PhanCong("P1","C1","N",VaiTroChuyen.TAI_XE_PHU,"Cập nhật vai trò")).get("maNhanVien"));
        assertEquals("Cập nhật vai trò",assignments.get("P1").get("ghiChu"));
        assertThrows(DomainException.class,()->assignments.save(null,new AssignmentInputs.PhanCong("P2","C2","N",VaiTroChuyen.TAI_XE_CHINH)));
        assertEquals(1,assignments.list().size());
        assignments.delete("P1"); assertTrue(assignments.list().isEmpty());
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();
            NhaXe otherOwner=new NhaXe(); otherOwner.maNhaXe="NX2"; otherOwner.tenNhaXe="Demo 2";
            em.persist(otherOwner);
            em.find(NhanVien.class,"N").nhaXe=otherOwner;
            em.getTransaction().commit();
        }
        assertEquals(409,assertThrows(DomainException.class,()->assignments.save(null,input)).status());
    }
}
