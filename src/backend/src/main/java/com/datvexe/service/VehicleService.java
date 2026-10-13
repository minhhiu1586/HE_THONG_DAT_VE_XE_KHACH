package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.VehicleInputs;
import com.datvexe.repository.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * Module rules and review notes are documented in
 * docs/plans/vehicle-seat-database.md; they are not yet confirmed team requirements.
 */
public final class VehicleService {
    private final EntityManagerFactory factory;
    public VehicleService(EntityManagerFactory factory) { this.factory = factory; }
    private <T> T tx(Function<EntityManager,T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try { T result = action.apply(em); tx.commit(); return result; }
            catch (RuntimeException e) { if (tx.isActive()) tx.rollback(); throw e; }
        }
    }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new DomainException(400, "Chuỗi bắt buộc bị thiếu hoặc quá dài");
        return value.trim();
    }
    private static int positive(Integer n) {
        if (n == null || n <= 0) throw new DomainException(400, "Số lượng phải lớn hơn 0");
        return n;
    }
    private static BigDecimal optionalPositiveDecimal(BigDecimal value) {
        if (value != null && (value.signum() <= 0 || value.precision() - value.scale() > 8 || value.scale() > 2)) {
            throw new DomainException(400, "Tốc độ hoặc dung tích không hợp lệ");
        }
        return value;
    }
    private static <T> T found(T value) {
        if (value == null) throw new DomainException(404, "Không tìm thấy dữ liệu"); return value;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new DomainException(409, message); }
    private static Map<String,Object> view(Object... pairs) {
        Map<String,Object> map = new LinkedHashMap<>();
        for (int i=0; i<pairs.length; i+=2) map.put((String)pairs[i], pairs[i+1]); return map;
    }
    private static Map<String,Object> view(LoaiXe x) { return view("maLoaiXe",x.maLoaiXe,"tenLoaiXe",x.tenLoaiXe,"soChoNgoi",x.soChoNgoi,"soTang",x.soTang,"moTa",x.moTa); }
    private static Map<String,Object> view(Xe x) { return view("maXe",x.maXe,"bienSo",x.bienSo,"maLoaiXe",x.loaiXe==null?null:x.loaiXe.maLoaiXe,"maNhaXe",x.nhaXe==null?null:x.nhaXe.maNhaXe,"soChoNgoi",x.soChoNgoi,"tocDo",x.tocDoGio,"dungTichXang",x.dungTichXang,"namSanXuat",x.namSanXuat,"ngayDangKiem",x.ngayDangKiem,"hanDangKiem",x.hanDangKiem,"trangThai",x.trangThai); }
    private static Map<String,Object> view(Ghe x) { return view("maGhe",x.maGhe,"maXe",x.xe.maXe,"viTri",x.viTri,"tang",x.tang,"trangThai",x.trangThai); }
    private static Map<String,Object> view(PhanCongChuyenXe x) { return view("maPhanCong",x.maPhanCong,"maChuyen",x.chuyenXe.maChuyen,"maNhanVien",x.nhanVien.maNhanVien,"vaiTro",x.vaiTro,"ghiChu",x.ghiChu); }

    public List<Map<String,Object>> list(String kind) { return tx(em -> switch(kind) {
        case "loai-xe" -> new LoaiXeRepository(em).list().stream().map(VehicleService::view).toList();
        case "xe" -> new XeRepository(em).list().stream().map(VehicleService::view).toList();
        case "phan-cong" -> new PhanCongChuyenXeRepository(em).list().stream().map(VehicleService::view).toList();
        default -> throw new DomainException(404,"API không tồn tại");
    }); }
    public Map<String,Object> get(String kind, String id) { return tx(em -> switch(kind) {
        case "loai-xe" -> view(found(new LoaiXeRepository(em).find(text(id,40))));
        case "xe" -> view(found(new XeRepository(em).find(text(id,40))));
        case "phan-cong" -> view(found(new PhanCongChuyenXeRepository(em).find(text(id,40))));
        default -> throw new DomainException(404,"API không tồn tại");
    }); }
    public Map<String,Object> saveType(String id, VehicleInputs.LoaiXe input) { return tx(em -> {
        LoaiXeRepository repo = new LoaiXeRepository(em);
        LoaiXe x = id == null ? new LoaiXe() : found(repo.lock(text(id,40)));
        if (id == null) { x.maLoaiXe=text(input.maLoaiXe(),40); check(repo.find(x.maLoaiXe)==null,"Mã loại xe đã tồn tại"); }
        int seats=positive(input.soChoNgoi()), floors=positive(input.soTang());
        check(floors<=seats,"Số tầng không được lớn hơn số ghế");
        if (id!=null && (seats!=x.soChoNgoi || floors!=x.soTang))
            check(x.danhSachXe.isEmpty(),"Loại xe đang được sử dụng; không thể đổi cấu trúc ghế");
        x.tenLoaiXe=text(input.tenLoaiXe(),120); x.soChoNgoi=seats; x.soTang=floors;
        if (input.moTa()!=null && input.moTa().length()>1000) throw new DomainException(400,"Mô tả quá dài");
        x.moTa=input.moTa(); if(id==null) repo.save(x); return view(x);
    }); }
    public Map<String,Object> saveVehicle(String id, VehicleInputs.Xe input) { return tx(em -> {
        XeRepository repo=new XeRepository(em);
        Xe x=id==null?new Xe():found(repo.lock(text(id,40)));
        if(id==null) { x.maXe=text(input.maXe(),40); check(repo.find(x.maXe)==null,"Mã xe đã tồn tại"); }
        LoaiXe type=found(new LoaiXeRepository(em).lock(text(input.maLoaiXe(),40)));
        NhaXe owner=found(em.find(NhaXe.class,text(input.maNhaXe(),40)));
        if(id!=null) {
            check(x.loaiXe!=null && x.loaiXe.maLoaiXe.equals(type.maLoaiXe),"Không đổi loại xe đã tạo; tạo xe mới để giữ lịch sử ghế");
            check(x.nhaXe!=null && x.nhaXe.maNhaXe.equals(owner.maNhaXe),"Không chuyển nhà xe của xe đã tạo");
        }
        if(input.ngayDangKiem()==null || input.hanDangKiem()==null || input.hanDangKiem().isBefore(input.ngayDangKiem()))
            throw new DomainException(400,"Ngày đăng kiểm và hạn đăng kiểm không hợp lệ");
        if(input.namSanXuat()!=null && input.namSanXuat()<=0)
            throw new DomainException(400,"Năm sản xuất không hợp lệ");
        TrangThaiXe state=input.trangThai()==null?TrangThaiXe.SAN_SANG:input.trangThai();
        if(id!=null) for(ChuyenXe trip:x.danhSachChuyenXe) if(active(trip)) {
            check(state==TrangThaiXe.SAN_SANG,"Xe đang có chuyến; cần xử lý lịch chuyến trước khi đổi trạng thái");
            check(!input.ngayDangKiem().isAfter(trip.ngayKhoiHanh) && !input.hanDangKiem().isBefore(trip.gioDenDuKien.toLocalDate()),"Đăng kiểm không bao phủ lịch chuyến");
        }
        x.tocDoGio=optionalPositiveDecimal(input.tocDo());
        x.dungTichXang=optionalPositiveDecimal(input.dungTichXang());
        x.bienSo=text(input.bienSo(),30).toUpperCase(Locale.ROOT);
        x.loaiXe=type; x.nhaXe=owner;
        x.soChoNgoi=type.soChoNgoi; x.namSanXuat=input.namSanXuat(); x.ngayDangKiem=input.ngayDangKiem(); x.hanDangKiem=input.hanDangKiem(); x.trangThai=state;
        if(id==null) { repo.save(x); generate(em,x); } return view(x);
    }); }
    private static void generate(EntityManager em, Xe x) {
        check(x.danhSachGhe.isEmpty(),"Xe đã có ghế; không sinh lại danh sách");
        for(int i=0;i<x.soChoNgoi;i++) {
            Ghe g=new Ghe(); g.maGhe=UUID.randomUUID().toString(); g.xe=x;
            g.viTri=String.format(Locale.ROOT,"G%03d",i+1); g.tang=1+i*x.loaiXe.soTang/x.soChoNgoi;
            x.danhSachGhe.add(g); new GheRepository(em).save(g);
        }
    }
    public List<Map<String,Object>> seats(String vehicle, boolean generate) { return tx(em -> {
        Xe x=found(new XeRepository(em).lock(text(vehicle,40)));
        if(generate) generate(em,x);
        return new GheRepository(em).byVehicle(x.maXe).stream().map(VehicleService::view).toList();
    }); }
    public Map<String,Object> updateSeat(String vehicle,String id,VehicleInputs.Ghe input) { return tx(em -> {
        found(new XeRepository(em).lock(text(vehicle,40)));
        Ghe g=found(new GheRepository(em).lock(text(id,40))); check(g.xe.maXe.equals(vehicle),"Ghế không thuộc xe");
        check(g.danhSachVe.isEmpty() && g.danhSachGiuCho.isEmpty(),"Ghế đã có vé hoặc giữ chỗ; không thể thay đổi");
        check(g.xe.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không thể sửa ghế");
        int floor=positive(input.tang()); check(floor<=g.xe.loaiXe.soTang,"Tầng vượt cấu hình loại xe");
        if(input.trangThai()==null) throw new DomainException(400,"Thiếu trạng thái ghế");
        g.viTri=text(input.viTri(),20); g.tang=floor; g.trangThai=input.trangThai(); return view(g);
    }); }
    public Map<String,Object> createSeat(String vehicle,VehicleInputs.Ghe input) { return tx(em -> {
        Xe x=found(new XeRepository(em).lock(text(vehicle,40)));
        check(x.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không thể thêm ghế");
        check(x.danhSachGhe.size()<x.soChoNgoi,"Danh sách đã đủ sức chứa xe");
        int floor=positive(input.tang());
        check(x.loaiXe!=null && floor<=x.loaiXe.soTang,"Tầng vượt cấu hình loại xe");
        Ghe g=new Ghe(); g.maGhe=UUID.randomUUID().toString(); g.xe=x;
        g.viTri=text(input.viTri(),20); g.tang=floor;
        g.trangThai=input.trangThai()==null?TrangThaiGhe.HOAT_DONG:input.trangThai();
        x.danhSachGhe.add(g); new GheRepository(em).save(g); return view(g);
    }); }
    public Map<String,Object> getSeat(String vehicle,String id) { return tx(em -> {
        Ghe g=found(new GheRepository(em).lock(text(id,40)));
        check(g.xe.maXe.equals(text(vehicle,40)),"Ghế không thuộc xe"); return view(g);
    }); }
    public void deleteSeat(String vehicle,String id) { tx(em -> {
        Xe x=found(new XeRepository(em).lock(text(vehicle,40)));
        Ghe g=found(new GheRepository(em).lock(text(id,40)));
        check(g.xe.maXe.equals(x.maXe),"Ghế không thuộc xe");
        check(g.danhSachVe.isEmpty() && g.danhSachGiuCho.isEmpty(),"Ghế đã có lịch sử vé hoặc giữ chỗ; không thể xóa");
        check(x.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không thể xóa ghế");
        x.danhSachGhe.remove(g); new GheRepository(em).delete(g); return null;
    }); }
    public static boolean active(ChuyenXe c) { return c.trangThai==TrangThaiChuyen.CHUA_KHOI_HANH || c.trangThai==TrangThaiChuyen.DANG_CHAY; }
    /** Checks eligibility without changing the trip or another module's service. */
    public Map<String,Object> vehicleEligibility(String vehicle, String trip) { return tx(em -> {
        Xe x=found(new XeRepository(em).find(text(vehicle,40)));
        ChuyenXe c=found(em.find(ChuyenXe.class,text(trip,40)));
        check(x.trangThai==TrangThaiXe.SAN_SANG,"Xe không sẵn sàng");
        check(x.danhSachGhe.size()==x.soChoNgoi,"Danh sách ghế chưa đủ sức chứa xe");
        check(x.ngayDangKiem!=null && x.hanDangKiem!=null && !x.ngayDangKiem.isAfter(c.ngayKhoiHanh)
            && !x.hanDangKiem.isBefore(c.gioDenDuKien.toLocalDate()),"Đăng kiểm không bao phủ lịch chuyến");
        if(c.tuyenXe.nhaXe!=null) check(x.nhaXe!=null && x.nhaXe.maNhaXe.equals(c.tuyenXe.nhaXe.maNhaXe),"Xe không thuộc nhà xe của tuyến");
        for(ChuyenXe other:x.danhSachChuyenXe) if(!other.maChuyen.equals(c.maChuyen) && active(other)) {
            check(!LocalDateTime.of(c.ngayKhoiHanh,c.gioKhoiHanh).isBefore(other.gioDenDuKien)
                || !LocalDateTime.of(other.ngayKhoiHanh,other.gioKhoiHanh).isBefore(c.gioDenDuKien),"Xe đã có chuyến trùng thời gian");
        }
        return view("maXe",x.maXe,"maChuyen",c.maChuyen,"duDieuKien",true);
    }); }
    public Map<String,Object> saveAssignment(String id, VehicleInputs.PhanCong input) { return tx(em -> {
        PhanCongChuyenXeRepository repo=new PhanCongChuyenXeRepository(em);
        PhanCongChuyenXe p=id==null?new PhanCongChuyenXe():found(repo.lock(text(id,40)));
        if(id==null) { p.maPhanCong=text(input.maPhanCong(),40); check(repo.find(p.maPhanCong)==null,"Mã phân công đã tồn tại"); }
        if(id!=null) check(p.chuyenXe.trangThai==TrangThaiChuyen.CHUA_KHOI_HANH,"Không sửa phân công chuyến đã khởi hành");
        ChuyenXe c=found(em.find(ChuyenXe.class,text(input.maChuyen(),40),LockModeType.PESSIMISTIC_WRITE));
        c=(ChuyenXe)org.hibernate.Hibernate.unproxy(c);
        c.tuyenXe=(TuyenXe)org.hibernate.Hibernate.unproxy(c.tuyenXe);
        check(c.trangThai==TrangThaiChuyen.CHUA_KHOI_HANH,"Chỉ phân công chuyến chưa khởi hành");
        NhanVien n=found(em.find(NhanVien.class,text(input.maNhanVien(),40),LockModeType.PESSIMISTIC_WRITE));
        n=(NhanVien)org.hibernate.Hibernate.unproxy(n);
        if(input.vaiTro()==null) throw new DomainException(400,"Thiếu vai trò");
        check(n.trangThai==TrangThaiNhanVien.DANG_LAM_VIEC,"Nhân viên không đang làm việc");
        check((input.vaiTro()!=VaiTroChuyen.PHU_XE && n.loaiNhanVien==LoaiNhanVien.TAI_XE) || (input.vaiTro()==VaiTroChuyen.PHU_XE && n.loaiNhanVien==LoaiNhanVien.PHU_XE),"Loại nhân viên không phù hợp vai trò");
        if(c.tuyenXe.nhaXe!=null) check(n.nhaXe!=null && n.nhaXe.maNhaXe.equals(c.tuyenXe.nhaXe.maNhaXe),"Nhân viên không thuộc nhà xe của tuyến");
        if(input.vaiTro()!=VaiTroChuyen.PHU_XE) check(n.soBangLai!=null && !n.soBangLai.isBlank() && n.ngayHetHanBangLai!=null && !n.ngayHetHanBangLai.isBefore(c.gioDenDuKien.toLocalDate()),"Bằng lái thiếu hoặc hết hạn trước khi kết thúc chuyến");
        for(PhanCongChuyenXe other:repo.list()) if(!other.maPhanCong.equals(p.maPhanCong) && other.nhanVien.maNhanVien.equals(n.maNhanVien) && active(other.chuyenXe)) {
            ChuyenXe o=other.chuyenXe;
            check(!LocalDateTime.of(c.ngayKhoiHanh,c.gioKhoiHanh).isBefore(o.gioDenDuKien) || !LocalDateTime.of(o.ngayKhoiHanh,o.gioKhoiHanh).isBefore(c.gioDenDuKien),"Nhân viên đã có chuyến trùng thời gian");
        }
        if(input.ghiChu()!=null && input.ghiChu().length()>1000) throw new DomainException(400,"Ghi chú quá dài");
        p.ghiChu=input.ghiChu(); p.chuyenXe=c; p.nhanVien=n; p.vaiTro=input.vaiTro();
        if(id==null) repo.save(p);
        return view(p);
    }); }
    public void delete(String kind,String id) { tx(em -> {
        switch(kind) {
            case "loai-xe" -> { var r=new LoaiXeRepository(em); var x=found(r.lock(text(id,40))); check(x.danhSachXe.isEmpty(),"Loại xe đang được sử dụng"); r.delete(x); }
            case "xe" -> { var r=new XeRepository(em); var x=found(r.lock(text(id,40))); check(x.danhSachChuyenXe.isEmpty(),"Xe đã có lịch sử chuyến"); for(Ghe g:x.danhSachGhe) check(g.danhSachVe.isEmpty() && g.danhSachGiuCho.isEmpty(),"Ghế đã được sử dụng"); r.delete(x); }
            case "phan-cong" -> { var r=new PhanCongChuyenXeRepository(em); var x=found(r.lock(text(id,40))); check(x.chuyenXe.trangThai==TrangThaiChuyen.CHUA_KHOI_HANH,"Chuyến đã khởi hành"); r.delete(x); }
            default -> throw new DomainException(404,"API không tồn tại");
        } return null;
    }); }
}
