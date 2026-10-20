package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.EmployeeInputs;
import com.datvexe.repository.NhanVienRepository;
import com.datvexe.security.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Employee profiles use the same authentication and operator scope as T09. */
public final class EmployeeService {
    private final EntityManagerFactory factory;
    public EmployeeService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    private <T> T tx(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            var transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                T result = action.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    private static NguoiDung caller(EntityManager em) {
        var context = RequestIdentity.get();
        return context == null ? null : AccessControl.authenticated(em, context.user(), Instant.now());
    }

    private static void scope(NguoiDung user, NhanVien employee, boolean write) {
        // HTTP requests pass AuthFilter; internal service calls may have no request identity.
        if (user == null) {
            return;
        }
        if (!write && user.loaiTaiKhoan == LoaiTaiKhoan.NHAN_VIEN) {
            if (user.nhanVien == null || !user.nhanVien.maNhanVien.equals(employee.maNhanVien)) {
                throw AccessControl.denied();
            }
        } else {
            AccessControl.operator(user, employee.nhaXe);
        }
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new DomainException(400, "Thông tin bắt buộc bị thiếu hoặc quá dài.");
        }
        return value.strip();
    }

    private static String optional(String value, int max) {
        return value == null || value.isBlank() ? null : text(value, max);
    }

