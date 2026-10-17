package com.datvexe.service;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.OperatorInputs;
import com.datvexe.dto.Inputs;
import com.datvexe.security.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OperatorServiceTest {
    private EntityManagerFactory factory;
    private OperatorService service;
    private CurrentUser admin;
    private CurrentUser customer;

    @BeforeEach void setup() {
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
            "jakarta.persistence.jdbc.driver", "org.h2.Driver", "jakarta.persistence.jdbc.url", "jdbc:h2:mem:operators;MODE=PostgreSQL",
            "jakarta.persistence.jdbc.user", "sa", "jakarta.persistence.jdbc.password", "", "hibernate.hbm2ddl.auto", "create-drop"));
        service = new OperatorService(factory);
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            admin = account(em, "AD", LoaiTaiKhoan.ADMIN);
            customer = account(em, "KH", LoaiTaiKhoan.KHACH_HANG);
            em.getTransaction().commit();
        }
    }
    private CurrentUser account(EntityManager em, String id, LoaiTaiKhoan role) {
        NguoiDung n = new NguoiDung(); n.maNguoiDung = id; n.hoTen = id;
        n.email = id + "@example.test"; n.soDienThoai = id; n.matKhau = "fixture";
        n.loaiTaiKhoan = role; n.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
        em.persist(n);
        AuthSession session = new AuthSession(); session.tokenHash = id; session.nguoiDung = n;
        session.csrfToken = "csrf"; session.taoLuc = Instant.now(); session.hetHan = Instant.now().plusSeconds(3600);
        em.persist(session);
        return new CurrentUser(id, role, null, null, id);
    }
    @AfterEach void close() { factory.close(); }
    private OperatorInputs.NhaXe form(String id) {
        return new OperatorInputs.NhaXe(id, "Nhà xe " + id, "0901234567", "nx@example.test", "Địa chỉ", null, null, null);
    }
    private void rejects(int status, Runnable action) {
        assertEquals(status, assertThrows(AccountException.class, action::run).status());
    }
    @Test void profileLifecycleAndAdminOnlyAccess() {
        rejects(403, () -> service.create(customer, form("NX1")));
        var created = service.create(admin, form("NX1"));
        assertEquals(TrangThaiNhaXe.CHO_PHE_DUYET, created.trangThai());
        assertEquals(1, service.list(admin, "NX1", null, 1, 20).totalItems());
        rejects(409, () -> service.create(admin, form("NX1")));
        rejects(400, () -> service.update(admin, "NX1", form("NX2")));
        assertEquals("Tên mới", service.update(admin, "NX1", new OperatorInputs.NhaXe(null, "Tên mới", null, null, null, null, null, null)).tenNhaXe());
        assertEquals(TrangThaiNhaXe.TAM_NGUNG,
            service.state(admin, "NX1", new OperatorInputs.TrangThai(TrangThaiNhaXe.TAM_NGUNG)).nhaXe().trangThai());
        assertEquals(1, service.list(admin, null, TrangThaiNhaXe.TAM_NGUNG, 1, 20).totalItems());
        rejects(400, () -> service.list(admin, null, null, 0, 20));
        service.delete(admin, "NX1");
        rejects(404, () -> service.get(admin, "NX1"));
    }
    @Test void managerGrantRevokesSessionsAndNeverCrossesOperator() {
        service.create(admin, form("NX1")); service.create(admin, form("NX2"));
        assertEquals("KH", service.grant(admin, "NX1", new OperatorInputs.QuanLy("KH")).maNguoiDung());
        rejects(401, () -> service.get(customer, "NX1"));
        assertEquals(1, service.managers(admin, "NX1").size());
        rejects(409, () -> service.delete(admin, "NX1"));
        rejects(409, () -> service.grant(admin, "NX2", new OperatorInputs.QuanLy("KH")));
        rejects(403, () -> service.revoke(admin, "NX2", "KH"));
        service.revoke(admin, "NX1", "KH");
        assertTrue(service.managers(admin, "NX1").isEmpty());
        try (EntityManager em = factory.createEntityManager()) {
            NguoiDung n = em.find(NguoiDung.class, "KH");
            assertEquals(LoaiTaiKhoan.KHACH_HANG, n.loaiTaiKhoan);
            assertNull(n.nhaXe);
            assertNull(em.find(AuthSession.class, "KH"));
        }
    }
    @Test void suspendedOperatorCanReadOwnHistoryButNotWrite() {
        service.create(admin, form("NX1"));
        service.state(admin, "NX1", new OperatorInputs.TrangThai(TrangThaiNhaXe.TAM_NGUNG));
        try (EntityManager em = factory.createEntityManager()) {
            NhaXe x = em.find(NhaXe.class, "NX1");
            NguoiDung manager = new NguoiDung(); manager.maNguoiDung = "NX"; manager.hoTen = "NX";
            manager.email = "manager@example.test"; manager.soDienThoai = "NX"; manager.matKhau = "fixture";
            manager.loaiTaiKhoan = LoaiTaiKhoan.NHA_XE; manager.trangThai = TrangThaiTaiKhoan.HOAT_DONG; manager.nhaXe = x;
            AccessControl.operator(manager, x);
            rejects(403, () -> AccessControl.activeOperator(manager));
        }
    }
    @Test void suspendingOperatorPreventsNewTripsWithoutChangingExistingTrips() {
        service.create(admin, form("NX1"));
        service.state(admin, "NX1", new OperatorInputs.TrangThai(TrangThaiNhaXe.HOAT_DONG));
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            TinhThanh city = new TinhThanh(); city.maTinhThanh = "CITY"; city.tenTinhThanh = "City"; em.persist(city);
            BenXe start = new BenXe(); start.maBenXe = "START"; start.tenBenXe = "Start";
            start.diaChi = "Start"; start.tinhThanh = city; em.persist(start);
            BenXe end = new BenXe(); end.maBenXe = "END"; end.tenBenXe = "End";
            end.diaChi = "End"; end.tinhThanh = city; em.persist(end);
            TuyenXe route = new TuyenXe(); route.maTuyen = "R"; route.tenTuyen = "Route";
            route.tramKhoiHanh = start; route.tramDen = end; route.nhaXe = em.find(NhaXe.class, "NX1");
            route.thoiGianDuKien = BigDecimal.ONE; route.giaCoBan = BigDecimal.TEN;
            route.trangThai = TrangThaiTuyenXe.DANG_KHAI_THAC; em.persist(route);
            em.getTransaction().commit();
        }
        RouteTripService trips = new RouteTripService(factory);
        trips.createChuyenXe(new Inputs.ChuyenXe("C1", "R", LocalDate.of(2030, 1, 1), LocalTime.NOON, null));
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            LocalDateTime businessNow = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            NhaXe operator = em.find(NhaXe.class, "NX1");
            ChuyenXe trip = em.find(ChuyenXe.class, "C1");
            Xe vehicle = new Xe(); vehicle.maXe = "X"; vehicle.bienSo = "TEST-X";
            vehicle.soChoNgoi = 2; vehicle.nhaXe = operator;
            var type=new LoaiXe();type.maLoaiXe="PUB_L";type.tenLoaiXe="Two seats";type.soChoNgoi=2;em.persist(type);
            vehicle.loaiXe=type;vehicle.ngayDangKiem=LocalDate.of(2020,1,1);vehicle.hanDangKiem=LocalDate.of(2100,1,1);em.persist(vehicle);trip.xe=vehicle;
            Ghe seat = new Ghe(); seat.maGhe = "G"; seat.viTri = "1"; seat.xe = vehicle; em.persist(seat);
            Ghe heldSeat = new Ghe(); heldSeat.maGhe = "G2"; heldSeat.viTri = "2"; heldSeat.xe = vehicle; em.persist(heldSeat);
            DonDatVe order = new DonDatVe(); order.maDon = "O"; order.nguoiDat = em.find(NguoiDung.class, "KH");
            order.ngayDat = businessNow; order.khachDatTen = "Khách";
            order.khachDatSoDienThoai = "0900000000"; order.trangThai = TrangThaiDon.DA_THANH_TOAN; em.persist(order);
            VeXe ticket = new VeXe(); ticket.maVe = "V"; ticket.chuyenXe = trip; ticket.ghe = seat;
            ticket.donDatVe = order; ticket.hoTenKhachHang = "Khách"; ticket.soDienThoaiKhachHang = "0900000000";
            ticket.giaVe = BigDecimal.TEN; ticket.ngayXuatVe = businessNow; em.persist(ticket);
            PhuongThucThanhToan method = new PhuongThucThanhToan(); method.maPhuongThuc = "PT";
            method.tenPhuongThuc = "Thử nghiệm"; em.persist(method);
            ThanhToan payment = new ThanhToan(); payment.maGiaoDich = "GD"; payment.donDatVe = order;
            payment.phuongThucThanhToan = method; payment.soTien = BigDecimal.TEN;
            payment.trangThai = TrangThaiThanhToan.THANH_CONG; em.persist(payment);
            GiuCho hold = new GiuCho(); hold.maGiuCho = "H"; hold.chuyenXe = trip; hold.ghe = heldSeat;
            hold.ngayDat = businessNow; hold.thoiDiemBatDau = businessNow;
            hold.thoiDiemHetHan = businessNow.plusMinutes(15); em.persist(hold);
            em.getTransaction().commit();
        }
        PublishedTripFixture.open(factory,"C1");
        assertEquals(1, trips.searchChuyenXe("START", "END", LocalDate.of(2030, 1, 1)).size());
        assertEquals(1, service.impact(admin, "NX1").chuyenSapKhoiHanh());
        var changed = service.state(admin, "NX1", new OperatorInputs.TrangThai(TrangThaiNhaXe.TAM_NGUNG));
        assertEquals(1, changed.anhHuong().chuyenSapKhoiHanh());
        assertEquals(1, changed.anhHuong().giuChoDangGiu());
        assertEquals(1, changed.anhHuong().veDaPhatHanh());
        assertEquals(1, changed.anhHuong().veCoThanhToanThanhCong());
        assertEquals(TrangThaiNhaXe.TAM_NGUNG, changed.nhaXe().trangThai());
        assertEquals(409, assertThrows(DomainException.class, () -> trips.createChuyenXe(
            new Inputs.ChuyenXe("C2", "R", LocalDate.of(2030, 1, 2), LocalTime.NOON, null))).status());
        assertEquals("C1", trips.getChuyenXe("C1").get("maChuyen"));
        assertTrue(trips.searchChuyenXe("START", "END", LocalDate.of(2030, 1, 1)).isEmpty());
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            assertEquals(409, assertThrows(AccountException.class, () -> AccessControl.saleAllowed(em, "C1")).status());
            assertEquals(TrangThaiDon.DA_THANH_TOAN, em.find(DonDatVe.class, "O").trangThai);
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, "V").trangThai);
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, "GD").trangThai);
            em.getTransaction().rollback();
        }
    }
}
