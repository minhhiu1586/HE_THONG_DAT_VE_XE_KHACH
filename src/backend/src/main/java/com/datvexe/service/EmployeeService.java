package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.EmployeeInputs;
import com.datvexe.security.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Employee profiles use the same authentication and operator scope as T09. */
public final class EmployeeService {
    private final EntityManagerFactory factory;
    public EmployeeService(EntityManagerFactory factory) { this.factory = factory; }
    private <T> T tx(Function<EntityManager,T> action) {
        try (EntityManager em=factory.createEntityManager()) {
            var transaction=em.getTransaction(); transaction.begin();
            try { AccessPolicy.guard(em); T result=action.apply(em); transaction.commit(); return result; }
            catch(RuntimeException error) { if(transaction.isActive()) transaction.rollback(); throw error; }
        }
    }
    private static NguoiDung caller(EntityManager em) {
        var context=RequestIdentity.get();
        return context==null?null:AccessControl.authenticated(em,context.user(),Instant.now());
    }
    private static void scope(NguoiDung user, NhanVien employee, boolean write) {
        if(user==null) return; // trusted service calls; HTTP always passes AuthFilter
        if(!write && user.loaiTaiKhoan==LoaiTaiKhoan.NHAN_VIEN) {
            if(user.nhanVien==null || !user.nhanVien.maNhanVien.equals(employee.maNhanVien)) throw AccessControl.denied();
        } else AccessControl.operator(user,employee.nhaXe);
    }
    private static String text(String value,int max) {
        if(value==null || value.isBlank() || value.strip().length()>max) throw new DomainException(400,"Thông tin bắt buộc bị thiếu hoặc quá dài.");
        return value.strip();
    }
    private static String optional(String value,int max) {
        return value==null || value.isBlank()?null:text(value,max);
    }
    private static Map<String,Object> view(NhanVien employee) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("maNhanVien",employee.maNhanVien); result.put("hoTen",employee.hoTen);
        result.put("soDienThoai",employee.soDienThoai); result.put("email",employee.email);
        result.put("ngaySinh",employee.ngaySinh); result.put("maNhaXe",employee.nhaXe.maNhaXe);
        result.put("loaiNhanVien",employee.loaiNhanVien); result.put("trangThai",employee.trangThai);
        result.put("soBangLai",employee.soBangLai); result.put("ngayHetHanBangLai",employee.ngayHetHanBangLai);
        return result;
    }
    public List<Map<String,Object>> list(String query,String owner,String role,String state) { return tx(em -> {
        NguoiDung user=caller(em);
        if(user!=null) AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE,LoaiTaiKhoan.NHAN_VIEN);
        if(user!=null && user.loaiTaiKhoan==LoaiTaiKhoan.NHA_XE && owner!=null
            && (user.nhaXe==null || !owner.equals(user.nhaXe.maNhaXe))) throw AccessControl.denied();
        LoaiNhanVien kind=role==null?null:LoaiNhanVien.valueOf(role);
        TrangThaiNhanVien status=state==null?null:TrangThaiNhanVien.valueOf(state);
        String needle=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        return new com.datvexe.repository.NhanVienRepository(em).list().stream()
            .filter(n -> user==null || user.loaiTaiKhoan==LoaiTaiKhoan.ADMIN
                || user.loaiTaiKhoan==LoaiTaiKhoan.NHA_XE && user.nhaXe!=null && n.nhaXe.maNhaXe.equals(user.nhaXe.maNhaXe)
                || user.loaiTaiKhoan==LoaiTaiKhoan.NHAN_VIEN && user.nhanVien!=null && n.maNhanVien.equals(user.nhanVien.maNhanVien))
            .filter(n -> owner==null || n.nhaXe.maNhaXe.equals(owner))
            .filter(n -> kind==null || n.loaiNhanVien==kind).filter(n -> status==null || n.trangThai==status)
            .filter(n -> (n.hoTen+" "+n.maNhanVien+" "+n.email+" "+n.soDienThoai).toLowerCase(Locale.ROOT).contains(needle))
            .map(EmployeeService::view).toList();
    }); }
    public Map<String,Object> get(String id) { return tx(em -> {
        NhanVien employee=AccessControl.resource(em,NhanVien.class,text(id,40)); scope(caller(em),employee,false); return view(employee);
    }); }
    public Map<String,Object> eligibility(String id,String tripId,VaiTroChuyen role) { return tx(em -> {
        NguoiDung user=caller(em);
        if(user!=null) AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE);
        NhanVien employee=AccessControl.resource(em,NhanVien.class,text(id,40)); scope(user,employee,true);
        ChuyenXe trip=AccessControl.resource(em,ChuyenXe.class,text(tripId,40));
        if(user!=null) AccessControl.operator(user,trip.tuyenXe.nhaXe);
        if(!TripSaleRules.scheduled(trip)) throw new DomainException(409,"Chỉ phân công chuyến chưa khởi hành.");
        AssignmentRules.validate(em,trip,employee,role,null);
        return Map.of("maNhanVien",id,"maChuyen",tripId,"duDieuKienCoBan",true,
            "canKiemTraKeHoachLaiNghi",AssignmentRules.needsDrivingPlan(trip),"heThongKiemChungGioNghi",false,
            "canhBao",AssignmentRules.needsDrivingPlan(trip)?"Người điều phối cần kiểm tra kế hoạch nghỉ/đổi tài xế.":"Hệ thống chưa lưu ca lái thực tế để kiểm chứng giờ nghỉ.");
    }); }
    public List<Map<String,Object>> assignments(String id) { return tx(em -> {
        NhanVien employee=AccessControl.resource(em,NhanVien.class,text(id,40)); scope(caller(em),employee,false);
        return employee.danhSachPhanCong.stream()
            .map(p -> { p.chuyenXe=(ChuyenXe)org.hibernate.Hibernate.unproxy(p.chuyenXe); return p; })
            .sorted(Comparator.comparing((PhanCongChuyenXe p) -> p.chuyenXe.ngayKhoiHanh).thenComparing(p -> p.chuyenXe.gioKhoiHanh))
            .map(p -> {
                p.chuyenXe=(ChuyenXe)org.hibernate.Hibernate.unproxy(p.chuyenXe);
                p.chuyenXe.tuyenXe=(TuyenXe)org.hibernate.Hibernate.unproxy(p.chuyenXe.tuyenXe);
                Map<String,Object> value=new LinkedHashMap<>(Map.<String,Object>of("maPhanCong",p.maPhanCong,"maChuyen",p.chuyenXe.maChuyen,
                "maNhanVien",employee.maNhanVien,"vaiTro",p.vaiTro,"ngayKhoiHanh",p.chuyenXe.ngayKhoiHanh,
                "gioKhoiHanh",p.chuyenXe.gioKhoiHanh,"gioDenDuKien",p.chuyenXe.gioDenDuKien,
                "trangThai",p.chuyenXe.trangThai));
                value.put("ghiChu",p.ghiChu); value.put("tenTuyen",p.chuyenXe.tuyenXe.tenTuyen); return value;
            }).toList();
    }); }
    public Map<String,Object> save(String id,EmployeeInputs.Employee input) { return tx(em -> {
        if(input==null) throw new DomainException(400,"Thiếu nội dung yêu cầu.");
        NguoiDung user=caller(em);
        if(user!=null) { AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE); AccessControl.activeOperator(em,user); }
        NhanVien employee=id==null?new NhanVien():AccessControl.resource(em,NhanVien.class,text(id,40));
        if(id!=null) scope(user,employee,true);
        NhaXe owner=AccessControl.resource(em,NhaXe.class,text(input.maNhaXe(),40));
        if(user!=null) AccessControl.operator(user,owner);
        if(id==null) {
            employee.maNhanVien=text(input.maNhanVien(),40);
            if(em.find(NhanVien.class,employee.maNhanVien)!=null) throw new DomainException(409,"Mã nhân viên đã tồn tại.");
        } else {
            if(input.maNhanVien()!=null && !id.equals(input.maNhanVien())) throw new DomainException(400,"Không đổi mã nhân viên.");
            if(!employee.nhaXe.maNhaXe.equals(owner.maNhaXe)) throw new DomainException(409,"Không chuyển hồ sơ sang nhà xe khác.");
        }
        if(input.loaiNhanVien()==null || input.trangThai()==null) throw new DomainException(400,"Thiếu vai trò hoặc trạng thái.");
        String phone=text(input.soDienThoai(),30),email=text(input.email(),254).toLowerCase(Locale.ROOT);
        if(!phone.matches("0[0-9]{9}") || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new DomainException(400,"Điện thoại hoặc email không hợp lệ.");
        if(input.ngaySinh()!=null && input.ngaySinh().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) throw new DomainException(400,"Ngày sinh không hợp lệ.");
        String license=optional(input.soBangLai(),80);
        if(input.loaiNhanVien()==LoaiNhanVien.TAI_XE && (license==null || input.ngayHetHanBangLai()==null)) throw new DomainException(400,"Tài xế cần số và hạn bằng lái.");
        if(em.createQuery("select count(n) from NhanVien n where n.maNhanVien <> :id and (lower(n.email)=:email or n.soDienThoai=:phone)",Long.class)
            .setParameter("id",employee.maNhanVien).setParameter("email",email).setParameter("phone",phone).getSingleResult()>0)
            throw new DomainException(409,"Email hoặc điện thoại đã được sử dụng.");
        for(PhanCongChuyenXe p:employee.danhSachPhanCong) if(TripSaleRules.scheduled(p.chuyenXe) || p.chuyenXe.trangThai==TrangThaiChuyen.DANG_CHAY) {
            if(input.trangThai()!=TrangThaiNhanVien.DANG_LAM_VIEC || input.loaiNhanVien()!=employee.loaiNhanVien)
                throw new DomainException(409,"Cần xử lý phân công đang hoạt động trước khi đổi vai trò/trạng thái.");
            if(p.vaiTro!=VaiTroChuyen.PHU_XE && (license==null || input.ngayHetHanBangLai()==null || input.ngayHetHanBangLai().isBefore(p.chuyenXe.gioDenDuKien.toLocalDate())))
                throw new DomainException(409,"Bằng lái không bao phủ chuyến đã phân công.");
        }
        employee.hoTen=text(input.hoTen(),160); employee.soDienThoai=phone; employee.email=email;
        employee.ngaySinh=input.ngaySinh(); employee.nhaXe=owner; employee.loaiNhanVien=input.loaiNhanVien();
        employee.trangThai=input.trangThai(); employee.soBangLai=license; employee.ngayHetHanBangLai=input.ngayHetHanBangLai();
        if(id==null) em.persist(employee); em.flush(); return view(employee);
    }); }
    public void delete(String id) { tx(em -> {
        NhanVien employee=AccessControl.resource(em,NhanVien.class,text(id,40)); NguoiDung user=caller(em);
        scope(user,employee,true); if(user!=null) AccessControl.activeOperator(em,user);
        if(!employee.danhSachPhanCong.isEmpty() || em.createQuery("select count(u) from NguoiDung u where u.nhanVien=:n",Long.class).setParameter("n",employee).getSingleResult()>0)
            throw new DomainException(409,"Nhân viên có phân công hoặc tài khoản; không được xóa cứng.");
        em.remove(employee); return null;
    }); }
}