    private static Map<String, Object> view(NhanVien employee) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maNhanVien", employee.maNhanVien);
        result.put("hoTen", employee.hoTen);
        result.put("soDienThoai", employee.soDienThoai);
        result.put("email", employee.email);
        result.put("ngaySinh", employee.ngaySinh);
        result.put("maNhaXe", employee.nhaXe == null ? null : employee.nhaXe.maNhaXe);
        result.put("loaiNhanVien", employee.loaiNhanVien);
        result.put("trangThai", employee.trangThai);
        result.put("soBangLai", employee.soBangLai);
        result.put("ngayHetHanBangLai", employee.ngayHetHanBangLai);
        return result;
    }

    public List<Map<String, Object>> list(String query, String owner, String role, String state) {
        return tx(em -> {
            NguoiDung user = caller(em);
            if (user != null) {
                AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE, LoaiTaiKhoan.NHAN_VIEN);
            }
            if (user != null && user.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE && owner != null
                && (user.nhaXe == null || !owner.equals(user.nhaXe.maNhaXe))) {
                throw AccessControl.denied();
            }
            LoaiNhanVien kind = role == null ? null : LoaiNhanVien.valueOf(role);
            TrangThaiNhanVien status = state == null ? null : TrangThaiNhanVien.valueOf(state);
            String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
            List<Map<String, Object>> result = new ArrayList<>();
            for (NhanVien employee : new NhanVienRepository(em).list()) {
                if (employee.loaiNhanVien == LoaiNhanVien.KIEM_VE || !visibleTo(user, employee)) {
                    continue;
                }
                boolean matchesOwner = owner == null
                    || employee.nhaXe != null && employee.nhaXe.maNhaXe.equals(owner);
                boolean matchesRole = kind == null || employee.loaiNhanVien == kind;
                boolean matchesState = status == null || employee.trangThai == status;
                if (matchesOwner && matchesRole && matchesState && matchesSearch(employee, needle)) {
                    result.add(view(employee));
                }
            }
            return result;
        });
    }

    private static boolean visibleTo(NguoiDung user, NhanVien employee) {
        if (user == null || user.loaiTaiKhoan == LoaiTaiKhoan.ADMIN) {
            return true;
        }
        if (user.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE) {
            return user.nhaXe != null && employee.nhaXe != null
                && employee.nhaXe.maNhaXe.equals(user.nhaXe.maNhaXe);
        }
        return user.loaiTaiKhoan == LoaiTaiKhoan.NHAN_VIEN && user.nhanVien != null
            && employee.maNhanVien.equals(user.nhanVien.maNhanVien);
    }

    private static boolean matchesSearch(NhanVien employee, String needle) {
        String searchable = employee.hoTen + " " + employee.maNhanVien + " "
            + employee.email + " " + employee.soDienThoai;
        return searchable.toLowerCase(Locale.ROOT).contains(needle);
    }

    public Map<String, Object> get(String id) {
        return tx(em -> {
            NhanVien employee = AccessControl.resource(em, NhanVien.class, text(id, 40));
            scope(caller(em), employee, false);
            return view(employee);
        });
    }

    public Map<String, Object> eligibility(String id, String tripId, VaiTroChuyen role) {
        return tx(em -> {
            NguoiDung user = caller(em);
            if (user != null) {
                AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
            }
            NhanVien employee = AccessControl.resource(em, NhanVien.class, text(id, 40));
            scope(user, employee, true);
            ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, text(tripId, 40));
            if (user != null) {
                AccessControl.operator(user, trip.tuyenXe.nhaXe);
            }
            if (!TripSaleRules.scheduled(trip)) {
                throw new DomainException(409, "Chỉ phân công chuyến chưa khởi hành.");
            }
            AssignmentRules.validate(em, trip, employee, role, null);
            boolean needsPlan = AssignmentRules.needsDrivingPlan(trip);
            return Map.of("maNhanVien", id, "maChuyen", tripId, "duDieuKienCoBan", true,
                "canKiemTraKeHoachLaiNghi", needsPlan, "heThongKiemChungGioNghi", false,
                "canhBao", needsPlan ? "Người điều phối cần kiểm tra kế hoạch nghỉ/đổi tài xế."
                    : "Hệ thống chưa lưu ca lái thực tế để kiểm chứng giờ nghỉ.");
        });
    }

    public List<Map<String, Object>> assignments(String id) {
        return tx(em -> {
            NhanVien employee = AccessControl.resource(em, NhanVien.class, text(id, 40));
            scope(caller(em), employee, false);
            List<PhanCongChuyenXe> schedule = new ArrayList<>();
            for (PhanCongChuyenXe assignment : employee.danhSachPhanCong) {
                if (assignment.vaiTro == VaiTroChuyen.KIEM_VE) {
                    continue;
                }
                assignment.chuyenXe = (ChuyenXe) org.hibernate.Hibernate.unproxy(assignment.chuyenXe);
                schedule.add(assignment);
            }
            schedule.sort(Comparator.comparing((PhanCongChuyenXe assignment) -> assignment.chuyenXe.ngayKhoiHanh)
                .thenComparing(assignment -> assignment.chuyenXe.gioKhoiHanh));
            List<Map<String, Object>> result = new ArrayList<>();
            for (PhanCongChuyenXe assignment : schedule) {
                result.add(assignmentView(employee, assignment));
            }
            return result;
        });
    }

    private static Map<String, Object> assignmentView(NhanVien employee, PhanCongChuyenXe assignment) {
        ChuyenXe trip = (ChuyenXe) org.hibernate.Hibernate.unproxy(assignment.chuyenXe);
        trip.tuyenXe = (TuyenXe) org.hibernate.Hibernate.unproxy(trip.tuyenXe);
        Map<String, Object> result = new LinkedHashMap<>(Map.<String, Object>of(
            "maPhanCong", assignment.maPhanCong,
            "maChuyen", trip.maChuyen,
            "maNhanVien", employee.maNhanVien,
            "vaiTro", assignment.vaiTro,
            "ngayKhoiHanh", trip.ngayKhoiHanh,
            "gioKhoiHanh", trip.gioKhoiHanh,
            "gioDenDuKien", trip.gioDenDuKien,
            "trangThai", trip.trangThai));
        result.put("ghiChu", assignment.ghiChu);
        result.put("tenTuyen", trip.tuyenXe.tenTuyen);
        return result;
    }

    public Map<String, Object> save(String id, EmployeeInputs.Employee input) {
        return tx(em -> {
            if (input == null) {
                throw new DomainException(400, "Thiếu nội dung yêu cầu.");
            }
            NguoiDung user = caller(em);
            authorizeWrite(em, user);
            NhanVien employee = id == null ? new NhanVien()
                : AccessControl.resource(em, NhanVien.class, text(id, 40));
            if (id != null) {
                scope(user, employee, true);
            }
            if (input.loaiNhanVien() == LoaiNhanVien.KIEM_VE) {
                throw new DomainException(400, "Loại nhân viên không còn được hỗ trợ.");
            }
            NhaXe owner = AccessControl.resource(em, NhaXe.class, text(input.maNhaXe(), 40));
            if (user != null) {
                AccessControl.operator(user, owner);
            }
            validateIdentity(em, id, employee, owner, input);
            Contact contact = validateProfile(input);
            NhanVienRepository employees = new NhanVienRepository(em);
            if (employees.contactInUse(employee.maNhanVien, contact.email(), contact.phone())) {
                throw new DomainException(409, "Email hoặc điện thoại đã được sử dụng.");
            }
            validateActiveAssignments(employee, input, contact.license());
            applyProfile(employee, owner, input, contact);
            if (id == null) {
                em.persist(employee);
            }
            em.flush();
            return view(employee);
        });
    }

    public void delete(String id) {
        tx(em -> {
            NhanVien employee = AccessControl.resource(em, NhanVien.class, text(id, 40));
            NguoiDung user = caller(em);
            scope(user, employee, true);
            if (user != null) {
                AccessControl.activeOperator(em, user);
            }
            NhanVienRepository employees = new NhanVienRepository(em);
            if (!employee.danhSachPhanCong.isEmpty() || employees.hasLinkedAccount(employee)) {
                throw new DomainException(409, "Nhân viên có phân công hoặc tài khoản; không được xóa cứng.");
            }
            em.remove(employee);
            return null;
        });
    }

    private static void authorizeWrite(EntityManager em, NguoiDung user) {
        if (user != null) {
            AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
            AccessControl.activeOperator(em, user);
        }
    }

    private static void validateIdentity(EntityManager em, String id, NhanVien employee,
                                         NhaXe owner, EmployeeInputs.Employee input) {
        if (id == null) {
            employee.maNhanVien = text(input.maNhanVien(), 40);
            if (em.find(NhanVien.class, employee.maNhanVien) != null) {
                throw new DomainException(409, "Mã nhân viên đã tồn tại.");
            }
            return;
        }
        if (input.maNhanVien() != null && !id.equals(input.maNhanVien())) {
            throw new DomainException(400, "Không đổi mã nhân viên.");
        }
        String currentOwner = employee.nhaXe == null ? null : employee.nhaXe.maNhaXe;
        String requestedOwner = owner == null ? null : owner.maNhaXe;
        if (!Objects.equals(currentOwner, requestedOwner)) {
            throw new DomainException(409, "Không chuyển phạm vi nhà xe của hồ sơ.");
        }
    }

    private record Contact(String phone, String email, String license) {}

    private static Contact validateProfile(EmployeeInputs.Employee input) {
        if (input.loaiNhanVien() == null || input.trangThai() == null) {
            throw new DomainException(400, "Thiếu vai trò hoặc trạng thái.");
        }
        String phone = text(input.soDienThoai(), 30);
        String email = text(input.email(), 254).toLowerCase(Locale.ROOT);
        if (!phone.matches("0[0-9]{9}") || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new DomainException(400, "Điện thoại hoặc email không hợp lệ.");
        }
        if (input.ngaySinh() != null
            && input.ngaySinh().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new DomainException(400, "Ngày sinh không hợp lệ.");
        }
        String license = optional(input.soBangLai(), 80);
        if (input.loaiNhanVien() == LoaiNhanVien.TAI_XE
            && (license == null || input.ngayHetHanBangLai() == null)) {
            throw new DomainException(400, "Tài xế cần số và hạn bằng lái.");
        }
        return new Contact(phone, email, license);
    }

    private static void validateActiveAssignments(NhanVien employee, EmployeeInputs.Employee input,
                                                   String license) {
        for (PhanCongChuyenXe assignment : employee.danhSachPhanCong) {
            ChuyenXe trip = assignment.chuyenXe;
            if (!TripSaleRules.scheduled(trip) && trip.trangThai != TrangThaiChuyen.DANG_CHAY) {
                continue;
            }
            if (input.trangThai() != TrangThaiNhanVien.DANG_LAM_VIEC
                || input.loaiNhanVien() != employee.loaiNhanVien) {
                throw new DomainException(409, "Cần xử lý phân công đang hoạt động trước khi đổi vai trò/trạng thái.");
            }
            if (input.loaiNhanVien() == LoaiNhanVien.TAI_XE
                && (license == null || input.ngayHetHanBangLai() == null
                    || input.ngayHetHanBangLai().isBefore(trip.gioDenDuKien.toLocalDate()))) {
                throw new DomainException(409, "Bằng lái không bao phủ chuyến đã phân công.");
            }
        }
    }

    private static void applyProfile(NhanVien employee, NhaXe owner,
                                     EmployeeInputs.Employee input, Contact contact) {
        employee.hoTen = text(input.hoTen(), 160);
        employee.soDienThoai = contact.phone();
        employee.email = contact.email();
        employee.ngaySinh = input.ngaySinh();
        employee.nhaXe = owner;
        employee.loaiNhanVien = input.loaiNhanVien();
        employee.trangThai = input.trangThai();
        employee.soBangLai = contact.license();
        employee.ngayHetHanBangLai = input.ngayHetHanBangLai();
    }
}
