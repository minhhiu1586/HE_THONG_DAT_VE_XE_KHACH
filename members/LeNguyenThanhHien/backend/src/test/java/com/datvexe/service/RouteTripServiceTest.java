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
            for (String name : List.of("GiuCho", "PhanCongChuyenXe", "NhanVien", "ChuyenXe", "DiemDung", "TuyenXe", "Ghe", "Xe", "LoaiXe", "NhaXe", "BenXe", "TinhThanh"))
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
            if(em.find(NhaXe.class,"TEST_NX")==null) { var n=new NhaXe();n.maNhaXe="TEST_NX";n.tenNhaXe="Test";em.persist(n); }
            if(em.find(LoaiXe.class,"TEST_L")==null) { var l=new LoaiXe();l.maLoaiXe="TEST_L";l.tenLoaiXe="Test";l.soChoNgoi=40;l.soTang=2;em.persist(l); }
            Xe x = new Xe(); x.maXe = id; x.bienSo = id; x.soChoNgoi = 40;x.nhaXe=em.find(NhaXe.class,"TEST_NX");x.loaiXe=em.find(LoaiXe.class,"TEST_L");
            x.ngayDangKiem=LocalDate.of(2020,1,1);x.hanDangKiem=LocalDate.of(2100,1,1);em.persist(x);
            for(int i=1;i<=40;i++) { var g=new Ghe();g.maGhe=id+"_"+i;g.viTri="G"+i;g.xe=x;em.persist(g); }
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

    @Test void persistenceUnitMapsDomainEntitiesAndInfrastructure() {
        assertEquals(22, factory.getMetamodel().getEntities().size());
        assertNotNull(factory.getMetamodel().entity(com.datvexe.domain.EmailOutbox.class).getAttribute("maDon"));
        assertNotNull(factory.getMetamodel().entity(NguoiDung.class).getAttribute("danhSachDonDat"));
        assertNotNull(factory.getMetamodel().entity(PhanCongChuyenXe.class).getAttribute("nhanVien"));
        assertNotNull(factory.getMetamodel().entity(VeXe.class).getAttribute("chuyenXe"));
        assertNotNull(factory.getMetamodel().entity(ThanhToan.class).getAttribute("phuongThucThanhToan"));
    }

    @Test void stationAndRouteCanBeUpdatedAndDeletedWhenUnused() {
        service.updateBenXe("MD", new Inputs.BenXe(null, "Miền Đông mới", "Địa chỉ mới", "123", "HCM", TrangThaiBenXe.TAM_NGUNG));
        assertEquals(TrangThaiBenXe.TAM_NGUNG, service.getBenXe("MD").get("trangThai"));
        assertEquals(409, assertThrows(DomainException.class, () -> service.createTuyenXe(route("INACTIVE"))).status());
        service.updateBenXe("MD", new Inputs.BenXe(null, "Miền Đông mới", "Địa chỉ mới", "123", "HCM", TrangThaiBenXe.HOAT_DONG));
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

    @Test void citiesCanBeSearchedUpdatedAndCannotBeDeletedWhileReferenced() {
        assertEquals(List.of("HCM"), service.listTinhThanh("hồ chí").stream().map(x -> x.get("maTinhThanh")).toList());
        assertEquals(409, assertThrows(DomainException.class, () -> service.createTinhThanh(
            new Inputs.TinhThanh("HCM", "Trùng mã", null))).status());
        service.updateTinhThanh("HCM", new Inputs.CapNhatTinhThanh("TP HCM", "Miền Đông Nam Bộ"));
        assertEquals("TP HCM", service.getTinhThanh("HCM").get("tenTinhThanh"));
        assertEquals(409, assertThrows(DomainException.class, () -> service.deleteTinhThanh("HCM")).status());
        assertEquals(400, assertThrows(DomainException.class, () -> service.updateTinhThanh("HCM",
            new Inputs.CapNhatTinhThanh(" ", null))).status());
        service.createTinhThanh(new Inputs.TinhThanh("FREE", "Tỉnh chưa dùng", null));
        service.deleteTinhThanh("FREE");
        assertEquals(404, assertThrows(DomainException.class, () -> service.getTinhThanh("FREE")).status());
    }

    @Test void stationsCanBeFilteredByProvinceNameAndStatus() {
        assertEquals(List.of("MD"), service.listBenXe("HCM", "MIỀN", TrangThaiBenXe.HOAT_DONG)
            .stream().map(x -> x.get("maBenXe")).toList());
        assertTrue(service.listBenXe("LD", null, null).stream().anyMatch(x -> x.get("maBenXe").equals("DL")));
        assertTrue(service.listBenXe("UNKNOWN", null, null).isEmpty());
    }
    @Test void inactiveTerminalCannotBeUsedToCreateANewTrip() {
        service.createTuyenXe(route("R"));
        service.updateBenXe("MD", new Inputs.BenXe(null, "Miền Đông", "HCM", null, "HCM", TrangThaiBenXe.TAM_NGUNG));
        assertEquals(409, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C", "R", LocalDate.of(2030, 1, 1), null, null))).status());
        service.updateBenXe("MD", new Inputs.BenXe(null, "Miền Đông", "HCM", null, "HCM", TrangThaiBenXe.HOAT_DONG));
        service.createChuyenXe(new Inputs.ChuyenXe("C", "R", LocalDate.of(2030, 1, 1), null, null));
    }

    @Test void routeSearchFiltersAndOperatorScopeAreAppliedTogether() {
        service.createTuyenXe(route("R1"));
        service.createTuyenXe(new Inputs.TuyenXe("R2", "Tuyến khác", "MD", "DL", LocalTime.NOON,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, null, TrangThaiTuyenXe.TAM_NGUNG, null));
        assertEquals(List.of("R1"), service.listTuyenXe("MD", "DL", null, TrangThaiTuyenXe.DANG_KHAI_THAC)
            .stream().map(x -> x.get("maTuyen")).toList());
        assertTrue(service.listTuyenXe("DL", "MD", null, null).isEmpty());
    }

    @Test void stopsCanBeInsertedReorderedAndRemovedWithoutDuplicatePositions() {
        service.createTuyenXe(route("R"));
        service.addDiemDung("R", new Inputs.DiemDung("A", "A", "A", null, 5, LoaiDiemDung.DIEM_DON));
        service.addDiemDung("R", new Inputs.DiemDung("C", "C", "C", null, 5, LoaiDiemDung.DIEM_TRA));
        service.addDiemDung("R", new Inputs.DiemDung("B", "B", "B", 2, 5, LoaiDiemDung.CA_HAI));
        assertEquals("Bến dừng đã sửa", service.updateDiemDung("R", "B",
            new Inputs.CapNhatDiemDung("Bến dừng đã sửa", "Địa chỉ mới", 10, LoaiDiemDung.DIEM_TRA)).get("tenDiemDung"));
        assertEquals(400, assertThrows(DomainException.class, () -> service.updateDiemDung("R", "B",
            new Inputs.CapNhatDiemDung("Sai", "Địa chỉ", -1, LoaiDiemDung.CA_HAI))).status());
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
        assertTrue(service.searchChuyenXe("HCM", "LD", date).isEmpty());
        PublishedTripFixture.open(factory,"C1");
        assertEquals(1, service.searchChuyenXe("HCM", "LD", date).size());
        assertEquals(1, service.searchChuyenXe("MD", "DL", date).size());
        var searchable = service.searchChuyenXe("CITY:HCM", "STATION:DL", date).getFirst();
        assertEquals("Miền Đông", searchable.get("tenBenDi"));
        assertEquals("Đà Lạt", searchable.get("tenBenDen"));
        assertEquals("Test", searchable.get("tenNhaXe"));
        assertEquals(40, searchable.get("soChoNgoi"));
        assertEquals(40L, searchable.get("tongSoGhe"));
        assertEquals(40L, searchable.get("soGheTrong"));
        assertEquals(true, searchable.get("coTheDatVe"));
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

    @Test void publicDetailAndAvailabilityUseCurrentTripSpecificSeats() {
        service.createTuyenXe(route("R"));
        service.createTuyenXe(new Inputs.TuyenXe("RETURN", "Đà Lạt - HCM", "DL", "MD", LocalTime.of(8, 0),
            BigDecimal.valueOf(300), BigDecimal.valueOf(6.5), BigDecimal.valueOf(250000),
            null, TrangThaiTuyenXe.DANG_KHAI_THAC, null));
        vehicle("X");
        LocalDate day = LocalDate.of(2030, 1, 1);
        service.createChuyenXe(new Inputs.ChuyenXe("C1", "R", day, null, "X"));
        service.createChuyenXe(new Inputs.ChuyenXe("C2", "RETURN", day.plusDays(1), null, "X"));
        PublishedTripFixture.open(factory, "C1");
        PublishedTripFixture.open(factory, "C2");

        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var hold = new GiuCho();
            hold.maGiuCho = "H1";
            hold.chuyenXe = em.find(ChuyenXe.class, "C1");
            hold.ghe = em.find(Ghe.class, "X_1");
            hold.ngayDat = LocalDateTime.now();
            hold.thoiDiemBatDau = LocalDateTime.now();
            hold.thoiDiemHetHan = LocalDateTime.now().plusDays(1);
            em.persist(hold);
            em.getTransaction().commit();
        }
        assertEquals(39L, service.getChuyenXe("C1").get("soGheTrong"));
        assertEquals(40L, service.getChuyenXe("C2").get("soGheTrong"));
        assertEquals("Miền Đông", service.getChuyenXe("C1").get("tenBenDi"));
        assertEquals("HCM", service.getChuyenXe("C1").get("diaChiBenDi"));
        assertEquals(List.of(), service.getChuyenXe("C1").get("diemDung"));

        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            em.find(GiuCho.class, "H1").thoiDiemHetHan = LocalDateTime.now().minusMinutes(1);
            em.getTransaction().commit();
        }
        assertEquals(40L, service.getChuyenXe("C1").get("soGheTrong"));
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("select g from Ghe g where g.xe.maXe = :vehicle", Ghe.class)
                .setParameter("vehicle", "X").getResultList()
                .forEach(seat -> seat.trangThai = TrangThaiGhe.BAO_TRI);
            em.getTransaction().commit();
        }
        var full = service.searchChuyenXe("CITY:HCM", "CITY:LD", day);
        assertEquals(1, full.size());
        assertEquals(0L, full.getFirst().get("soGheTrong"));
        assertEquals(false, full.getFirst().get("coTheDatVe"));
        assertEquals("HET_CHO", service.getChuyenXe("C1").get("trangThaiNhanDatVe"));
        assertEquals(400, assertThrows(DomainException.class,
            () -> service.searchChuyenXe("CITY:UNKNOWN", "CITY:LD", day)).status());
        assertEquals(400, assertThrows(DomainException.class,
            () -> service.searchChuyenXe("CITY:HCM", "STATION:MD", day)).status());
    }

    @Test void emptySearchWrongDirectionDateAndDepartureOrdering() {
        LocalDate date = LocalDate.of(2030, 1, 1);
        assertEquals(List.of(), service.searchChuyenXe("MD", "DL", date));
        service.createTuyenXe(route("R"));
        service.createChuyenXe(new Inputs.ChuyenXe("LATE", "R", date, LocalTime.of(22, 0), null));
        service.createChuyenXe(new Inputs.ChuyenXe("EARLY", "R", date, LocalTime.of(8, 0), null));
        PublishedTripFixture.open(factory,"EARLY"); PublishedTripFixture.open(factory,"LATE");
        assertEquals(List.of("EARLY", "LATE"), service.searchChuyenXe(" MD ", " DL ", date)
            .stream().map(x -> x.get("maChuyen")).toList());
        assertTrue(service.searchChuyenXe("DL", "MD", date).isEmpty());
        assertTrue(service.searchChuyenXe("MD", "DL", date.plusDays(1)).isEmpty());
        assertEquals(400, assertThrows(DomainException.class,
            () -> service.searchChuyenXe("UNKNOWN", "DL", date)).status());
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
        service.createTuyenXe(new Inputs.TuyenXe("RETURN","Đà Lạt - HCM","DL","MD",LocalTime.of(15,0),
            BigDecimal.valueOf(300),BigDecimal.valueOf(6.5),BigDecimal.valueOf(250000),null,TrangThaiTuyenXe.DANG_KHAI_THAC,null));
        // Same vehicle returns from its arrival station after 30 minutes of preparation.
        service.createChuyenXe(new Inputs.ChuyenXe("C2", "RETURN", date, LocalTime.of(15, 0), "X"));
        assertEquals(409, assertThrows(DomainException.class, () -> service.updateChuyenXe("C2",
            new Inputs.CapNhatChuyen(date, LocalTime.of(12, 0)))).status());
        assertEquals(LocalTime.of(15, 0), service.getChuyenXe("C2").get("gioKhoiHanh"));
        service.updateChuyenXe("C2", new Inputs.CapNhatChuyen(date.plusDays(1), LocalTime.of(10, 0)));
        assertTrue(service.searchChuyenXe("MD", "DL", date).isEmpty());
        assertTrue(service.searchChuyenXe("DL", "MD", date.plusDays(1)).isEmpty());
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
        service.setTrangThaiTuyenXe("R", TrangThaiTuyenXe.TAM_NGUNG);
        assertTrue(service.searchChuyenXe("MD", "DL", date).isEmpty());
        assertEquals(409, assertThrows(DomainException.class, () -> service.createChuyenXe(
            new Inputs.ChuyenXe("C2", "R", date, null, null))).status());
    }

    @Test void routeAndStopsCannotBeChangedAfterTripsExistButStatusCanChange() {
        service.createTuyenXe(route("R"));
        service.addDiemDung("R", new Inputs.DiemDung("A", "A", "A", null, 0, null));
        service.createChuyenXe(new Inputs.ChuyenXe("C", "R", LocalDate.of(2030, 1, 1), null, null));
        assertEquals(409, assertThrows(DomainException.class, () -> service.updateBenXe("MD",
            new Inputs.BenXe(null, "Bến đổi tên", "Địa chỉ mới", null, "LD", TrangThaiBenXe.HOAT_DONG))).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.updateTuyenXe("R", route("R"))).status());
        assertEquals(409, assertThrows(DomainException.class,
            () -> service.setTramDauCuoi("R", new Inputs.TramDauCuoi("MD", "DL"))).status());
        assertEquals(409, assertThrows(DomainException.class,
            () -> service.addDiemDung("R", new Inputs.DiemDung("B", "B", "B", null, 0, null))).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.deleteDiemDung("R", "A")).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.updateDiemDung("R", "A",
            new Inputs.CapNhatDiemDung("A", "Địa chỉ", 1, LoaiDiemDung.CA_HAI))).status());
        assertEquals(409, assertThrows(DomainException.class,
            () -> service.reorderDiemDung("R", new Inputs.SapXepDiemDung(List.of("A")))).status());
        assertEquals(409, assertThrows(DomainException.class, () -> service.deleteTuyenXe("R")).status());
        service.setTrangThaiTuyenXe("R", TrangThaiTuyenXe.TAM_NGUNG);
        assertEquals(1, service.getTuyenXe("R").get("tramTrungGian") instanceof List<?> l ? l.size() : -1);
        assertEquals(409, assertThrows(DomainException.class,
            () -> service.createChuyenXe(new Inputs.ChuyenXe("C2", "R", LocalDate.of(2030, 1, 1), null, null))).status());
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
        assertEquals(400, assertThrows(DomainException.class, () -> service.createTuyenXe(new Inputs.TuyenXe("R", "Test", "MD", "DL", null, null,
            null, BigDecimal.TEN, null, TrangThaiTuyenXe.DANG_KHAI_THAC, null))).status());
        service.createTuyenXe(new Inputs.TuyenXe("R", "Test", "MD", "DL", null, null,
            BigDecimal.ONE, BigDecimal.TEN, null, TrangThaiTuyenXe.DANG_KHAI_THAC, null));
        LocalDate date = LocalDate.of(2030, 1, 1);
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
    @Test void searchAcceptsCityStationAndLegacyCodesWithoutChangingSaleRules() {
        LocalDate day = LocalDate.of(2030, 1, 1);
        service.createTuyenXe(route("SEARCH_ROUTE"));
        service.createChuyenXe(new Inputs.ChuyenXe("SEARCH_TRIP", "SEARCH_ROUTE", day, LocalTime.of(8, 0), null));
        PublishedTripFixture.open(factory, "SEARCH_TRIP");
        for (String from : List.of("HCM", "CITY:HCM", "STATION:MD")) {
            for (String to : List.of("LD", "CITY:LD", "STATION:DL")) {
                List<Map<String, Object>> trips = service.searchChuyenXe(from, to, day);
                assertEquals(1, trips.size(), from + " -> " + to);
                Map<String, Object> trip = trips.getFirst();
                assertEquals("SEARCH_TRIP", trip.get("maChuyen"));
                assertEquals("MD", trip.get("maBenDi"));
                assertEquals("DL", trip.get("maBenDen"));
                assertEquals("CON_CHO", trip.get("trangThaiNhanDatVe"));
                assertEquals(true, trip.get("coTheDatVe"));
            }
        }
        service.transition("SEARCH_TRIP", TrangThaiChuyen.DANG_CHAY);
        assertTrue(service.searchChuyenXe("CITY:HCM", "CITY:LD", day).isEmpty());
        service.transition("SEARCH_TRIP", TrangThaiChuyen.HOAN_TAT);
        assertTrue(service.searchChuyenXe("STATION:MD", "STATION:DL", day).isEmpty());
    }

}
