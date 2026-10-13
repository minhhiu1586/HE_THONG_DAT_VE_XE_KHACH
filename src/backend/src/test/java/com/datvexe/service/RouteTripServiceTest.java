package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.Inputs;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteTripServiceTest {
    private static EntityManagerFactory factory;
    private RouteTripService service;

    @BeforeAll static void startDb() {
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
            "jakarta.persistence.jdbc.driver", "org.h2.Driver",
            "jakarta.persistence.jdbc.url", "jdbc:h2:mem:route_trip;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "jakarta.persistence.jdbc.user", "sa",
            "jakarta.persistence.jdbc.password", "",
            "hibernate.hbm2ddl.auto", "create-drop"
        ));
    }
    @AfterAll static void stopDb() { factory.close(); }
    @BeforeEach void prepare() {
        service = new RouteTripService(factory);
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            for (String name : List.of("ChuyenXe", "DiemDung", "TuyenXe", "Xe", "NhaXe", "BenXe", "TinhThanh"))
                em.createQuery("delete from " + name).executeUpdate();
            em.getTransaction().commit();
        }
        service.createTinhThanh(new Inputs.TinhThanh("HCM", "TP. Hồ Chí Minh", "Miền Nam"));
        service.createTinhThanh(new Inputs.TinhThanh("LD", "Lâm Đồng", "Tây Nguyên"));
        service.createBenXe(new Inputs.BenXe("MD", "Miền Đông", "HCM", null, "HCM", null));
        service.createBenXe(new Inputs.BenXe("DL", "Đà Lạt", "Đà Lạt", null, "LD", null));
    }
    private Inputs.TuyenXe route(String id) {
        return new Inputs.TuyenXe(id, "HCM - Đà Lạt", "MD", "DL", LocalTime.of(8, 0),
            BigDecimal.valueOf(300), BigDecimal.valueOf(6.5), BigDecimal.valueOf(250000),
            null, TrangThaiTuyenXe.DANG_KHAI_THAC, null);
    }
    private void vehicle(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            Xe x = new Xe(); x.maXe = id; x.bienSo = id; x.soChoNgoi = 40;
            em.persist(x);
            em.getTransaction().commit();
        }
    }

    @Test void stationAndRouteValidationAndDeleteProtection() {
        assertThrows(DomainException.class, () -> service.createTuyenXe(new Inputs.TuyenXe("R", "Sai",
            "MD", "MD", LocalTime.NOON, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN,
            null, TrangThaiTuyenXe.DANG_KHAI_THAC, null)));
        service.createTuyenXe(route("R"));
        assertEquals("MD", service.getTuyenXe("R").get("tramKhoiHanh"));
        assertEquals(409, assertThrows(DomainException.class, () -> service.deleteBenXe("MD")).status());
        assertEquals("DL", service.setTramDauCuoi("R", new Inputs.TramDauCuoi("MD", "DL")).get("tramDen"));
        assertEquals(400, assertThrows(DomainException.class,
            () -> service.setTramDauCuoi("R", new Inputs.TramDauCuoi("MD", "MD"))).status());
    }

    @Test void persistenceUnitMapsAllTwentyEntitiesFromTheClassDiagram() {
        assertEquals(20, factory.getMetamodel().getEntities().size());
        assertNotNull(factory.getMetamodel().entity(NguoiDung.class).getAttribute("danhSachDonDat"));
        assertNotNull(factory.getMetamodel().entity(PhanCongChuyenXe.class).getAttribute("nhanVien"));
        assertNotNull(factory.getMetamodel().entity(VeXe.class).getAttribute("chuyenXe"));
        assertNotNull(factory.getMetamodel().entity(ThanhToan.class).getAttribute("phuongThucThanhToan"));
    }

    @Test void stationAndRouteCanBeUpdatedAndDeletedWhenUnused() {
        service.updateBenXe("MD", new Inputs.BenXe(null, "Miền Đông mới", "Địa chỉ mới", "123", "HCM", TrangThaiBenXe.TAM_NGUNG));
        assertEquals(TrangThaiBenXe.TAM_NGUNG, service.getBenXe("MD").get("trangThai"));
        service.createTuyenXe(route("R"));
        service.updateTuyenXe("R", new Inputs.TuyenXe(null, "Tuyến mới", "MD", "DL", LocalTime.of(8, 0),
            BigDecimal.valueOf(300), BigDecimal.valueOf(6.5), BigDecimal.valueOf(260000),
            null, null, null));
        assertEquals(TrangThaiTuyenXe.DANG_KHAI_THAC, service.getTuyenXe("R").get("trangThai"));
        assertEquals(BigDecimal.valueOf(260000).setScale(2), service.getTuyenXe("R").get("giaCoBan"));
        service.addDiemDung("R", new Inputs.DiemDung("A", "Stop", "A", null, 0, LoaiDiemDung.CA_HAI));
        service.deleteTuyenXe("R");
        assertEquals(404, assertThrows(DomainException.class, () -> service.getTuyenXe("R")).status());
        service.deleteBenXe("MD");
        assertEquals(404, assertThrows(DomainException.class, () -> service.getBenXe("MD")).status());
    }

    @Test void stopsCanBeInsertedReorderedAndRemovedWithoutDuplicatePositions() {
        service.createTuyenXe(route("R"));
        service.addDiemDung("R", new Inputs.DiemDung("A", "A", "A", null, 5, LoaiDiemDung.DIEM_DON));
        service.addDiemDung("R", new Inputs.DiemDung("C", "C", "C", null, 5, LoaiDiemDung.DIEM_TRA));
        service.addDiemDung("R", new Inputs.DiemDung("B", "B", "B", 2, 5, LoaiDiemDung.CA_HAI));
        var reordered = service.reorderDiemDung("R", new Inputs.SapXepDiemDung(List.of("C", "B", "A")));
        assertEquals(List.of("C", "B", "A"), reordered.stream().map(x -> x.get("maDiemDung")).toList());
        assertEquals(List.of(1, 2, 3), reordered.stream().map(x -> x.get("thuTu")).toList());
        assertEquals(400, assertThrows(DomainException.class,
            () -> service.reorderDiemDung("R", new Inputs.SapXepDiemDung(List.of("C", "C", "A")))).status());
        service.deleteDiemDung("R", "B");
        @SuppressWarnings("unchecked")
        var remaining = (List<Map<String, Object>>) service.getTuyenXe("R").get("tramTrungGian");
        assertEquals(List.of(1, 2), remaining.stream().map(x -> x.get("thuTu")).toList());
    }

    @Test void tripSearchVehicleConflictsAndLifecycle() {
        service.createTuyenXe(route("R"));
        vehicle("X");
        LocalDate date = LocalDate.of(2030, 1, 1);
        var first = service.createChuyenXe(new Inputs.ChuyenXe("C1", "R", date, null, "X"));
        assertEquals(LocalDateTime.of(2030, 1, 1, 14, 30), first.get("gioDenDuKien"));
        service.createChuyenXe(new Inputs.ChuyenXe("C2", "R", date, LocalTime.of(9, 0), null));
        assertEquals(409, assertThrows(DomainException.class, () -> service.assignVehicle("C2", "X")).status());
        assertEquals(2, service.searchChuyenXe("HCM", "LD", date).size());
        assertEquals(2, service.searchChuyenXe("MD", "DL", date).size());
        service.transition("C1", TrangThaiChuyen.DANG_CHAY);
        assertEquals(409, assertThrows(DomainException.class,
            () -> service.updateChuyenXe("C1", new Inputs.CapNhatChuyen(date, LocalTime.NOON))).status());
        service.transition("C1", TrangThaiChuyen.HOAN_TAT);
        service.transition("C2", TrangThaiChuyen.DA_HUY);
        assertEquals(0, service.searchChuyenXe("MD", "DL", date).size());
        assertEquals(409, assertThrows(DomainException.class,
            () -> service.transition("C2", TrangThaiChuyen.DANG_CHAY)).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.deleteTuyenXe("R")).status());
    }

    @Test void emptySearchWrongDirectionDateAndDepartureOrdering() {
        LocalDate date = LocalDate.of(2030, 1, 1);
        assertEquals(List.of(), service.searchChuyenXe("MD", "DL", date));
        service.createTuyenXe(route("R"));
        service.createChuyenXe(new Inputs.ChuyenXe("LATE", "R", date, LocalTime.of(22, 0), null));
        service.createChuyenXe(new Inputs.ChuyenXe("EARLY", "R", date, LocalTime.of(8, 0), null));
        assertEquals(List.of("EARLY", "LATE"), service.searchChuyenXe(" MD ", " DL ", date)
            .stream().map(x -> x.get("maChuyen")).toList());
        assertTrue(service.searchChuyenXe("DL", "MD", date).isEmpty());
        assertTrue(service.searchChuyenXe("MD", "DL", date.plusDays(1)).isEmpty());
        assertTrue(service.searchChuyenXe("UNKNOWN", "DL", date).isEmpty());
        assertEquals(LocalDateTime.of(2030, 1, 2, 4, 30), service.getChuyenXe("LATE").get("gioDenDuKien"));
        service.transition("EARLY", TrangThaiChuyen.DA_HUY);
        assertEquals(List.of("LATE"), service.searchChuyenXe("MD", "DL", date)
            .stream().map(x -> x.get("maChuyen")).toList());
        assertEquals(TrangThaiChuyen.DA_HUY, service.getChuyenXe("EARLY").get("trangThai"));
    }

    @Test void tripRescheduleRechecksVehicleAndRollsBackFailedChanges() {
        service.createTuyenXe(route("R")); vehicle("X");
        LocalDate date = LocalDate.of(2030, 1, 1);
        service.createChuyenXe(new Inputs.ChuyenXe("C1", "R", date, LocalTime.of(8, 0), "X"));
        // The exact end/start boundary does not overlap.
        service.createChuyenXe(new Inputs.ChuyenXe("C2", "R", date, LocalTime.of(14, 30), "X"));
        assertEquals(409, assertThrows(DomainException.class, () -> service.updateChuyenXe("C2",
            new Inputs.CapNhatChuyen(date, LocalTime.of(12, 0)))).status());
        assertEquals(LocalTime.of(14, 30), service.getChuyenXe("C2").get("gioKhoiHanh"));
        service.updateChuyenXe("C2", new Inputs.CapNhatChuyen(date.plusDays(1), LocalTime.of(10, 0)));
        assertEquals(1, service.searchChuyenXe("MD", "DL", date).size());
        assertEquals(1, service.searchChuyenXe("MD", "DL", date.plusDays(1)).size());
        service.transition("C1", TrangThaiChuyen.DA_HUY);
        service.updateChuyenXe("C2", new Inputs.CapNhatChuyen(date, LocalTime.of(8, 0)));
    }

    @Test void invalidStationRouteAndTripDataAreRejectedBeforePersistence() {
        assertEquals(400, assertThrows(DomainException.class, () -> service.createBenXe(
            new Inputs.BenXe("NEW", " ", "A", null, "HCM", null))).status());
        assertEquals(400, assertThrows(DomainException.class, () -> service.createTinhThanh(
            new Inputs.TinhThanh("X".repeat(41), "Test", null))).status());
        assertEquals(404, assertThrows(DomainException.class, () -> service.createBenXe(
            new Inputs.BenXe("NEW", "Test", "A", null, "MISSING", null))).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.createBenXe(
            new Inputs.BenXe("MD", "Test", "A", null, "HCM", null))).status());
        Inputs.TuyenXe valid = route("R");
        assertEquals(400, assertThrows(DomainException.class, () -> service.createTuyenXe(
            new Inputs.TuyenXe("BAD", "Test", "MD", "DL", LocalTime.NOON, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("0.001"), null, null, null))).status());
        service.createTuyenXe(valid);
        assertEquals(400, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C", "R", null, null, null))).status());
        assertEquals(404, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C", "MISSING", LocalDate.of(2030, 1, 1), null, null))).status());
        assertEquals(400, assertThrows(DomainException.class,
            () -> service.searchChuyenXe("MD", "DL", null)).status());
    }

    @Test void inactiveRoutesRejectCreationAndHideExistingTrips() {
        service.createTuyenXe(route("R"));
        LocalDate date = LocalDate.of(2030, 1, 1);
        service.createChuyenXe(new Inputs.ChuyenXe("C", "R", date, null, null));
        service.updateTuyenXe("R", new Inputs.TuyenXe(null, "Test", "MD", "DL", LocalTime.NOON,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, null, TrangThaiTuyenXe.TAM_NGUNG, null));
        assertTrue(service.searchChuyenXe("MD", "DL", date).isEmpty());
        assertEquals(409, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C2", "R", date, null, null))).status());
    }

    @Test void invalidStopOperationsDoNotChangeExistingOrder() {
        service.createTuyenXe(route("R")); service.createTuyenXe(route("OTHER"));
        service.addDiemDung("R", new Inputs.DiemDung("A", "A", "A", null, 0, null));
        service.addDiemDung("OTHER", new Inputs.DiemDung("B", "B", "B", null, 0, null));
        assertEquals(400, assertThrows(DomainException.class, () -> service.addDiemDung("R",
            new Inputs.DiemDung("C", "C", "C", 0, 0, null))).status());
        assertEquals(400, assertThrows(DomainException.class, () -> service.addDiemDung("R",
            new Inputs.DiemDung("C", "C", "C", null, -1, null))).status());
        assertEquals(400, assertThrows(DomainException.class, () -> service.reorderDiemDung("R",
            new Inputs.SapXepDiemDung(List.of("B")))).status());
        assertEquals(404, assertThrows(DomainException.class, () -> service.deleteDiemDung("R", "B")).status());
        assertEquals(400, assertThrows(DomainException.class, () -> service.reorderDiemDung("R",
            new Inputs.SapXepDiemDung(List.of()))).status());
        @SuppressWarnings("unchecked")
        var stops = (List<Map<String, Object>>) service.getTuyenXe("R").get("tramTrungGian");
        assertEquals(List.of("A"), stops.stream().map(x -> x.get("maDiemDung")).toList());
        assertEquals(1, stops.getFirst().get("thuTu"));
    }

    @Test void missingUnavailableVehicleAndInvalidTransitionsAreRejected() {
        service.createTuyenXe(route("R")); vehicle("X");
        LocalDate date = LocalDate.of(2030, 1, 1);
        service.createChuyenXe(new Inputs.ChuyenXe("C", "R", date, null, null));
        assertEquals(404, assertThrows(DomainException.class, () -> service.assignVehicle("C", "MISSING")).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.transition("C", TrangThaiChuyen.DANG_CHAY)).status());
        service.assignVehicle("C", "X");
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin(); em.find(Xe.class, "X").trangThai = TrangThaiXe.BAO_TRI; em.getTransaction().commit();
        }
        assertEquals(409, assertThrows(DomainException.class, () -> service.assignVehicle("C", "X")).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.transition("C", TrangThaiChuyen.DANG_CHAY)).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.transition("C", TrangThaiChuyen.HOAN_TAT)).status());
        service.transition("C", TrangThaiChuyen.DA_HUY);
        assertEquals(409, assertThrows(DomainException.class, () -> service.assignVehicle("C", "X")).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.transition("C", TrangThaiChuyen.DA_HUY)).status());
    }

    @Test void routeNeedsDurationAndDepartureTimeAndFailedCreationRollsBack() {
        service.createTuyenXe(new Inputs.TuyenXe("R", "Test", "MD", "DL", null, null,
            null, BigDecimal.TEN, null, TrangThaiTuyenXe.DANG_KHAI_THAC, null));
        LocalDate date = LocalDate.of(2030, 1, 1);
        assertEquals(400, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C", "R", date, LocalTime.NOON, null))).status());
        service.updateTuyenXe("R", new Inputs.TuyenXe(null, "Test", "MD", "DL", null, null,
            BigDecimal.ONE, BigDecimal.TEN, null, null, null));
        assertEquals(400, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C", "R", date, null, null))).status());
        assertEquals(404, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C", "R", date, LocalTime.NOON, "MISSING"))).status());
        assertEquals(404, assertThrows(DomainException.class, () -> service.getChuyenXe("C")).status());
        service.createChuyenXe(new Inputs.ChuyenXe("C", "R", date, LocalTime.NOON, null));
    }

    @Test void assignedVehicleMustBelongToRouteOperatorWhenSpecified() {
        vehicle("X");
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            NhaXe a = new NhaXe(); a.maNhaXe = "A"; a.tenNhaXe = "A"; em.persist(a);
            NhaXe b = new NhaXe(); b.maNhaXe = "B"; b.tenNhaXe = "B"; em.persist(b);
            em.getTransaction().commit();
        }
        service.createTuyenXe(new Inputs.TuyenXe("R", "Test", "MD", "DL", LocalTime.NOON,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, null, TrangThaiTuyenXe.DANG_KHAI_THAC, "A"));
        service.createChuyenXe(new Inputs.ChuyenXe("C", "R", LocalDate.of(2030, 1, 1), null, null));
        assertEquals(409, assertThrows(DomainException.class, () -> service.assignVehicle("C", "X")).status());
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin(); em.find(Xe.class, "X").nhaXe = em.find(NhaXe.class, "B"); em.getTransaction().commit();
        }
        assertEquals(409, assertThrows(DomainException.class, () -> service.assignVehicle("C", "X")).status());
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin(); em.find(Xe.class, "X").nhaXe = em.find(NhaXe.class, "A"); em.getTransaction().commit();
        }
        assertEquals("X", service.assignVehicle("C", "X").get("maXe"));
    }
}
