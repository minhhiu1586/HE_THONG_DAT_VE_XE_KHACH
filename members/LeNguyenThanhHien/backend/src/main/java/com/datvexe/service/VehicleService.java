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

/**
 * Module rules and review notes are documented in docs/plans/thiet-ke-du-lieu-xe-va-ghe.md;
 * they are not yet confirmed team requirements.
 */
public final class VehicleService {
    private static final Comparator<Ghe> SEAT_ORDER = VehicleService::compareSeats;

    private final EntityManagerFactory factory;

    public VehicleService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    // So sánh tầng trước, sau đó so sánh vị trí để sắp xếp ghế.
    private static int compareSeats(Ghe first, Ghe second) {
        int floorOrder = Integer.compare(first.tang, second.tang);
        if (floorOrder != 0) {
            return floorOrder;
        }
        return sortableSeatPosition(first.viTri).compareTo(sortableSeatPosition(second.viTri));
    }

    // Thêm số 0 vào đầu phần số khi so sánh chuỗi để G2 đứng trước G10.
    private static String sortableSeatPosition(String position) {
        String uppercasePosition = position.toUpperCase(Locale.ROOT);
        java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile("[0-9]+");
        java.util.regex.Matcher numbers = numberPattern.matcher(uppercasePosition);
        StringBuilder result = new StringBuilder();

        while (numbers.find()) {
            java.math.BigInteger number = new java.math.BigInteger(numbers.group());
            String paddedNumber = String.format(Locale.ROOT, "%020d", number);
            numbers.appendReplacement(result, paddedNumber);
        }

        numbers.appendTail(result);
        return result.toString();
    }

    // Kiểm tra xe có chuyến chưa kết thúc để bảo vệ thông tin xe và sơ đồ ghế.
    private static boolean hasActiveTrip(Xe vehicle) {
        for (ChuyenXe trip : vehicle.danhSachChuyenXe) {
            if (active(trip)) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra danh sách ghế đã có lịch sử vé hoặc giữ chỗ hay chưa.
    private static boolean hasSeatHistory(Collection<Ghe> seats) {
        for (Ghe seat : seats) {
            if (!seat.danhSachVe.isEmpty() || !seat.danhSachGiuCho.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra vị trí ghế có trùng trong cùng xe hay không, bỏ qua ghế đang được sửa.
    private static boolean duplicateSeatPosition(Xe vehicle, String position, String ignoredSeatId) {
        for (Ghe seat : vehicle.danhSachGhe) {
            if (ignoredSeatId != null && seat.maGhe.equals(ignoredSeatId)) {
                continue;
            }
            if (seat.viTri.equalsIgnoreCase(position)) {
                return true;
            }
        }
        return false;
    }

    // Sắp xếp ghế theo tầng và vị trí rồi chuyển thành danh sách dữ liệu trả về.
    private static List<Map<String, Object>> seatResponses(Collection<Ghe> seats) {
        List<Ghe> ordered = new ArrayList<>(seats);
        ordered.sort(SEAT_ORDER);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Ghe seat : ordered) {
            results.add(view(seat));
        }
        return results;
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new DomainException(400, "Chuỗi bắt buộc bị thiếu hoặc quá dài");
        }
        return value.trim();
    }

    private static int positive(Integer value) {
        if (value == null || value <= 0) {
            throw new DomainException(400, "Số lượng phải lớn hơn 0");
        }
        return value;
    }

    // Cho phép bỏ trống tốc độ hoặc dung tích, còn giá trị nhập phải dương và đúng giới hạn số.
    private static BigDecimal optionalPositiveDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }

        int integerDigits = value.precision() - value.scale();
        if (value.signum() <= 0 || integerDigits > 8 || value.scale() > 2) {
            throw new DomainException(400, "Tốc độ hoặc dung tích không hợp lệ");
        }
        return value;
    }

    private static <T> T found(T value) {
        if (value == null) {
            throw new DomainException(404, "Không tìm thấy dữ liệu");
        }
        return value;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new DomainException(409, message);
        }
    }

