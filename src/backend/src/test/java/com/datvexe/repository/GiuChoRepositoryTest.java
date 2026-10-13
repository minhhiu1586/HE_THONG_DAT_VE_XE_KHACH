package com.datvexe.repository;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.TrangThaiGiuCho;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GiuChoRepositoryTest {
    @Test void activeHoldsAreScopedToTripAndExpiredHoldsAreReleased() {
        try (EntityManagerFactory factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", "jdbc:h2:mem:hold_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "hibernate.hbm2ddl.auto", "create-drop"));
             EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            TinhThanh city = new TinhThanh(); city.maTinhThanh = "CITY"; city.tenTinhThanh = "City"; em.persist(city);
            BenXe start = station("START", city), end = station("END", city);
            em.persist(start); em.persist(end);
            NhaXe operator = new NhaXe(); operator.maNhaXe = "OP"; operator.tenNhaXe = "Operator"; em.persist(operator);
            TuyenXe route = new TuyenXe(); route.maTuyen = "ROUTE"; route.tenTuyen = "Route";
            route.tramKhoiHanh = start; route.tramDen = end; route.nhaXe = operator;
            route.giaCoBan = BigDecimal.valueOf(100000); em.persist(route);
            Xe vehicle = new Xe(); vehicle.maXe = "BUS"; vehicle.bienSo = "51B-HOLD";
            vehicle.soChoNgoi = 3; vehicle.nhaXe = operator; em.persist(vehicle);
            Ghe first = seat("S1", vehicle), second = seat("S2", vehicle), third = seat("S3", vehicle);
            em.persist(first); em.persist(second); em.persist(third);
            ChuyenXe outbound = trip("T1", route, vehicle), returnTrip = trip("T2", route, vehicle);
            em.persist(outbound); em.persist(returnTrip);
            LocalDateTime now = LocalDateTime.now();
            GiuChoRepository repository = new GiuChoRepository();
            repository.save(em, hold("H1", outbound, first, now.plusMinutes(15), TrangThaiGiuCho.DANG_GIU));
            repository.save(em, hold("H2", outbound, second, now.minusMinutes(1), TrangThaiGiuCho.DANG_GIU));
            repository.save(em, hold("H3", returnTrip, first, now.plusMinutes(15), TrangThaiGiuCho.DANG_GIU));
            repository.save(em, hold("H4", outbound, third, now.plusMinutes(15), TrangThaiGiuCho.DA_HUY));
            em.flush();
            assertEquals(List.of("S1"), repository.timCacGheDangBiGiu(em, "T1", List.of("S1", "S2", "S3"), now));
            assertEquals(List.of("S1"), repository.timCacGheDangBiGiu(em, "T2", List.of("S1"), now));
            assertEquals(1, repository.huyGiuChoQuaHan(em, now));
            em.clear();
            assertEquals(TrangThaiGiuCho.DA_HUY, em.find(GiuCho.class, "H2").trangThai);
            assertEquals(TrangThaiGiuCho.DANG_GIU, em.find(GiuCho.class, "H1").trangThai);
            em.getTransaction().rollback();
        }
    }
    private static BenXe station(String id, TinhThanh city) {
        BenXe x = new BenXe(); x.maBenXe = id; x.tenBenXe = id; x.diaChi = id; x.tinhThanh = city; return x;
    }
    private static Ghe seat(String id, Xe vehicle) {
        Ghe x = new Ghe(); x.maGhe = id; x.viTri = id; x.xe = vehicle; return x;
    }
    private static ChuyenXe trip(String id, TuyenXe route, Xe vehicle) {
        ChuyenXe x = new ChuyenXe(); x.maChuyen = id; x.tuyenXe = route; x.xe = vehicle;
        x.ngayKhoiHanh = LocalDate.now().plusDays(1); x.gioKhoiHanh = LocalTime.NOON;
        x.gioDenDuKien = x.ngayKhoiHanh.atTime(18, 0); return x;
    }
    private static GiuCho hold(String id, ChuyenXe trip, Ghe seat, LocalDateTime expiresAt, TrangThaiGiuCho status) {
        GiuCho x = new GiuCho(); x.maGiuCho = id; x.chuyenXe = trip; x.ghe = seat;
        x.ngayDat = expiresAt.minusMinutes(15); x.thoiDiemBatDau = x.ngayDat;
        x.thoiDiemHetHan = expiresAt; x.trangThai = status; return x;
    }
}