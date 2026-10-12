package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.VehicleInputs;
import com.datvexe.repository.*;
import com.datvexe.security.*;
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
    private static final Comparator<Ghe> SEAT_ORDER = Comparator.comparingInt((Ghe g) -> g.tang)
        .thenComparing(g -> java.util.regex.Pattern.compile("[0-9]+").matcher(g.viTri.toUpperCase(Locale.ROOT))
            .replaceAll(match -> String.format(Locale.ROOT,"%020d",new java.math.BigInteger(match.group()))));
    private final EntityManagerFactory factory;
    public VehicleService(EntityManagerFactory factory) { this.factory = factory; }
    private <T> T tx(Function<EntityManager,T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try { AccessPolicy.guard(em); T result = action.apply(em); tx.commit(); return result; }
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
    private static Map<String,Object> view(Xe x) { return view("maXe",x.maXe,"bienSo",x.bienSo,"maLoaiXe",x.loaiXe==null?null:x.loaiXe.maLoaiXe,"maNhaXe",x.nhaXe==null?null:x.nhaXe.maNhaXe,"soChoNgoi",x.soChoNgoi,"tocDo",x.tocDoGio,"dungTichXang",x.dungTichXang,"namSanXuat",x.namSanXuat,"ngayDangKiem",x.ngayDangKiem,"hanDangKiem",x.hanDangKiem,"trangThai",x.trangThai,"soGheThucTe",x.danhSachGhe.size(),
        "soDoHopLe",VehicleRules.layoutValid(x),"dangKiemConHieuLuc",VehicleRules.inspection(x,VehicleRules.today(),VehicleRules.today()),
        "coTheSuaSoDo",x.danhSachChuyenXe.stream().noneMatch(VehicleService::active) && x.danhSachGhe.stream().allMatch(g -> g.danhSachVe.isEmpty() && g.danhSachGiuCho.isEmpty())); }
    private static Map<String,Object> view(Ghe x) { return view("maGhe",x.maGhe,"maXe",x.xe.maXe,"viTri",x.viTri,"tang",x.tang,"trangThai",x.trangThai); }
    private static Map<String,Object> view(PhanCongChuyenXe x) { return view("maPhanCong",x.maPhanCong,"maChuyen",x.chuyenXe.maChuyen,"maNhanVien",x.nhanVien.maNhanVien,"vaiTro",x.vaiTro,"ghiChu",x.ghiChu); }

    public List<Map<String,Object>> list(String kind) { return tx(em -> switch(kind) {
        case "loai-xe" -> new LoaiXeRepository(em).list().stream().map(VehicleService::view).toList();
        case "xe" -> new XeRepository(em).list().stream().filter(x -> RequestIdentity.operatorId() == null || (x.nhaXe != null && x.nhaXe.maNhaXe.equals(RequestIdentity.operatorId()))).map(VehicleService::view).toList();
        case "phan-cong" -> new PhanCongChuyenXeRepository(em).list().stream().filter(x -> RequestIdentity.operatorId() == null || (x.chuyenXe.tuyenXe.nhaXe != null && x.chuyenXe.tuyenXe.nhaXe.maNhaXe.equals(RequestIdentity.operatorId()) && x.nhanVien.nhaXe.maNhaXe.equals(RequestIdentity.operatorId()))).map(VehicleService::view).toList();
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
        if(id==null) { x.maXe=text(input.maXe(),40); if(x.maXe.equalsIgnoreCase("pham-vi")) throw new DomainException(400,"Mã xe này được dành cho hệ thống."); check(repo.find(x.maXe)==null,"Mã xe đã tồn tại"); }
        LoaiXe type=found(new LoaiXeRepository(em).lock(text(input.maLoaiXe(),40)));
        String authenticatedOwner = RequestIdentity.operatorId();
        NhaXe owner=found(em.find(NhaXe.class,text(authenticatedOwner == null ? input.maNhaXe() : authenticatedOwner,40)));
        if (id == null) AccessControl.businessAllowed(em, owner);
        if (input.soChoNgoi() != null && positive(input.soChoNgoi()) != type.soChoNgoi)
            throw new DomainException(400,"Sức chứa phải khớp với loại xe.");
        if(id!=null) {
            check(x.soChoNgoi==type.soChoNgoi,"Sức chứa hiện tại không khớp loại xe; không tự thay sức chứa khi sửa thông tin.");
            check(x.loaiXe!=null && x.loaiXe.maLoaiXe.equals(type.maLoaiXe),"Không đổi loại xe đã tạo; tạo xe mới để giữ lịch sử ghế");
            check(x.nhaXe!=null && x.nhaXe.maNhaXe.equals(owner.maNhaXe),"Không chuyển nhà xe của xe đã tạo");
        }
        if(input.ngayDangKiem()==null || input.hanDangKiem()==null || input.hanDangKiem().isBefore(input.ngayDangKiem()))
            throw new DomainException(400,"Ngày đăng kiểm và hạn đăng kiểm không hợp lệ");
        if(input.namSanXuat()!=null && (input.namSanXuat()<=0 || input.namSanXuat()>VehicleRules.today().getYear()+1))
            throw new DomainException(400,"Năm sản xuất không hợp lệ");
        TrangThaiXe state=input.trangThai()==null?TrangThaiXe.SAN_SANG:input.trangThai();
        if(id!=null) for(ChuyenXe trip:x.danhSachChuyenXe) if(active(trip)) {
            check(state==TrangThaiXe.SAN_SANG,"Xe đang có chuyến; cần xử lý lịch chuyến trước khi đổi trạng thái");
            check(!input.ngayDangKiem().isAfter(trip.ngayKhoiHanh) && !input.hanDangKiem().isBefore(trip.gioDenDuKien.toLocalDate()),"Đăng kiểm không bao phủ lịch chuyến");
        }
        x.tocDoGio=optionalPositiveDecimal(input.tocDo());
        x.dungTichXang=optionalPositiveDecimal(input.dungTichXang());
        String plate=text(input.bienSo(),30).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!plate.matches("[A-Z0-9.-]{3,30}")) throw new DomainException(400,"Biển số không hợp lệ.");
        if (em.createQuery("select count(v) from Xe v where upper(trim(v.bienSo)) = :plate and v.maXe <> :id", Long.class)
                .setParameter("plate", plate).setParameter("id", x.maXe).getSingleResult() > 0)
            throw new DomainException(409,"Biển số đã tồn tại.");
        x.bienSo=plate;
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
        if(generate) { check(x.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không sinh lại ghế."); generate(em,x); }
        return new GheRepository(em).byVehicle(x.maXe).stream().sorted(SEAT_ORDER).map(VehicleService::view).toList();
    }); }
    public Map<String,Object> updateSeat(String vehicle,String id,VehicleInputs.Ghe input) { return tx(em -> {
        found(new XeRepository(em).lock(text(vehicle,40)));
        Ghe g=found(new GheRepository(em).lock(text(id,40))); check(g.xe.maXe.equals(vehicle),"Ghế không thuộc xe");
        check(g.danhSachVe.isEmpty() && g.danhSachGiuCho.isEmpty(),"Ghế đã có vé hoặc giữ chỗ; không thể thay đổi");
        check(g.xe.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không thể sửa ghế");
        int floor=positive(input.tang()); check(floor<=g.xe.loaiXe.soTang,"Tầng vượt cấu hình loại xe");
        if(input.trangThai()==null) throw new DomainException(400,"Thiếu trạng thái ghế");
        String code=seatCode(input.viTri());
        check(g.xe.danhSachGhe.stream().noneMatch(other -> !other.maGhe.equals(g.maGhe) && other.viTri.equalsIgnoreCase(code)),"Mã/vị trí ghế đã tồn tại trong xe.");
        g.viTri=code; g.tang=floor; g.trangThai=input.trangThai(); return view(g);
    }); }
    public Map<String,Object> createSeat(String vehicle,VehicleInputs.Ghe input) { return tx(em -> {
        Xe x=found(new XeRepository(em).lock(text(vehicle,40)));
        check(x.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không thể thêm ghế");
        check(x.danhSachGhe.size()+1==x.soChoNgoi,"Thêm từng ghế chỉ được hoàn thiện đủ sức chứa; dùng API lưu toàn bộ sơ đồ.");
        int floor=positive(input.tang());
        check(x.loaiXe!=null && floor<=x.loaiXe.soTang,"Tầng vượt cấu hình loại xe");
        Ghe g=new Ghe(); g.maGhe=UUID.randomUUID().toString(); g.xe=x;
        g.viTri=seatCode(input.viTri()); g.tang=floor;
        check(x.danhSachGhe.stream().noneMatch(other -> other.viTri.equalsIgnoreCase(g.viTri)),"Mã/vị trí ghế đã tồn tại trong xe.");
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
        throw new DomainException(409,"Không xóa riêng một ghế làm thiếu sức chứa; hãy lưu toàn bộ sơ đồ thay thế.");
    }); }
    public static boolean active(ChuyenXe c) { return c.trangThai==TrangThaiChuyen.CHUA_KHOI_HANH || c.trangThai==TrangThaiChuyen.DANG_CHAY; }
    /** Checks eligibility without changing the trip or another module's service. */
    public Map<String,Object> vehicleEligibility(String vehicle, String trip) { return tx(em -> {
        Xe x=found(new XeRepository(em).find(text(vehicle,40)));
        ChuyenXe c=found(em.find(ChuyenXe.class,text(trip,40)));
        VehicleRules.ready(em, x.maXe, c);
        for(ChuyenXe other:x.danhSachChuyenXe) if(!other.maChuyen.equals(c.maChuyen) && active(other)) {
            check(!LocalDateTime.of(c.ngayKhoiHanh,c.gioKhoiHanh).isBefore(other.gioDenDuKien)
                || !LocalDateTime.of(other.ngayKhoiHanh,other.gioKhoiHanh).isBefore(c.gioDenDuKien),"Xe đã có chuyến trùng thời gian");
        }
        return view("maXe",x.maXe,"maChuyen",c.maChuyen,"duDieuKien",true);
    }); }
    private static String seatCode(String code) {
        String value=text(code,20).toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9.-]{0,19}")) throw new DomainException(400,"Mã/vị trí ghế chỉ dùng chữ, số, dấu chấm hoặc gạch ngang.");
        return value;
    }
    public List<Map<String,Object>> filtered(String kind, String search, String type, String owner, String state, String tripId) {
        return tx(em -> {
            String term=search==null?"":search.strip().toLowerCase(Locale.ROOT);
            if(term.length()>160) throw new DomainException(400,"Từ khóa quá dài.");
            if(kind.equals("loai-xe")) return new LoaiXeRepository(em).list().stream()
                .filter(x -> term.isEmpty() || (x.maLoaiXe+" "+x.tenLoaiXe).toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(x -> x.maLoaiXe)).map(VehicleService::view).toList();
            if(!kind.equals("xe")) {
                if (!kind.equals("phan-cong")) throw new DomainException(404,"API không tồn tại");
                return new PhanCongChuyenXeRepository(em).list().stream()
                    .filter(x -> RequestIdentity.operatorId()==null || (x.chuyenXe.tuyenXe.nhaXe!=null
                        && x.chuyenXe.tuyenXe.nhaXe.maNhaXe.equals(RequestIdentity.operatorId()) && x.nhanVien.nhaXe.maNhaXe.equals(RequestIdentity.operatorId())))
                    .map(VehicleService::view).toList();
            }
            TrangThaiXe status=state==null || state.isBlank()?null:TrangThaiXe.valueOf(state);
            ChuyenXe trip=tripId==null || tripId.isBlank()?null:found(em.find(ChuyenXe.class,tripId));
            if(trip!=null && RequestIdentity.operatorId()!=null) AccessControl.trip(em,
                AccessControl.authenticated(em,RequestIdentity.get().user(),Instant.now()),tripId);
            String scope=RequestIdentity.operatorId();
            List<Map<String,Object>> results=new ArrayList<>();
            for(Xe x:new XeRepository(em).list()) {
                if(scope!=null && (x.nhaXe==null || !scope.equals(x.nhaXe.maNhaXe))) continue;
                if(owner!=null && !owner.isBlank() && (x.nhaXe==null || !owner.equals(x.nhaXe.maNhaXe))) continue;
                if(type!=null && !type.isBlank() && (x.loaiXe==null || !type.equals(x.loaiXe.maLoaiXe))) continue;
                if(status!=null && x.trangThai!=status) continue;
                if(!term.isEmpty() && !(x.bienSo+" "+x.maXe).toLowerCase(Locale.ROOT).contains(term)) continue;
                if(trip!=null) {
                    try { VehicleRules.ready(em,x.maXe,trip); }
                    catch(DomainException invalid) { continue; }
                    boolean overlaps=x.danhSachChuyenXe.stream().anyMatch(other -> !other.maChuyen.equals(trip.maChuyen) && active(other)
                        && LocalDateTime.of(trip.ngayKhoiHanh,trip.gioKhoiHanh).isBefore(other.gioDenDuKien)
                        && LocalDateTime.of(other.ngayKhoiHanh,other.gioKhoiHanh).isBefore(trip.gioDenDuKien));
                    if(overlaps) continue;
                }
                results.add(view(x));
            }
            results.sort(Comparator.comparing(x -> x.get("maXe").toString())); return results;
        });
    }
    public List<Map<String,Object>> operators() { return tx(em -> {
        String scope=RequestIdentity.operatorId();
        return em.createQuery("select n from NhaXe n order by n.tenNhaXe",NhaXe.class).getResultList().stream()
            .filter(n -> scope==null || scope.equals(n.maNhaXe))
            .map(n -> view("maNhaXe",n.maNhaXe,"tenNhaXe",n.tenNhaXe,"trangThai",n.trangThai)).toList();
    }); }
    public List<Map<String,Object>> saveLayout(String id, VehicleInputs.SoDoGhe input) { return tx(em -> {
        Xe vehicle=found(new XeRepository(em).lock(text(id,40)));
        List<VehicleInputs.ViTriGhe> entries=input.danhSachGhe();
        if(entries==null || entries.size()!=vehicle.soChoNgoi) throw new DomainException(400,"Sơ đồ phải có đúng "+vehicle.soChoNgoi+" ghế.");
        check(vehicle.loaiXe!=null && vehicle.soChoNgoi==vehicle.loaiXe.soChoNgoi,"Sức chứa xe không khớp loại xe.");
        Set<String> ids=new HashSet<>(),codes=new HashSet<>();
        Map<String,Ghe> existing=new HashMap<>(); vehicle.danhSachGhe.forEach(g -> existing.put(g.maGhe,g));
        for(var entry:entries) {
            if(entry==null || entry.trangThai()==null) throw new DomainException(400,"Thông tin/trạng thái ghế bị thiếu.");
            if(!codes.add(seatCode(entry.viTri()))) throw new DomainException(400,"Mã/vị trí ghế trùng trong xe.");
            if(entry.tang()==null || entry.tang()<1 || entry.tang()>vehicle.loaiXe.soTang) throw new DomainException(400,"Tầng ghế không hợp lệ.");
            if(entry.maGhe()!=null && (!ids.add(entry.maGhe()) || !existing.containsKey(entry.maGhe())))
                throw new DomainException(400,"Mã ghế trùng hoặc không thuộc xe.");
        }
        boolean changed=entries.stream().anyMatch(e -> {
            Ghe old=existing.get(e.maGhe());
            return old==null || !old.viTri.equals(seatCode(e.viTri())) || old.tang!=e.tang() || old.trangThai!=e.trangThai();
        }) || ids.size()!=existing.size();
        if(changed) {
            check(vehicle.danhSachChuyenXe.stream().noneMatch(VehicleService::active),"Xe đang có lịch chuyến; không sửa sơ đồ ghế.");
            check(existing.values().stream().allMatch(g -> g.danhSachVe.isEmpty() && g.danhSachGiuCho.isEmpty()),"Sơ đồ đã có lịch sử vé/giữ chỗ; không được thay đổi.");
            // Park old labels so swaps cannot violate the existing database unique key.
            Set<String> parked=new HashSet<>(codes); existing.values().forEach(g -> parked.add(g.viTri));
            for(Ghe g:existing.values()) {
                String temporary; do { temporary="TMP"+UUID.randomUUID().toString().replace("-", "").substring(0,16); } while(!parked.add(temporary));
                g.viTri=temporary;
            }
            em.flush();
            vehicle.danhSachGhe.removeIf(g -> !ids.contains(g.maGhe)); em.flush();
            for(var entry:entries) {
                Ghe g=existing.get(entry.maGhe());
                if(g==null) { g=new Ghe(); g.maGhe=UUID.randomUUID().toString();g.xe=vehicle;vehicle.danhSachGhe.add(g); }
                g.viTri=seatCode(entry.viTri());g.tang=entry.tang();g.trangThai=entry.trangThai();
                if(entry.maGhe()==null) em.persist(g);
            }
            em.flush();
        }
        return vehicle.danhSachGhe.stream().sorted(SEAT_ORDER).map(VehicleService::view).toList();
    }); }
    public Map<String,Object> tripSeats(String tripId) { return tx(em -> {
        ChuyenXe trip=AccessControl.resource(em,ChuyenXe.class,tripId);
        AccessControl.saleAllowed(em,tripId);
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var seats=new GheRepository(em).byVehicle(trip.xe.maXe).stream().sorted(SEAT_ORDER).map(g -> view("maGhe",g.maGhe,
            "viTri",g.viTri,"tang",g.tang,"trangThaiSuDung",g.trangThai,"tinhTrangKhaDung",VehicleRules.availability(em,trip,g,now))).toList();
        return view("maChuyen",trip.maChuyen,"maXe",trip.xe.maXe,"serverTime",OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),"danhSachGhe",seats);
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