    // Chuyển thông tin loại xe thành dữ liệu trả về cho giao diện.
    private static Map<String, Object> view(LoaiXe vehicleType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maLoaiXe", vehicleType.maLoaiXe);
        result.put("tenLoaiXe", vehicleType.tenLoaiXe);
        result.put("soChoNgoi", vehicleType.soChoNgoi);
        result.put("soTang", vehicleType.soTang);
        result.put("moTa", vehicleType.moTa);
        return result;
    }

    // Ghép thông tin xe, số ghế và tình trạng sơ đồ ghế để giao diện hiển thị.
    private static Map<String, Object> view(Xe vehicle) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maXe", vehicle.maXe);
        result.put("bienSo", vehicle.bienSo);

        if (vehicle.loaiXe == null) {
            result.put("maLoaiXe", null);
        } else {
            result.put("maLoaiXe", vehicle.loaiXe.maLoaiXe);
        }

        if (vehicle.nhaXe == null) {
            result.put("maNhaXe", null);
        } else {
            result.put("maNhaXe", vehicle.nhaXe.maNhaXe);
        }

        result.put("soChoNgoi", vehicle.soChoNgoi);
        result.put("tocDo", vehicle.tocDoGio);
        result.put("dungTichXang", vehicle.dungTichXang);
        result.put("namSanXuat", vehicle.namSanXuat);
        result.put("ngayDangKiem", vehicle.ngayDangKiem);
        result.put("hanDangKiem", vehicle.hanDangKiem);
        result.put("trangThai", vehicle.trangThai);
        result.put("soGheThucTe", vehicle.danhSachGhe.size());
        result.put("soDoHopLe", VehicleRules.layoutValid(vehicle));

        boolean inspectionValid = VehicleRules.inspection(vehicle, VehicleRules.today(), VehicleRules.today());
        result.put("dangKiemConHieuLuc", inspectionValid);

