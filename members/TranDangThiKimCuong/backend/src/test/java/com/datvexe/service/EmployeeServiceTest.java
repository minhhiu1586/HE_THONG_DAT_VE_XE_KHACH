package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.EmployeeInputs;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {
    private EntityManagerFactory factory;
    private EmployeeService employees;
    @BeforeEach void setup() {
        factory=Persistence.createEntityManagerFactory("datvexe",Map.of(
            "jakarta.persistence.jdbc.driver","org.h2.Driver",
            "jakarta.persistence.jdbc.url","jdbc:h2:mem:employee;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "jakarta.persistence.jdbc.user","sa","jakarta.persistence.jdbc.password","",
            "hibernate.hbm2ddl.auto","create-drop"));
        employees=new EmployeeService(factory);
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            for(String id:new String[]{"NX","OTHER"}) { NhaXe owner=new NhaXe(); owner.maNhaXe=id; owner.tenNhaXe=id; em.persist(owner); }
            em.getTransaction().commit();
        }
    }
    @AfterEach void close() { com.datvexe.security.RequestIdentity.clear(); factory.close(); }
    private EmployeeInputs.Employee input(String id,String email,String owner,TrangThaiNhanVien state) {
        return new EmployeeInputs.Employee(id,"Nguyễn Văn An","0912345678",email,null,owner,
            LoaiNhanVien.TAI_XE,state,"123456789012",LocalDate.of(2035,1,1));
    }
    @Test void profileCrudFiltersAndStableIdentity() {
        employees.save(null,input("E","a@example.test","NX",TrangThaiNhanVien.DANG_LAM_VIEC));
        assertEquals(1,employees.list("NGUYỄN","NX","TAI_XE",null).size());
        assertTrue(employees.list(null,"OTHER",null,null).isEmpty());
        employees.save("E",input("E","a@example.test","NX",TrangThaiNhanVien.TAM_NGHI));
        assertEquals(TrangThaiNhanVien.TAM_NGHI,employees.get("E").get("trangThai"));
        assertEquals(409,assertThrows(DomainException.class,()->employees.save("E",input("E","a@example.test","OTHER",TrangThaiNhanVien.TAM_NGHI))).status());
        employees.delete("E"); assertTrue(employees.list(null,null,null,null).isEmpty());
    }
    @Test void duplicateContactAndMissingOwnerRollback() {
        employees.save(null,input("E","a@example.test","NX",TrangThaiNhanVien.DANG_LAM_VIEC));
        assertEquals(409,assertThrows(DomainException.class,()->employees.save(null,input("E2","a@example.test","NX",TrangThaiNhanVien.DANG_LAM_VIEC))).status());
        assertEquals(404,assertThrows(DomainException.class,()->employees.save(null,input("E3","b@example.test","MISSING",TrangThaiNhanVien.DANG_LAM_VIEC))).status());
        assertEquals(1,employees.list(null,null,null,null).size());
    }
    @Test void driverRequiresLicenseAndInputValidation() {
        var invalid=new EmployeeInputs.Employee("E","Name","wrong","a@example.test",null,"NX",LoaiNhanVien.TAI_XE,TrangThaiNhanVien.DANG_LAM_VIEC,null,null);
        assertEquals(400,assertThrows(DomainException.class,()->employees.save(null,invalid)).status());
        var missing=new EmployeeInputs.Employee("E","Name","0912345678","a@example.test",null,"NX",LoaiNhanVien.TAI_XE,TrangThaiNhanVien.DANG_LAM_VIEC,null,null);
        assertEquals(400,assertThrows(DomainException.class,()->employees.save(null,missing)).status());
        assertTrue(employees.list(null,null,null,null).isEmpty());
    }
    @Test
    void contactNormalizationExcludesSelfButRejectsEachDuplicateIndependently() {
        var first = input("E", "  A@EXAMPLE.TEST  ", "NX", TrangThaiNhanVien.DANG_LAM_VIEC);
        assertEquals("a@example.test", employees.save(null, first).get("email"));
        assertDoesNotThrow(() -> employees.save("E", first));

        var duplicateEmail = new EmployeeInputs.Employee("E2", "Người khác", "0912345680",
            "A@example.test", null, "NX", LoaiNhanVien.PHU_XE,
            TrangThaiNhanVien.DANG_LAM_VIEC, null, null);
        assertEquals(409, assertThrows(DomainException.class,
            () -> employees.save(null, duplicateEmail)).status());

        var duplicatePhone = new EmployeeInputs.Employee("E3", "Người khác", "0912345678",
            "other@example.test", null, "NX", LoaiNhanVien.PHU_XE,
            TrangThaiNhanVien.DANG_LAM_VIEC, null, null);
        assertEquals(409, assertThrows(DomainException.class,
            () -> employees.save(null, duplicatePhone)).status());
        assertEquals(1, employees.list(null, null, null, null).size());
    }

    @Test
    void invalidUpdateRollsBackProfileAndNamesMayRepeat() {
        employees.save(null, input("E", "a@example.test", "NX", TrangThaiNhanVien.DANG_LAM_VIEC));
        var invalid = new EmployeeInputs.Employee("E", "Tên mới", "0912345680", "new@example.test",
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1), "NX", LoaiNhanVien.TAI_XE,
            TrangThaiNhanVien.DANG_LAM_VIEC, "LICENSE", LocalDate.of(2035, 1, 1));
        assertEquals(400, assertThrows(DomainException.class, () -> employees.save("E", invalid)).status());
        assertEquals("a@example.test", employees.get("E").get("email"));
        assertEquals("0912345678", employees.get("E").get("soDienThoai"));

        var sameName = new EmployeeInputs.Employee("E2", "Nguyễn Văn An", "0912345680",
            "other@example.test", null, "NX", LoaiNhanVien.PHU_XE,
            TrangThaiNhanVien.DANG_LAM_VIEC, null, null);
        assertDoesNotThrow(() -> employees.save(null, sameName));
        assertEquals(2, employees.list(null, null, null, null).size());
    }

    private void identity(LoaiTaiKhoan role,String owner,String employee) {
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            NguoiDung user=new NguoiDung(); user.maNguoiDung="CALLER"; user.hoTen="Caller";
            user.soDienThoai="0987654321"; user.email="caller@example.test"; user.matKhau="test";
            user.loaiTaiKhoan=role; user.trangThai=TrangThaiTaiKhoan.HOAT_DONG;
            user.nhaXe=owner==null?null:em.find(NhaXe.class,owner);
            user.nhanVien=employee==null?null:em.find(NhanVien.class,employee); em.persist(user);
            var session=new com.datvexe.domain.AuthSession(); session.tokenHash="SESSION"; session.csrfToken="CSRF";
            session.nguoiDung=user; session.taoLuc=Instant.now(); session.hetHan=Instant.now().plusSeconds(3600); em.persist(session);
            em.getTransaction().commit();
        }
        var current=new com.datvexe.security.CurrentUser("CALLER",role,owner,employee,"SESSION");
        com.datvexe.security.RequestIdentity.set(new com.datvexe.security.RequestIdentity.Context(current,"GET","/api/nhan-vien",null));
    }
    @Test void operatorCannotReadOrEditOtherOperatorEmployees() {
        employees.save(null,input("E","a@example.test","OTHER",TrangThaiNhanVien.DANG_LAM_VIEC));
        identity(LoaiTaiKhoan.NHA_XE,"NX",null);
        assertTrue(employees.list(null,null,null,null).isEmpty());
        assertEquals(403,assertThrows(DomainException.class,()->employees.get("E")).status());
        assertEquals(403,assertThrows(DomainException.class,()->employees.save("E",input("E","a@example.test","OTHER",TrangThaiNhanVien.TAM_NGHI))).status());
    }
    @Test void staffReadsOwnProfileButCannotWriteOrDeleteLinkedProfile() {
        employees.save(null,input("E","a@example.test","NX",TrangThaiNhanVien.DANG_LAM_VIEC));
        identity(LoaiTaiKhoan.NHAN_VIEN,null,"E");
        assertEquals(1,employees.list(null,null,null,null).size());
        assertEquals("E",employees.get("E").get("maNhanVien"));
        assertEquals(403,assertThrows(DomainException.class,()->employees.save("E",input("E","a@example.test","NX",TrangThaiNhanVien.TAM_NGHI))).status());
        com.datvexe.security.RequestIdentity.clear();
        assertEquals(409,assertThrows(DomainException.class,()->employees.delete("E")).status());
    }
    @Test void crewRequirementsAndEmployeeChangesPreserveActiveAssignments() {
        employees.save(null,input("E","a@example.test","NX",TrangThaiNhanVien.DANG_LAM_VIEC));
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            var city=new TinhThanh(); city.maTinhThanh="T"; city.tenTinhThanh="T"; em.persist(city);
            var a=new BenXe(); a.maBenXe="A"; a.tenBenXe="A"; a.diaChi="A"; a.tinhThanh=city; em.persist(a);
            var b=new BenXe(); b.maBenXe="B"; b.tenBenXe="B"; b.diaChi="B"; b.tinhThanh=city; em.persist(b);
            var route=new TuyenXe(); route.maTuyen="R"; route.tenTuyen="R"; route.tramKhoiHanh=a; route.tramDen=b;
            route.giaCoBan=java.math.BigDecimal.TEN; route.nhaXe=em.find(NhaXe.class,"NX"); em.persist(route);
            var trip=new ChuyenXe(); trip.maChuyen="C"; trip.tuyenXe=route; trip.ngayKhoiHanh=LocalDate.of(2030,1,1);
            trip.gioKhoiHanh=LocalTime.of(8,0); trip.gioDenDuKien=LocalDateTime.of(2030,1,1,12,0); em.persist(trip);
            var main=new PhanCongChuyenXe(); main.maPhanCong="MAIN"; main.chuyenXe=trip;
            main.nhanVien=em.find(NhanVien.class,"E"); main.vaiTro=VaiTroChuyen.TAI_XE_CHINH; em.persist(main);
            assertThrows(DomainException.class,()->AssignmentRules.readyForOperation(em,trip));
            var inspector=new NhanVien(); inspector.maNhanVien="CHECKER"; inspector.hoTen="Kiểm vé";
            inspector.email="checker@example.test"; inspector.soDienThoai="0912345681";
            inspector.loaiNhanVien=LoaiNhanVien.KIEM_VE; em.persist(inspector);
            assertThrows(DomainException.class,()->AssignmentRules.validate(em,trip,inspector,VaiTroChuyen.KIEM_VE,null));
            assertThrows(DomainException.class,()->AssignmentRules.validate(em,trip,inspector,VaiTroChuyen.TAI_XE_CHINH,null));
            assertThrows(DomainException.class,()->AssignmentRules.validate(em,trip,inspector,VaiTroChuyen.PHU_XE,null));
            var checking=new PhanCongChuyenXe(); checking.maPhanCong="CHECKING"; checking.chuyenXe=trip;
            checking.nhanVien=inspector; checking.vaiTro=VaiTroChuyen.KIEM_VE; em.persist(checking);
            assertThrows(DomainException.class,()->AssignmentRules.readyForOperation(em,trip));
            var assistant=new NhanVien(); assistant.maNhanVien="P"; assistant.hoTen="P"; assistant.email="p@example.test";
            assistant.soDienThoai="0912345679"; assistant.nhaXe=route.nhaXe; assistant.loaiNhanVien=LoaiNhanVien.PHU_XE; em.persist(assistant);
            var support=new PhanCongChuyenXe(); support.maPhanCong="SUPPORT"; support.chuyenXe=trip;
            support.nhanVien=assistant; support.vaiTro=VaiTroChuyen.PHU_XE; em.persist(support);
            assertDoesNotThrow(()->AssignmentRules.readyForOperation(em,trip));
            trip.gioDenDuKien=trip.gioDenDuKien.plusMinutes(1);
            assertThrows(DomainException.class,()->AssignmentRules.readyForOperation(em,trip));
            var second=new NhanVien(); second.maNhanVien="SECOND"; second.hoTen="Second"; second.email="second@example.test";
            second.soDienThoai="0912345680"; second.nhaXe=route.nhaXe; second.loaiNhanVien=LoaiNhanVien.TAI_XE;
            second.soBangLai="987654321012"; second.ngayHetHanBangLai=LocalDate.of(2035,1,1); em.persist(second);
            var relief=new PhanCongChuyenXe(); relief.maPhanCong="RELIEF"; relief.chuyenXe=trip;
            relief.nhanVien=second; relief.vaiTro=VaiTroChuyen.TAI_XE_PHU; em.persist(relief);
            assertThrows(DomainException.class,()->AssignmentRules.readyForOperation(em,trip));
            main.ghiChu=AssignmentRules.reviewPrefix(trip)+"TEST] Đã kiểm tra kế hoạch.";
            assertDoesNotThrow(()->AssignmentRules.readyForOperation(em,trip));
            em.getTransaction().commit();
        }
        assertEquals(409,assertThrows(DomainException.class,()->employees.save("E",input("E","a@example.test","NX",TrangThaiNhanVien.TAM_NGHI))).status());
        assertEquals(TrangThaiNhanVien.DANG_LAM_VIEC,employees.get("E").get("trangThai"));
        assertEquals("R",employees.assignments("E").getFirst().get("tenTuyen"));
        assertEquals(409,assertThrows(DomainException.class,()->employees.delete("E")).status());
        var checkerUpdate=new EmployeeInputs.Employee("CHECKER","Kiểm vé cập nhật","0912345681","checker@example.test",
            null,null,LoaiNhanVien.KIEM_VE,TrangThaiNhanVien.DANG_LAM_VIEC,null,null);
        assertEquals(400,assertThrows(DomainException.class,()->employees.save("CHECKER",checkerUpdate)).status());
        assertTrue(employees.list(null,null,null,null).stream().noneMatch(n->"CHECKER".equals(n.get("maNhanVien"))));
        assertEquals(409,assertThrows(DomainException.class,()->employees.delete("CHECKER")).status());
    }
}
