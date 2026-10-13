package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.Inputs;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Business rules and transactions for the route/trip section of the class diagram. */
public class RouteTripService {
    private final EntityManagerFactory factory;
    public RouteTripService(EntityManagerFactory factory) { this.factory = factory; }

    private <T> T read(Function<EntityManager, T> task) {
        try (EntityManager em = factory.createEntityManager()) { return task.apply(em); }
    }
    private <T> T write(Function<EntityManager, T> task) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                T result = task.apply(em);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new DomainException(400, field + " là bắt buộc");
        return value.trim();
    }
    private static String text(String value, String field, int max, boolean mandatory) {
        String result = mandatory ? required(value, field) : value;
        if (result != null && result.length() > max) throw new DomainException(400, field + " vượt quá " + max + " ký tự");
        return result;
    }
    private static <T> T found(EntityManager em, Class<T> type, String id) {
        T item = em.find(type, text(id, "Mã", 40, true));
        if (item == null) throw new DomainException(404, type.getSimpleName() + " không tồn tại: " + id);
        return item;
    }
    private static void newId(EntityManager em, Class<?> type, String id) {
        if (em.find(type, text(id, "Mã", 40, true)) != null)
            throw new DomainException(409, "Mã đã tồn tại: " + id);
    }
    private static BigDecimal amount(BigDecimal value, String field, boolean zeroAllowed) {
        if (value == null || value.signum() < (zeroAllowed ? 0 : 1))
            throw new DomainException(400, field + " không hợp lệ");
        return value;
    }
    private static BigDecimal decimal(BigDecimal value, String field, int integerDigits, boolean zeroAllowed) {
        amount(value, field, zeroAllowed);
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.precision() - normalized.scale() > integerDigits)
            throw new DomainException(400, field + " vượt giới hạn hoặc có quá hai chữ số thập phân");
        return value;
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static Map<String, Object> view(TinhThanh x) {
        return map("maTinhThanh", x.maTinhThanh, "tenTinhThanh", x.tenTinhThanh, "khuVuc", x.khuVuc);
    }
    private static Map<String, Object> view(BenXe x) {
        return map("maBenXe", x.maBenXe, "tenBenXe", x.tenBenXe, "diaChi", x.diaChi,
            "soDienThoai", x.soDienThoai, "maTinhThanh", x.tinhThanh.maTinhThanh, "trangThai", x.trangThai);
    }
    private static Map<String, Object> view(DiemDung x) {
        return map("maDiemDung", x.maDiemDung, "tenDiemDung", x.tenDiemDung, "diaChi", x.diaChi,
            "thuTu", x.thuTu, "thoiGianDung", x.thoiGianDung, "loaiDiemDung", x.loaiDiemDung);
    }
    private static Map<String, Object> view(TuyenXe x) {
        return map("maTuyen", x.maTuyen, "tenTuyen", x.tenTuyen, "tramKhoiHanh", x.tramKhoiHanh.maBenXe,
            "tramDen", x.tramDen.maBenXe, "tramTrungGian", x.tramTrungGian.stream().map(RouteTripService::view).toList(),
            "thoiGianKhoiHanh", x.thoiGianKhoiHanh, "khoangCachKM", x.khoangCachKM,
            "thoiGianDuKien", x.thoiGianDuKien, "giaCoBan", x.giaCoBan, "moTa", x.moTa,
            "trangThai", x.trangThai, "maNhaXe", x.nhaXe == null ? null : x.nhaXe.maNhaXe);
    }
    private static Map<String, Object> view(ChuyenXe x) {
        return map("maChuyen", x.maChuyen, "maTuyen", x.tuyenXe.maTuyen,
            "maXe", x.xe == null ? null : x.xe.maXe, "ngayKhoiHanh", x.ngayKhoiHanh,
            "gioKhoiHanh", x.gioKhoiHanh, "gioDenDuKien", x.gioDenDuKien, "trangThai", x.trangThai);
    }

    public Map<String, Object> createTinhThanh(Inputs.TinhThanh input) {
        return write(em -> {
            newId(em, TinhThanh.class, input.maTinhThanh());
            TinhThanh x = new TinhThanh();
            x.maTinhThanh = required(input.maTinhThanh(), "Mã tỉnh thành");
            x.tenTinhThanh = text(input.tenTinhThanh(), "Tên tỉnh thành", 120, true);
            x.khuVuc = text(input.khuVuc(), "Khu vực", 80, false);
            em.persist(x);
            return view(x);
        });
    }
    public List<Map<String, Object>> listTinhThanh() {
        return read(em -> em.createQuery("select x from TinhThanh x order by x.tenTinhThanh", TinhThanh.class)
            .getResultList().stream().map(RouteTripService::view).toList());
    }

    private static void apply(BenXe x, Inputs.BenXe input, EntityManager em) {
        x.tenBenXe = text(input.tenBenXe(), "Tên bến xe", 160, true);
        x.diaChi = text(input.diaChi(), "Địa chỉ", 255, true);
        x.soDienThoai = text(input.soDienThoai(), "Số điện thoại", 30, false);
        x.tinhThanh = found(em, TinhThanh.class, input.maTinhThanh());
        if (input.trangThai() != null) x.trangThai = input.trangThai();
    }
    public Map<String, Object> createBenXe(Inputs.BenXe input) {
        return write(em -> {
            newId(em, BenXe.class, input.maBenXe());
            BenXe x = new BenXe();
            x.maBenXe = required(input.maBenXe(), "Mã bến xe");
            apply(x, input, em);
            em.persist(x);
            return view(x);
        });
    }
    public Map<String, Object> updateBenXe(String id, Inputs.BenXe input) {
        return write(em -> {
            BenXe x = found(em, BenXe.class, id);
            apply(x, input, em);
            return view(x);
        });
    }
    public Map<String, Object> getBenXe(String id) { return read(em -> view(found(em, BenXe.class, id))); }
    public List<Map<String, Object>> listBenXe() {
        return read(em -> em.createQuery("select x from BenXe x order by x.tenBenXe", BenXe.class)
            .getResultList().stream().map(RouteTripService::view).toList());
    }
    public void deleteBenXe(String id) {
        write(em -> {
            BenXe x = found(em, BenXe.class, id);
            long refs = em.createQuery("select count(t) from TuyenXe t where t.tramKhoiHanh = :x or t.tramDen = :x", Long.class)
                .setParameter("x", x).getSingleResult();
            if (refs > 0) throw new DomainException(409, "Bến xe đang được tuyến sử dụng");
            em.remove(x);
            return null;
        });
    }

    private static void apply(TuyenXe x, Inputs.TuyenXe input, EntityManager em) {
        x.tenTuyen = text(input.tenTuyen(), "Tên tuyến", 160, true);
        x.tramKhoiHanh = found(em, BenXe.class, input.tramKhoiHanh());
        x.tramDen = found(em, BenXe.class, input.tramDen());
        if (x.tramKhoiHanh.maBenXe.equals(x.tramDen.maBenXe))
            throw new DomainException(400, "Trạm khởi hành và trạm đến phải khác nhau");
        x.thoiGianKhoiHanh = input.thoiGianKhoiHanh();
        x.khoangCachKM = input.khoangCachKM() == null ? null : decimal(input.khoangCachKM(), "Khoảng cách", 10, false);
        x.thoiGianDuKien = input.thoiGianDuKien() == null ? null : decimal(input.thoiGianDuKien(), "Thời gian dự kiến", 8, false);
        x.giaCoBan = decimal(input.giaCoBan(), "Giá cơ bản", 12, true);
        x.moTa = text(input.moTa(), "Mô tả", 1000, false);
        if (input.trangThai() != null) x.trangThai = input.trangThai();
        x.nhaXe = input.maNhaXe() == null ? null : found(em, NhaXe.class, input.maNhaXe());
    }
    public Map<String, Object> createTuyenXe(Inputs.TuyenXe input) {
        return write(em -> {
            newId(em, TuyenXe.class, input.maTuyen());
            TuyenXe x = new TuyenXe();
            x.maTuyen = required(input.maTuyen(), "Mã tuyến");
            apply(x, input, em);
            em.persist(x);
            return view(x);
        });
    }
    public Map<String, Object> updateTuyenXe(String id, Inputs.TuyenXe input) {
        return write(em -> {
            TuyenXe x = found(em, TuyenXe.class, id);
            apply(x, input, em);
            return view(x);
        });
    }
    public Map<String, Object> setTramDauCuoi(String id, Inputs.TramDauCuoi input) {
        return write(em -> {
            TuyenXe x = found(em, TuyenXe.class, id);
            x.tramKhoiHanh = found(em, BenXe.class, input.tramKhoiHanh());
            x.tramDen = found(em, BenXe.class, input.tramDen());
            if (x.tramKhoiHanh.maBenXe.equals(x.tramDen.maBenXe))
                throw new DomainException(400, "Trạm khởi hành và trạm đến phải khác nhau");
            return view(x);
        });
    }
    public Map<String, Object> getTuyenXe(String id) { return read(em -> view(found(em, TuyenXe.class, id))); }
    public List<Map<String, Object>> listTuyenXe() {
        return read(em -> em.createQuery("select x from TuyenXe x order by x.maTuyen", TuyenXe.class)
            .getResultList().stream().map(RouteTripService::view).toList());
    }
    public void deleteTuyenXe(String id) {
        write(em -> {
            TuyenXe x = found(em, TuyenXe.class, id);
            long count = em.createQuery("select count(c) from ChuyenXe c where c.tuyenXe = :x", Long.class)
                .setParameter("x", x).getSingleResult();
            if (count > 0) throw new DomainException(409, "Tuyến đã có chuyến xe");
            em.remove(x);
            return null;
        });
    }

    private static List<DiemDung> stops(EntityManager em, TuyenXe route) {
        return new ArrayList<>(em.createQuery("select d from DiemDung d where d.tuyenXe = :route order by d.thuTu", DiemDung.class)
            .setParameter("route", route).getResultList());
    }
    private static void renumber(EntityManager em, List<DiemDung> items) {
        park(em, items);
        for (int i = 0; i < items.size(); i++) items.get(i).thuTu = i + 1;
        em.flush();
    }
    private static void park(EntityManager em, List<DiemDung> items) {
        int start = items.stream().mapToInt(d -> d.thuTu).max().orElse(0) + items.size() + 1;
        for (int i = 0; i < items.size(); i++) items.get(i).thuTu = start + i;
        em.flush();
    }
    public Map<String, Object> addDiemDung(String routeId, Inputs.DiemDung input) {
        return write(em -> {
            TuyenXe route = found(em, TuyenXe.class, routeId);
            newId(em, DiemDung.class, input.maDiemDung());
            List<DiemDung> items = stops(em, route);
            int position = input.thuTu() == null ? items.size() + 1 : input.thuTu();
            if (position < 1 || position > items.size() + 1)
                throw new DomainException(400, "Thứ tự điểm dừng không hợp lệ");
            DiemDung x = new DiemDung();
            x.maDiemDung = required(input.maDiemDung(), "Mã điểm dừng");
            x.tenDiemDung = text(input.tenDiemDung(), "Tên điểm dừng", 160, true);
            x.diaChi = text(input.diaChi(), "Địa chỉ", 255, true);
            x.thoiGianDung = input.thoiGianDung() == null ? 0 : input.thoiGianDung();
            if (x.thoiGianDung < 0) throw new DomainException(400, "Thời gian dừng không hợp lệ");
            x.loaiDiemDung = Objects.requireNonNullElse(input.loaiDiemDung(), LoaiDiemDung.CA_HAI);
            x.tuyenXe = route;
            park(em, items);
            for (int i = 0; i < items.size(); i++) items.get(i).thuTu = i < position - 1 ? i + 1 : i + 2;
            x.thuTu = position;
            em.persist(x);
            em.flush();
            return view(x);
        });
    }
    public void deleteDiemDung(String routeId, String stopId) {
        write(em -> {
            TuyenXe route = found(em, TuyenXe.class, routeId);
            DiemDung x = found(em, DiemDung.class, stopId);
            if (!x.tuyenXe.maTuyen.equals(route.maTuyen)) throw new DomainException(404, "Điểm dừng không thuộc tuyến");
            em.remove(x);
            em.flush();
            renumber(em, stops(em, route));
            return null;
        });
    }
    public List<Map<String, Object>> reorderDiemDung(String routeId, Inputs.SapXepDiemDung input) {
        return write(em -> {
            TuyenXe route = found(em, TuyenXe.class, routeId);
            List<DiemDung> current = stops(em, route);
            List<String> ids = input.maDiemDungTheoThuTu();
            if (ids == null || ids.size() != current.size() || new HashSet<>(ids).size() != ids.size())
                throw new DomainException(400, "Danh sách điểm dừng phải chứa mỗi điểm đúng một lần");
            Map<String, DiemDung> byId = new HashMap<>();
            current.forEach(d -> byId.put(d.maDiemDung, d));
            List<DiemDung> ordered = new ArrayList<>();
            for (String id : ids) {
                DiemDung d = byId.get(id);
                if (d == null) throw new DomainException(400, "Điểm dừng không thuộc tuyến: " + id);
                ordered.add(d);
            }
            renumber(em, ordered);
            return ordered.stream().map(RouteTripService::view).toList();
        });
    }

    private static LocalDateTime expectedArrival(TuyenXe route, LocalDate date, LocalTime time) {
        if (date == null || time == null) throw new DomainException(400, "Ngày và giờ khởi hành là bắt buộc");
        if (route.thoiGianDuKien == null || route.thoiGianDuKien.signum() <= 0)
            throw new DomainException(400, "Tuyến chưa có thời gian dự kiến");
        long minutes = route.thoiGianDuKien.multiply(BigDecimal.valueOf(60))
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return LocalDateTime.of(date, time).plusMinutes(minutes);
    }
    public Map<String, Object> createChuyenXe(Inputs.ChuyenXe input) {
        return write(em -> {
            newId(em, ChuyenXe.class, input.maChuyen());
            TuyenXe route = found(em, TuyenXe.class, input.maTuyen());
            if (route.trangThai != TrangThaiTuyenXe.DANG_KHAI_THAC)
                throw new DomainException(409, "Tuyến chưa hoạt động");
            LocalTime time = input.gioKhoiHanh() == null ? route.thoiGianKhoiHanh : input.gioKhoiHanh();
            ChuyenXe x = new ChuyenXe();
            x.maChuyen = required(input.maChuyen(), "Mã chuyến");
            x.tuyenXe = route;
            x.ngayKhoiHanh = input.ngayKhoiHanh();
            x.gioKhoiHanh = time;
            x.gioDenDuKien = expectedArrival(route, x.ngayKhoiHanh, time);
            em.persist(x);
            if (input.maXe() != null) assignVehicle(em, x, input.maXe());
            return view(x);
        });
    }
    private static void assignVehicle(EntityManager em, ChuyenXe trip, String xeId) {
        if (trip.trangThai != TrangThaiChuyen.CHUA_KHOI_HANH)
            throw new DomainException(409, "Chỉ gán xe cho chuyến chưa khởi hành");
        Xe xe = em.find(Xe.class, text(xeId, "Mã xe", 40, true), LockModeType.PESSIMISTIC_WRITE);
        if (xe == null) throw new DomainException(404, "Xe không tồn tại: " + xeId);
        if (xe.trangThai != TrangThaiXe.SAN_SANG) throw new DomainException(409, "Xe không sẵn sàng");
        if (trip.tuyenXe.nhaXe != null && (xe.nhaXe == null ||
            !trip.tuyenXe.nhaXe.maNhaXe.equals(xe.nhaXe.maNhaXe)))
            throw new DomainException(409, "Xe không thuộc nhà xe khai thác tuyến");
        List<ChuyenXe> other = em.createQuery("select c from ChuyenXe c where c.xe = :xe and c.maChuyen <> :id and c.trangThai <> :cancelled and c.trangThai <> :done", ChuyenXe.class)
            .setParameter("xe", xe).setParameter("id", trip.maChuyen)
            .setParameter("cancelled", TrangThaiChuyen.DA_HUY).setParameter("done", TrangThaiChuyen.HOAN_TAT).getResultList();
        LocalDateTime start = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);
        for (ChuyenXe c : other) {
            LocalDateTime otherStart = LocalDateTime.of(c.ngayKhoiHanh, c.gioKhoiHanh);
            if (start.isBefore(c.gioDenDuKien) && otherStart.isBefore(trip.gioDenDuKien))
                throw new DomainException(409, "Xe đã có chuyến trùng thời gian");
        }
        trip.xe = xe;
    }
    public Map<String, Object> assignVehicle(String id, String xeId) {
        return write(em -> {
            ChuyenXe x = found(em, ChuyenXe.class, id);
            assignVehicle(em, x, xeId);
            return view(x);
        });
    }
    public Map<String, Object> updateChuyenXe(String id, Inputs.CapNhatChuyen input) {
        return write(em -> {
            ChuyenXe x = found(em, ChuyenXe.class, id);
            if (x.trangThai != TrangThaiChuyen.CHUA_KHOI_HANH)
                throw new DomainException(409, "Chỉ sửa chuyến chưa khởi hành");
            x.ngayKhoiHanh = input.ngayKhoiHanh();
            x.gioKhoiHanh = input.gioKhoiHanh();
            x.gioDenDuKien = expectedArrival(x.tuyenXe, x.ngayKhoiHanh, x.gioKhoiHanh);
            if (x.xe != null) assignVehicle(em, x, x.xe.maXe);
            return view(x);
        });
    }
    public Map<String, Object> transition(String id, TrangThaiChuyen target) {
        return write(em -> {
            ChuyenXe x = found(em, ChuyenXe.class, id);
            if (target == TrangThaiChuyen.DANG_CHAY && x.xe != null) assignVehicle(em, x, x.xe.maXe);
            boolean allowed = switch (target) {
                case DA_HUY -> x.trangThai == TrangThaiChuyen.CHUA_KHOI_HANH;
                case DANG_CHAY -> x.trangThai == TrangThaiChuyen.CHUA_KHOI_HANH && x.xe != null;
                case HOAN_TAT -> x.trangThai == TrangThaiChuyen.DANG_CHAY;
                default -> false;
            };
            if (!allowed) throw new DomainException(409, "Chuyển trạng thái chuyến không hợp lệ");
            x.trangThai = target;
            return view(x);
        });
    }
    public Map<String, Object> getChuyenXe(String id) { return read(em -> view(found(em, ChuyenXe.class, id))); }
    public List<Map<String, Object>> searchChuyenXe(String from, String to, LocalDate date) {
        String fromId = text(from, "Điểm đi", 40, true);
        String toId = text(to, "Điểm đến", 40, true);
        if (date == null) throw new DomainException(400, "Ngày đi là bắt buộc");
        return read(em -> em.createQuery("select c from ChuyenXe c where c.ngayKhoiHanh = :date and c.trangThai in (:scheduled, :running) and c.tuyenXe.trangThai = :active and " +
                "(c.tuyenXe.tramKhoiHanh.maBenXe = :from or c.tuyenXe.tramKhoiHanh.tinhThanh.maTinhThanh = :from) and " +
                "(c.tuyenXe.tramDen.maBenXe = :to or c.tuyenXe.tramDen.tinhThanh.maTinhThanh = :to) order by c.gioKhoiHanh", ChuyenXe.class)
            .setParameter("date", date).setParameter("scheduled", TrangThaiChuyen.CHUA_KHOI_HANH)
            .setParameter("running", TrangThaiChuyen.DANG_CHAY)
            .setParameter("active", TrangThaiTuyenXe.DANG_KHAI_THAC)
            .setParameter("from", fromId).setParameter("to", toId)
            .getResultList().stream().map(RouteTripService::view).toList());
    }
}