        boolean editable = !hasActiveTrip(vehicle) && !hasSeatHistory(vehicle.danhSachGhe);
        result.put("coTheSuaSoDo", editable);
        return result;
    }

    // Chuyển thông tin vị trí, tầng và trạng thái ghế thành dữ liệu trả về.
    private static Map<String, Object> view(Ghe seat) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maGhe", seat.maGhe);
        result.put("maXe", seat.xe.maXe);
        result.put("viTri", seat.viTri);
        result.put("tang", seat.tang);
        result.put("trangThai", seat.trangThai);
        return result;
    }

    // Lấy danh sách loại xe hoặc xe theo quyền và phạm vi nhà xe của người đăng nhập.
    public List<Map<String, Object>> list(String kind) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                List<Map<String, Object>> results = new ArrayList<>();
                String operatorId = RequestIdentity.operatorId();

                if (kind.equals("loai-xe")) {
                    for (LoaiXe type : new LoaiXeRepository(em).list()) {
                        results.add(view(type));
                    }
                } else if (kind.equals("xe")) {
                    for (Xe vehicle : new XeRepository(em).list()) {
                        if (operatorId != null && (vehicle.nhaXe == null || !operatorId.equals(vehicle.nhaXe.maNhaXe))) {
                            continue;
                        }
                        results.add(view(vehicle));
                    }
                } else {
                    throw new DomainException(404, "API không tồn tại");
                }

                transaction.commit();
                return results;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Tìm một loại xe hoặc xe theo mã rồi chuyển thành dữ liệu trả về cho giao diện.
    public Map<String, Object> get(String kind, String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Map<String, Object> result;

                if (kind.equals("loai-xe")) {
                    LoaiXe type = found(new LoaiXeRepository(em).find(text(id, 40)));
                    result = view(type);
                } else if (kind.equals("xe")) {
                    Xe vehicle = found(new XeRepository(em).find(text(id, 40)));
                    result = view(vehicle);
                } else {
                    throw new DomainException(404, "API không tồn tại");
                }

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

    // Tạo hoặc sửa loại xe và chặn đổi cấu trúc ghế khi loại xe đã được sử dụng.
    public Map<String, Object> saveType(String id, VehicleInputs.LoaiXe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                LoaiXeRepository repo = new LoaiXeRepository(em);

                LoaiXe vehicleType;
                if (id == null) {
                    vehicleType = new LoaiXe();
                } else {
                    vehicleType = found(repo.lock(text(id, 40)));
                }

                if (id == null) {
                    vehicleType.maLoaiXe = text(input.maLoaiXe(), 40);
                    check(repo.find(vehicleType.maLoaiXe) == null, "Mã loại xe đã tồn tại");
                }

                int seats = positive(input.soChoNgoi());
                int floors = positive(input.soTang());
                check(floors <= seats, "Số tầng không được lớn hơn số ghế");

                if (id != null && (seats != vehicleType.soChoNgoi || floors != vehicleType.soTang)) {
                    check(vehicleType.danhSachXe.isEmpty(), "Loại xe đang được sử dụng; không thể đổi cấu trúc ghế");
                }

                vehicleType.tenLoaiXe = text(input.tenLoaiXe(), 120);
                vehicleType.soChoNgoi = seats;
                vehicleType.soTang = floors;

                if (input.moTa() != null && input.moTa().length() > 1000) {
                    throw new DomainException(400, "Mô tả quá dài");
                }
                vehicleType.moTa = input.moTa();

                if (id == null) {
                    repo.save(vehicleType);
                }

                Map<String, Object> result = view(vehicleType);
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

    // Tạo hoặc sửa xe sau khi kiểm tra nhà xe, loại xe, biển số, đăng kiểm và lịch chuyến.
    public Map<String, Object> saveVehicle(String id, VehicleInputs.Xe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                XeRepository repo = new XeRepository(em);

                Xe vehicle;
                if (id == null) {
                    vehicle = new Xe();
                } else {
                    vehicle = found(repo.lock(text(id, 40)));
                }

                if (id == null) {
                    vehicle.maXe = text(input.maXe(), 40);
                    if (vehicle.maXe.equalsIgnoreCase("pham-vi")) {
                        throw new DomainException(400, "Mã xe này được dành cho hệ thống.");
                    }
                    check(repo.find(vehicle.maXe) == null, "Mã xe đã tồn tại");
                }

                LoaiXe type = found(new LoaiXeRepository(em).lock(text(input.maLoaiXe(), 40)));
                String ownerId = RequestIdentity.operatorId();

                if (ownerId == null) {
                    ownerId = input.maNhaXe();
                }
                NhaXe owner = found(em.find(NhaXe.class, text(ownerId, 40)));

                if (id == null) {
                    AccessControl.businessAllowed(em, owner);
                }

                if (input.soChoNgoi() != null && positive(input.soChoNgoi()) != type.soChoNgoi) {
                    throw new DomainException(400, "Sức chứa phải khớp với loại xe.");
                }

                if (id != null) {
                    check(vehicle.soChoNgoi == type.soChoNgoi,
                        "Sức chứa hiện tại không khớp loại xe; không tự thay sức chứa khi sửa thông tin.");
                    check(vehicle.loaiXe != null && vehicle.loaiXe.maLoaiXe.equals(type.maLoaiXe),
                        "Không đổi loại xe đã tạo; tạo xe mới để giữ lịch sử ghế");
                    check(vehicle.nhaXe != null && vehicle.nhaXe.maNhaXe.equals(owner.maNhaXe),
                        "Không chuyển nhà xe của xe đã tạo");
                }

                if (input.ngayDangKiem() == null || input.hanDangKiem() == null
                    || input.hanDangKiem().isBefore(input.ngayDangKiem())) {
                    throw new DomainException(400, "Ngày đăng kiểm và hạn đăng kiểm không hợp lệ");
                }

                if (input.namSanXuat() != null
                    && (input.namSanXuat() <= 0 || input.namSanXuat() > VehicleRules.today().getYear() + 1)) {
                    throw new DomainException(400, "Năm sản xuất không hợp lệ");
                }

                TrangThaiXe state;
                if (input.trangThai() == null) {
                    state = TrangThaiXe.SAN_SANG;
                } else {
                    state = input.trangThai();
                }

                if (id != null) {
                    for (ChuyenXe trip : vehicle.danhSachChuyenXe) {
                        if (!active(trip)) {
                            continue;
                        }

                        check(state == TrangThaiXe.SAN_SANG,
                            "Xe đang có chuyến; cần xử lý lịch chuyến trước khi đổi trạng thái");

                        boolean inspectionCoversTrip = !input.ngayDangKiem().isAfter(trip.ngayKhoiHanh)
                            && !input.hanDangKiem().isBefore(trip.gioDenDuKien.toLocalDate());
                        check(inspectionCoversTrip, "Đăng kiểm không bao phủ lịch chuyến");
                    }
                }

                vehicle.tocDoGio = optionalPositiveDecimal(input.tocDo());
                vehicle.dungTichXang = optionalPositiveDecimal(input.dungTichXang());

                String plate = text(input.bienSo(), 30).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
                if (!plate.matches("[A-Z0-9.-]{3,30}")) {
                    throw new DomainException(400, "Biển số không hợp lệ.");
                }

                String duplicatePlateQuery =
                    "select count(v) from Xe v where upper(trim(v.bienSo)) = :plate and v.maXe <> :id";
                Long duplicatePlates = em.createQuery(duplicatePlateQuery, Long.class)
                    .setParameter("plate", plate)
                    .setParameter("id", vehicle.maXe)
                    .getSingleResult();

                if (duplicatePlates > 0) {
                    throw new DomainException(409, "Biển số đã tồn tại.");
                }

                vehicle.bienSo = plate;
                vehicle.loaiXe = type;
                vehicle.nhaXe = owner;

                // Lấy sức chứa từ loại xe để thông tin xe và ghế dùng cùng cấu hình.
                vehicle.soChoNgoi = type.soChoNgoi;
                vehicle.namSanXuat = input.namSanXuat();
                vehicle.ngayDangKiem = input.ngayDangKiem();
                vehicle.hanDangKiem = input.hanDangKiem();
                vehicle.trangThai = state;

                if (id == null) {
                    repo.save(vehicle);
                    generate(em, vehicle);
                }

                Map<String, Object> result = view(vehicle);
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

    // Tạo đủ ghế mặc định theo sức chứa và chia tầng khi xe chưa có danh sách ghế.
    private static void generate(EntityManager em, Xe vehicle) {
        check(vehicle.danhSachGhe.isEmpty(), "Xe đã có ghế; không sinh lại danh sách");

        for (int i = 0; i < vehicle.soChoNgoi; i++) {
            Ghe seat = new Ghe();
            seat.maGhe = UUID.randomUUID().toString();
            seat.xe = vehicle;
            seat.viTri = String.format(Locale.ROOT, "G%03d", i + 1);

            // Chia các ghế liên tiếp thành nhóm theo số tầng của loại xe.
            seat.tang = 1 + i * vehicle.loaiXe.soTang / vehicle.soChoNgoi;
            vehicle.danhSachGhe.add(seat);
            new GheRepository(em).save(seat);
        }
    }

    // Lấy danh sách ghế và chỉ sinh ghế khi có yêu cầu và xe đáp ứng điều kiện.
    public List<Map<String, Object>> seats(String vehicle, boolean generate) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Xe selectedVehicle = found(new XeRepository(em).lock(text(vehicle, 40)));

                if (generate) {
                    check(!hasActiveTrip(selectedVehicle), "Xe đang có lịch chuyến; không sinh lại ghế.");
                    generate(em, selectedVehicle);
                }

                List<Map<String, Object>> result = seatResponses(new GheRepository(em).byVehicle(selectedVehicle.maXe));
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

    // Sửa thông tin ghế sau khi kiểm tra vị trí, tầng, lịch chuyến và lịch sử sử dụng.
    public Map<String, Object> updateSeat(String vehicle, String id, VehicleInputs.Ghe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                found(new XeRepository(em).lock(text(vehicle, 40)));
                Ghe seat = found(new GheRepository(em).lock(text(id, 40)));

                check(seat.xe.maXe.equals(vehicle), "Ghế không thuộc xe");
                check(seat.danhSachVe.isEmpty() && seat.danhSachGiuCho.isEmpty(),
                    "Ghế đã có vé hoặc giữ chỗ; không thể thay đổi");
                check(!hasActiveTrip(seat.xe), "Xe đang có lịch chuyến; không thể sửa ghế");

                int floor = positive(input.tang());
                check(floor <= seat.xe.loaiXe.soTang, "Tầng vượt cấu hình loại xe");

                if (input.trangThai() == null) {
                    throw new DomainException(400, "Thiếu trạng thái ghế");
                }

                String code = seatCode(input.viTri());
                check(!duplicateSeatPosition(seat.xe, code, seat.maGhe), "Mã/vị trí ghế đã tồn tại trong xe.");

                seat.viTri = code;
                seat.tang = floor;
                seat.trangThai = input.trangThai();

                Map<String, Object> result = view(seat);
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

    // Thêm ghế sau khi kiểm tra sức chứa, vị trí, tầng và điều kiện sửa sơ đồ của xe.
    public Map<String, Object> createSeat(String vehicle, VehicleInputs.Ghe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Xe selectedVehicle = found(new XeRepository(em).lock(text(vehicle, 40)));

                check(!hasActiveTrip(selectedVehicle), "Xe đang có lịch chuyến; không thể thêm ghế");
                check(selectedVehicle.danhSachGhe.size() + 1 == selectedVehicle.soChoNgoi,
                    "Thêm từng ghế chỉ được hoàn thiện đủ sức chứa; dùng API lưu toàn bộ sơ đồ.");

                int floor = positive(input.tang());
                check(selectedVehicle.loaiXe != null && floor <= selectedVehicle.loaiXe.soTang,
                    "Tầng vượt cấu hình loại xe");

                Ghe seat = new Ghe();
                seat.maGhe = UUID.randomUUID().toString();
                seat.xe = selectedVehicle;
                seat.viTri = seatCode(input.viTri());
                seat.tang = floor;

                check(!duplicateSeatPosition(selectedVehicle, seat.viTri, null), "Mã/vị trí ghế đã tồn tại trong xe.");

                if (input.trangThai() == null) {
                    seat.trangThai = TrangThaiGhe.HOAT_DONG;
                } else {
                    seat.trangThai = input.trangThai();
                }

                selectedVehicle.danhSachGhe.add(seat);
                new GheRepository(em).save(seat);

                Map<String, Object> result = view(seat);
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

    // Kiểm tra ghế thuộc đúng xe rồi trả thông tin ghế theo mã.
    public Map<String, Object> getSeat(String vehicle, String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Ghe seat = found(new GheRepository(em).lock(text(id, 40)));
                check(seat.xe.maXe.equals(text(vehicle, 40)), "Ghế không thuộc xe");

                Map<String, Object> result = view(seat);
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

    // Kiểm tra ghế trước khi từ chối xóa riêng lẻ để tránh làm thiếu sức chứa của xe.
    public void deleteSeat(String vehicle, String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Xe selectedVehicle = found(new XeRepository(em).lock(text(vehicle, 40)));
                Ghe seat = found(new GheRepository(em).lock(text(id, 40)));

                check(seat.xe.maXe.equals(selectedVehicle.maXe), "Ghế không thuộc xe");
                check(seat.danhSachVe.isEmpty() && seat.danhSachGiuCho.isEmpty(),
                    "Ghế đã có lịch sử vé hoặc giữ chỗ; không thể xóa");
                check(!hasActiveTrip(selectedVehicle), "Xe đang có lịch chuyến; không thể xóa ghế");

                throw new DomainException(409,
                    "Không xóa riêng một ghế làm thiếu sức chứa; hãy lưu toàn bộ sơ đồ thay thế.");
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Chuyến nháp, mở bán hoặc đang chạy đều được tính khi bảo vệ việc sửa xe và ghế.
    public static boolean active(ChuyenXe trip) {
        return TripSaleRules.scheduled(trip) || trip.trangThai == TrangThaiChuyen.DANG_CHAY;
    }

    // Kiểm tra đăng kiểm, sơ đồ ghế và lịch vận hành để xác định xe phù hợp với chuyến.
    public Map<String, Object> vehicleEligibility(String vehicle, String trip) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Xe selectedVehicle = found(new XeRepository(em).find(text(vehicle, 40)));
                ChuyenXe selectedTrip = found(em.find(ChuyenXe.class, text(trip, 40)));

                VehicleRules.ready(em, selectedVehicle.maXe, selectedTrip);
                AssignmentRules.validateVehicleSchedule(em, selectedTrip, selectedVehicle.maXe);
                check(!overlapsAnotherTrip(selectedVehicle, selectedTrip), "Xe đã có chuyến trùng thời gian");

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("maXe", selectedVehicle.maXe);
                result.put("maChuyen", selectedTrip.maChuyen);
                result.put("duDieuKien", true);

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

    private static String seatCode(String code) {
        String value = text(code, 20).toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9.-]{0,19}")) {
            throw new DomainException(400, "Mã/vị trí ghế chỉ dùng chữ, số, dấu chấm hoặc gạch ngang.");
        }
        return value;
    }

    // Hai khoảng thời gian trùng khi mỗi chuyến bắt đầu trước lúc chuyến kia kết thúc.
    private static boolean overlapsAnotherTrip(Xe vehicle, ChuyenXe trip) {
        LocalDateTime departure = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);

        for (ChuyenXe other : vehicle.danhSachChuyenXe) {
            if (other.maChuyen.equals(trip.maChuyen) || !active(other)) {
                continue;
            }

            LocalDateTime otherDeparture = LocalDateTime.of(other.ngayKhoiHanh, other.gioKhoiHanh);
            if (departure.isBefore(other.gioDenDuKien) && otherDeparture.isBefore(trip.gioDenDuKien)) {
                return true;
            }
        }
        return false;
    }

    // Lọc loại xe hoặc xe theo từ khóa, trạng thái, nhà xe và điều kiện của chuyến.
    public List<Map<String, Object>> filtered(String kind, String search, String type, String owner, String state, String tripId) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);

                String term;
                if (search == null) {
                    term = "";
                } else {
                    term = search.strip().toLowerCase(Locale.ROOT);
                }

                if (term.length() > 160) {
                    throw new DomainException(400, "Từ khóa quá dài.");
                }

                String operatorId = RequestIdentity.operatorId();
                List<Map<String, Object>> results = new ArrayList<>();

                if (kind.equals("loai-xe")) {
                    List<LoaiXe> types = new ArrayList<>(new LoaiXeRepository(em).list());
                    types.sort(Comparator.comparing(item -> item.maLoaiXe));

                    for (LoaiXe typeItem : types) {
                        String searchable = (typeItem.maLoaiXe + " " + typeItem.tenLoaiXe).toLowerCase(Locale.ROOT);
                        if (term.isEmpty() || searchable.contains(term)) {
                            results.add(view(typeItem));
                        }
                    }
                } else if (kind.equals("xe")) {
                    TrangThaiXe status = null;
                    if (state != null && !state.isBlank()) {
                        status = TrangThaiXe.valueOf(state);
                    }

                    ChuyenXe trip = null;
                    if (tripId != null && !tripId.isBlank()) {
                        trip = found(em.find(ChuyenXe.class, tripId));
                    }

                    if (trip != null && operatorId != null) {
                        var authenticatedUser = AccessControl.authenticated(em, RequestIdentity.get().user(), Instant.now());
                        AccessControl.trip(em, authenticatedUser, tripId);
                    }

                    for (Xe vehicle : new XeRepository(em).list()) {
                        if (operatorId != null && (vehicle.nhaXe == null || !operatorId.equals(vehicle.nhaXe.maNhaXe))) {
                            continue;
                        }

                        if (owner != null && !owner.isBlank()
                            && (vehicle.nhaXe == null || !owner.equals(vehicle.nhaXe.maNhaXe))) {
                            continue;
                        }

                        if (type != null && !type.isBlank()
                            && (vehicle.loaiXe == null || !type.equals(vehicle.loaiXe.maLoaiXe))) {
                            continue;
                        }

                        if (status != null && vehicle.trangThai != status) {
                            continue;
                        }

                        String searchable = (vehicle.bienSo + " " + vehicle.maXe).toLowerCase(Locale.ROOT);
                        if (!term.isEmpty() && !searchable.contains(term)) {
                            continue;
                        }

                        if (trip != null) {
                            try {
                                VehicleRules.ready(em, vehicle.maXe, trip);
                            } catch (DomainException invalid) {
                                continue;
                            }

                            if (overlapsAnotherTrip(vehicle, trip)) {
                                continue;
                            }
                        }

                        results.add(view(vehicle));
                    }

                    results.sort(Comparator.comparing(item -> item.get("maXe").toString()));
                } else {
                    throw new DomainException(404, "API không tồn tại");
                }

                transaction.commit();
                return results;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Lấy danh sách nhà xe để chọn khi quản lý xe, có giới hạn theo phạm vi tài khoản.
    public List<Map<String, Object>> operators() {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                String operatorId = RequestIdentity.operatorId();
                List<Map<String, Object>> results = new ArrayList<>();
                List<NhaXe> operators = em.createQuery("select n from NhaXe n order by n.tenNhaXe", NhaXe.class).getResultList();

                for (NhaXe operator : operators) {
                    if (operatorId != null && !operatorId.equals(operator.maNhaXe)) {
                        continue;
                    }

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("maNhaXe", operator.maNhaXe);
                    item.put("tenNhaXe", operator.tenNhaXe);
                    item.put("trangThai", operator.trangThai);
                    results.add(item);
                }

                transaction.commit();
                return results;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Kiểm tra toàn bộ sơ đồ trước khi lưu để một ghế sai không làm lưu dở các ghế còn lại.
    public List<Map<String, Object>> saveLayout(String id, VehicleInputs.SoDoGhe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                Xe vehicle = found(new XeRepository(em).lock(text(id, 40)));
                List<VehicleInputs.ViTriGhe> entries = input.danhSachGhe();

                if (entries == null || entries.size() != vehicle.soChoNgoi) {
                    throw new DomainException(400, "Sơ đồ phải có đúng " + vehicle.soChoNgoi + " ghế.");
                }

                check(vehicle.loaiXe != null && vehicle.soChoNgoi == vehicle.loaiXe.soChoNgoi,
                    "Sức chứa xe không khớp loại xe.");

                Set<String> ids = new HashSet<>();
                Set<String> codes = new HashSet<>();
                Map<String, Ghe> existing = new HashMap<>();

                for (Ghe seat : vehicle.danhSachGhe) {
                    existing.put(seat.maGhe, seat);
                }

                for (VehicleInputs.ViTriGhe entry : entries) {
                    if (entry == null || entry.trangThai() == null) {
                        throw new DomainException(400, "Thông tin/trạng thái ghế bị thiếu.");
                    }

                    if (!codes.add(seatCode(entry.viTri()))) {
                        throw new DomainException(400, "Mã/vị trí ghế trùng trong xe.");
                    }

                    if (entry.tang() == null || entry.tang() < 1 || entry.tang() > vehicle.loaiXe.soTang) {
                        throw new DomainException(400, "Tầng ghế không hợp lệ.");
                    }

                    if (entry.maGhe() != null && (!ids.add(entry.maGhe()) || !existing.containsKey(entry.maGhe()))) {
                        throw new DomainException(400, "Mã ghế trùng hoặc không thuộc xe.");
                    }
                }

                boolean changed = ids.size() != existing.size();
                for (VehicleInputs.ViTriGhe entry : entries) {
                    Ghe old = existing.get(entry.maGhe());
                    if (old == null || !old.viTri.equals(seatCode(entry.viTri()))
                        || old.tang != entry.tang() || old.trangThai != entry.trangThai()) {
                        changed = true;
                        break;
                    }
                }

                if (changed) {
                    check(!hasActiveTrip(vehicle), "Xe đang có lịch chuyến; không sửa sơ đồ ghế.");
                    check(!hasSeatHistory(existing.values()), "Sơ đồ đã có lịch sử vé/giữ chỗ; không được thay đổi.");

                    // Đổi vị trí tạm để hoán đổi ghế mà không vi phạm khóa duy nhất.
                    Set<String> parked = new HashSet<>(codes);
                    for (Ghe seat : existing.values()) {
                        parked.add(seat.viTri);
                    }

                    for (Ghe seat : existing.values()) {
                        String temporary;
                        do {
                            temporary = "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                        } while (!parked.add(temporary));
                        seat.viTri = temporary;
                    }
                    em.flush();

                    Iterator<Ghe> seatIterator = vehicle.danhSachGhe.iterator();
                    while (seatIterator.hasNext()) {
                        Ghe seat = seatIterator.next();
                        if (!ids.contains(seat.maGhe)) {
                            seatIterator.remove();
                        }
                    }
                    em.flush();

                    for (VehicleInputs.ViTriGhe entry : entries) {
                        Ghe seat = existing.get(entry.maGhe());
                        if (seat == null) {
                            seat = new Ghe();
                            seat.maGhe = UUID.randomUUID().toString();
                            seat.xe = vehicle;
                            vehicle.danhSachGhe.add(seat);
                        }

                        seat.viTri = seatCode(entry.viTri());
                        seat.tang = entry.tang();
                        seat.trangThai = entry.trangThai();

                        if (entry.maGhe() == null) {
                            em.persist(seat);
                        }
                    }
                    em.flush();
                }

                List<Map<String, Object>> result = seatResponses(vehicle.danhSachGhe);
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

    // Trả ghế của chuyến cùng trạng thái còn trống, đang giữ, đã bán hoặc ngừng bán.
    public Map<String, Object> tripSeats(String tripId) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, tripId);
                AccessControl.saleAllowed(em, tripId);

                ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
                LocalDateTime now = LocalDateTime.now(zone);
                List<Ghe> seats = new ArrayList<>(new GheRepository(em).byVehicle(trip.xe.maXe));
                seats.sort(SEAT_ORDER);

                List<Map<String, Object>> seatItems = new ArrayList<>();
                for (Ghe seat : seats) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("maGhe", seat.maGhe);
                    item.put("viTri", seat.viTri);
                    item.put("tang", seat.tang);
                    item.put("trangThaiSuDung", seat.trangThai);
                    item.put("tinhTrangKhaDung", VehicleRules.availability(em, trip, seat, now));
                    seatItems.add(item);
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("maChuyen", trip.maChuyen);
                result.put("maXe", trip.xe.maXe);
                result.put("serverTime", OffsetDateTime.now(zone));
                result.put("danhSachGhe", seatItems);

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

    // Xóa loại xe hoặc xe sau khi kiểm tra lịch sử sử dụng và các ràng buộc liên quan.
    public void delete(String kind, String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();

            try {
                AccessPolicy.guard(em);

                if (kind.equals("loai-xe")) {
                    LoaiXeRepository repository = new LoaiXeRepository(em);
                    LoaiXe vehicleType = found(repository.lock(text(id, 40)));
                    check(vehicleType.danhSachXe.isEmpty(), "Loại xe đang được sử dụng");
                    repository.delete(vehicleType);
                } else if (kind.equals("xe")) {
                    XeRepository repository = new XeRepository(em);
                    Xe vehicle = found(repository.lock(text(id, 40)));
                    check(vehicle.danhSachChuyenXe.isEmpty(), "Xe đã có lịch sử chuyến");

                    for (Ghe seat : vehicle.danhSachGhe) {
                        check(seat.danhSachVe.isEmpty() && seat.danhSachGiuCho.isEmpty(), "Ghế đã được sử dụng");
                    }

                    repository.delete(vehicle);
                } else {
                    throw new DomainException(404, "API không tồn tại");
                }

                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }
}