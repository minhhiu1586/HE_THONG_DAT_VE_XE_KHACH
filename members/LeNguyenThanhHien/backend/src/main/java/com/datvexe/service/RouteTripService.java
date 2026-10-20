package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.Inputs;
import com.datvexe.repository.ChuyenXeRepository;
import com.datvexe.security.*;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Quản lý tỉnh, bến, tuyến và tìm kiếm chuyến; giữ transaction cho từng thao tác. */
public class RouteTripService {
    private final EntityManagerFactory factory;

    public RouteTripService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(400, field + " là bắt buộc");
        }
        return value.trim();
    }

    // Kiểm tra trường chuỗi có bắt buộc hay không và giới hạn độ dài trước khi dùng dữ liệu.
    private static String text(String value, String field, int max, boolean mandatory) {
        String result;
        if (mandatory) {
            result = required(value, field);
        } else {
            result = value;
        }
        if (result != null && result.length() > max) {
            throw new DomainException(400, field + " vượt quá " + max + " ký tự");
        }
        return result;
    }

    // T giữ kiểu của đối tượng cần tìm; không có bản ghi thì báo lỗi 404.
    private static <T> T found(EntityManager em, Class<T> type, String id) {
        T item = em.find(type, text(id, "Mã", 40, true));
        if (item == null) {
            throw new DomainException(404, type.getSimpleName() + " không tồn tại: " + id);
        }
        return item;
    }

    private static void newId(EntityManager em, Class<?> type, String id) {
        if (em.find(type, text(id, "Mã", 40, true)) != null) {
            throw new DomainException(409, "Mã đã tồn tại: " + id);
        }
    }

    // Kiểm tra giá trị số có bắt buộc lớn hơn 0 hay được phép bằng 0 theo từng trường.
    private static BigDecimal amount(BigDecimal value, String field, boolean zeroAllowed) {
        int minimumSign = 1;
        if (zeroAllowed) {
            minimumSign = 0;
        }
        if (value == null || value.signum() < minimumSign) {
            throw new DomainException(400, field + " không hợp lệ");
        }
        return value;
    }

    // Giới hạn phần nguyên và số lẻ bằng BigDecimal để giá trị lưu xuống CSDL không bị mất chính xác.
    private static BigDecimal decimal(BigDecimal value, String field, int integerDigits, boolean zeroAllowed) {
        amount(value, field, zeroAllowed);
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.precision() - normalized.scale() > integerDigits) {
            throw new DomainException(400, field + " vượt giới hạn hoặc có quá hai chữ số thập phân");
        }
        return value;
    }

    private static Map<String, Object> view(TinhThanh city) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maTinhThanh", city.maTinhThanh);
        result.put("tenTinhThanh", city.tenTinhThanh);
        result.put("khuVuc", city.khuVuc);
        return result;
    }

    // Chuyển thông tin bến xe, tỉnh thành và trạng thái hoạt động thành dữ liệu trả về.
    private static Map<String, Object> view(BenXe station) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maBenXe", station.maBenXe);
        result.put("tenBenXe", station.tenBenXe);
        result.put("diaChi", station.diaChi);
        result.put("soDienThoai", station.soDienThoai);
        result.put("maTinhThanh", station.tinhThanh.maTinhThanh);
        result.put("trangThai", station.trangThai);
        return result;
    }

    // Chuyển thông tin điểm dừng, thứ tự và thời gian dừng thành dữ liệu trả về.
    private static Map<String, Object> view(DiemDung stop) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maDiemDung", stop.maDiemDung);
        result.put("tenDiemDung", stop.tenDiemDung);
        result.put("diaChi", stop.diaChi);
        result.put("thuTu", stop.thuTu);
        result.put("thoiGianDung", stop.thoiGianDung);
        result.put("loaiDiemDung", stop.loaiDiemDung);
        return result;
    }

    // Ghép thông tin tuyến và các điểm dừng thành dữ liệu trả về cho giao diện.
    private static Map<String, Object> view(TuyenXe route) {
        List<Map<String, Object>> intermediateStops = new ArrayList<>();
        for (DiemDung stop : route.tramTrungGian) {
            intermediateStops.add(view(stop));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maTuyen", route.maTuyen);
        result.put("tenTuyen", route.tenTuyen);
        result.put("tramKhoiHanh", route.tramKhoiHanh.maBenXe);
        result.put("tramDen", route.tramDen.maBenXe);
        result.put("tramTrungGian", intermediateStops);
        result.put("thoiGianKhoiHanh", route.thoiGianKhoiHanh);
        result.put("khoangCachKM", route.khoangCachKM);
        result.put("thoiGianDuKien", route.thoiGianDuKien);
        result.put("giaCoBan", route.giaCoBan);
        result.put("moTa", route.moTa);
        result.put("trangThai", route.trangThai);
        if (route.nhaXe == null) {
            result.put("maNhaXe", null);
        } else {
            result.put("maNhaXe", route.nhaXe.maNhaXe);
        }
        return result;
    }

    // Ghép thông tin cơ bản của chuyến, giá vé và thời hạn nhận đặt vé để trả về giao diện.
    private static Map<String, Object> view(ChuyenXe trip) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maChuyen", trip.maChuyen);
        result.put("maTuyen", trip.tuyenXe.maTuyen);
        if (trip.xe == null) {
            result.put("maXe", null);
        } else {
            result.put("maXe", trip.xe.maXe);
        }
        result.put("ngayKhoiHanh", trip.ngayKhoiHanh);
        result.put("gioKhoiHanh", trip.gioKhoiHanh);
        result.put("gioDenDuKien", trip.gioDenDuKien);
        result.put("trangThai", trip.trangThai);
        if (trip.tuyenXe.giaCoBan == null) {
            result.put("giaVeDuKien", null);
        } else {
            result.put("giaVeDuKien", trip.tuyenXe.giaCoBan.stripTrailingZeros().toPlainString());
        }
        result.put("ngungNhanDatVeLuc", TripSaleRules.cutoff(trip).atZone(TripSaleRules.ZONE).toOffsetDateTime());
        return result;
    }

    private record SearchPoint(String kind, String code) {}

    // CITY là tỉnh, STATION là bến; mã không có tiền tố vẫn được nhận theo cách gọi cũ.
    private static SearchPoint searchPoint(String value, String field) {
        String input = required(value, field);
        int separator = input.indexOf(':');
        if (separator > 0) {
            String kind = input.substring(0, separator).toUpperCase(Locale.ROOT);
            if (kind.equals("CITY") || kind.equals("STATION")) {
                return new SearchPoint(kind, text(input.substring(separator + 1), field, 40, true));
            }
        }
        return new SearchPoint("ANY", text(input, field, 40, true));
    }

    // Quy đổi cả mã bến và mã tỉnh về tỉnh tương ứng để kiểm tra điểm đi/đến cùng tỉnh.
    private static String cityOf(EntityManager em, SearchPoint point) {
        if (point.kind().equals("CITY")) {
            if (em.find(TinhThanh.class, point.code()) == null) {
                throw new DomainException(400, "Tỉnh/thành phố không tồn tại: " + point.code());
            }
            return point.code();
        }
        if (point.kind().equals("STATION")) {
            BenXe station = em.find(BenXe.class, point.code());
            if (station == null) {
                throw new DomainException(400, "Bến xe không tồn tại: " + point.code());
            }
            return station.tinhThanh.maTinhThanh;
        }
        TinhThanh city = em.find(TinhThanh.class, point.code());
        if (city != null) {
            return city.maTinhThanh;
        }
        BenXe station = em.find(BenXe.class, point.code());
        if (station != null) {
            return station.tinhThanh.maTinhThanh;
        }
        throw new DomainException(400, "Điểm tìm kiếm không tồn tại: " + point.code());
    }

    // Ghép dữ liệu chuyến với tuyến, nhà xe, bến và ghế thành kết quả frontend cần hiển thị.
    private static Map<String, Object> searchView(EntityManager em, ChuyenXe trip, LocalDateTime now) {
        TuyenXe route = trip.tuyenXe;
        BenXe origin = route.tramKhoiHanh;
        BenXe destination = route.tramDen;
        Map<String, Object> result = view(trip);
        result.put("tenTuyen", route.tenTuyen);
        if (route.nhaXe == null) {
            result.put("maNhaXe", null);
            result.put("tenNhaXe", null);
            result.put("logoNhaXe", null);
        } else {
            result.put("maNhaXe", route.nhaXe.maNhaXe);
            result.put("tenNhaXe", route.nhaXe.tenNhaXe);
            result.put("logoNhaXe", route.nhaXe.logoUrl);
        }
        result.put("maBenDi", origin.maBenXe);
        result.put("tenBenDi", origin.tenBenXe);
        result.put("diaChiBenDi", origin.diaChi);
        result.put("maTinhDi", origin.tinhThanh.maTinhThanh);
        result.put("tenTinhDi", origin.tinhThanh.tenTinhThanh);
        result.put("maBenDen", destination.maBenXe);
        result.put("tenBenDen", destination.tenBenXe);
        result.put("diaChiBenDen", destination.diaChi);
        result.put("maTinhDen", destination.tinhThanh.maTinhThanh);
        result.put("tenTinhDen", destination.tinhThanh.tenTinhThanh);
        result.put("thoiLuongPhut", Duration.between(LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh), trip.gioDenDuKien).toMinutes());
        List<DiemDung> orderedStops = new ArrayList<>(route.tramTrungGian);
        orderedStops.sort(Comparator.comparingInt(stop -> stop.thuTu));
        List<Map<String, Object>> stopResponses = new ArrayList<>();
        for (DiemDung stop : orderedStops) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("maDiemDung", stop.maDiemDung);
            item.put("tenDiemDung", stop.tenDiemDung);
            item.put("diaChi", stop.diaChi);
            item.put("thuTu", stop.thuTu);
            item.put("thoiGianDung", stop.thoiGianDung);
            stopResponses.add(item);
        }
        result.put("diemDung", stopResponses);
        if (trip.xe == null) {
            result.put("bienSo", null);
        } else {
            result.put("bienSo", trip.xe.bienSo);
        }
        if (trip.xe == null || trip.xe.loaiXe == null) {
            result.put("maLoaiXe", null);
            result.put("tenLoaiXe", null);
            result.put("soChoNgoi", 0);
            result.put("tongSoGhe", 0L);
            result.put("soGheTrong", 0L);
        } else {
            result.put("maLoaiXe", trip.xe.loaiXe.maLoaiXe);
            result.put("tenLoaiXe", trip.xe.loaiXe.tenLoaiXe);
            result.put("soChoNgoi", trip.xe.soChoNgoi);
            Long total = em.createQuery("select count(g) from Ghe g where g.xe = :vehicle", Long.class)
                .setParameter("vehicle", trip.xe)
                .getSingleResult();
            // Chỉ đếm ghế hoạt động, chưa có vé hiệu lực và không bị giữ còn hạn trên chuyến này.
            Long available = em.createQuery("select count(g) from Ghe g where g.xe = :vehicle and"
                + " g.trangThai = :active and not exists (select v.maVe" + " from VeXe v where v.chuyenXe = :trip and v.ghe = g and"
                + " v.trangThai in :sold) and not exists (select h.maGiuCho" + " from GiuCho h where h.chuyenXe = :trip and h.ghe = g"
                + " and h.trangThai = :holding and h.thoiDiemHetHan >" + " :now)", Long.class)
                .setParameter("vehicle", trip.xe)
                .setParameter("active", TrangThaiGhe.HOAT_DONG)
                .setParameter("trip", trip)
                .setParameter("sold", List.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE))
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                .setParameter("now", now)
                .getSingleResult();
            result.put("tongSoGhe", total);
            result.put("soGheTrong", available);
        }
        return result;
    }

    // Tạo tỉnh thành mới sau khi kiểm tra mã và các thông tin bắt buộc.
    public Map<String, Object> createTinhThanh(Inputs.TinhThanh input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                newId(em, TinhThanh.class, input.maTinhThanh());
                TinhThanh city = new TinhThanh();
                city.maTinhThanh = required(input.maTinhThanh(), "Mã tỉnh thành");
                city.tenTinhThanh = text(input.tenTinhThanh(), "Tên tỉnh thành", 120, true);
                city.khuVuc = text(input.khuVuc(), "Khu vực", 80, false);
                em.persist(city);

                Map<String, Object> responseData = view(city);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Cập nhật thông tin tỉnh thành đã tồn tại sau khi kiểm tra dữ liệu nhập.
    public Map<String, Object> updateTinhThanh(String id, Inputs.CapNhatTinhThanh input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TinhThanh city = found(em, TinhThanh.class, id);
                city.tenTinhThanh = text(input.tenTinhThanh(), "Tên tỉnh thành", 120, true);
                city.khuVuc = text(input.khuVuc(), "Khu vực", 80, false);

                Map<String, Object> responseData = view(city);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Tìm tỉnh thành theo mã và trả dữ liệu cho giao diện.
    public Map<String, Object> getTinhThanh(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                Map<String, Object> responseData = view(found(em, TinhThanh.class, id));
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Lọc danh sách tỉnh thành theo từ khóa và sắp xếp theo tên.
    public List<Map<String, Object>> listTinhThanh(String search) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                String value;
                if (search == null || search.isBlank()) {
                    value = null;
                } else {
                    value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                }
                String jpql = "select x from TinhThanh x";
                if (value != null) {
                    jpql += " where lower(x.maTinhThanh) like :search" + " or lower(x.tenTinhThanh) like :search";
                }
                jpql += " order by x.tenTinhThanh";
                TypedQuery<TinhThanh> query = em.createQuery(jpql, TinhThanh.class);
                if (value != null) {
                    query.setParameter("search", value);
                }

                List<Map<String, Object>> responseData = new ArrayList<>();
                for (TinhThanh item : query.getResultList()) {
                    responseData.add(view(item));
                }
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Xóa tỉnh thành khi không còn bến xe tham chiếu đến tỉnh thành đó.
    public void deleteTinhThanh(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TinhThanh city = found(em, TinhThanh.class, id);
                long refs = em.createQuery("select count(b) from BenXe b where b.tinhThanh = :x", Long.class)
                    .setParameter("x", city)
                    .getSingleResult();
                if (refs > 0) {
                    throw new DomainException(409, "Tỉnh/thành phố đang được bến xe sử dụng");
                }
                em.remove(city);
                transaction.commit();
                return;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Kiểm tra và gán thông tin bến xe cùng tỉnh thành và trạng thái hoạt động.
    private static void apply(BenXe station, Inputs.BenXe input, EntityManager em) {
        station.tenBenXe = text(input.tenBenXe(), "Tên bến xe", 160, true);
        station.diaChi = text(input.diaChi(), "Địa chỉ", 255, true);
        station.soDienThoai = text(input.soDienThoai(), "Số điện thoại", 30, false);
        station.tinhThanh = found(em, TinhThanh.class, input.maTinhThanh());
        if (input.trangThai() != null) {
            station.trangThai = input.trangThai();
        }
    }

    // Tạo bến xe mới sau khi kiểm tra mã bến và tỉnh thành liên quan.
    public Map<String, Object> createBenXe(Inputs.BenXe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                newId(em, BenXe.class, input.maBenXe());
                BenXe station = new BenXe();
                station.maBenXe = required(input.maBenXe(), "Mã bến xe");
                apply(station, input, em);
                em.persist(station);

                Map<String, Object> responseData = view(station);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Sửa bến xe và bảo vệ tên, địa chỉ, tỉnh thành của bến đã có chuyến sử dụng.
    public Map<String, Object> updateBenXe(String id, Inputs.BenXe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                BenXe station = found(em, BenXe.class, id);
                boolean usedByTrip = em.createQuery("select count(c) from ChuyenXe c where" + " c.tuyenXe.tramKhoiHanh = :x or"
                    + " c.tuyenXe.tramDen = :x", Long.class)
                    .setParameter("x", station)
                    .getSingleResult() > 0;
                if (usedByTrip) {
                    String newName = input.tenBenXe();
                    if (newName != null) {
                        newName = newName.trim();
                    }
                    String newAddress = input.diaChi();
                    if (newAddress != null) {
                        newAddress = newAddress.trim();
                    }
                    boolean nameChanged = !Objects.equals(station.tenBenXe, newName);
                    boolean addressChanged = !Objects.equals(station.diaChi, newAddress);
                    boolean cityChanged = !Objects.equals(station.tinhThanh.maTinhThanh, input.maTinhThanh());
                    if (nameChanged || addressChanged || cityChanged) {
                        throw new DomainException(409, "Không thể đổi tên, địa chỉ hoặc tỉnh của bến đã có chuyến sử dụng;"
                            + " thao tác này sẽ làm sai lệch lịch sử");
                    }
                }
                apply(station, input, em);

                Map<String, Object> responseData = view(station);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Tìm bến xe theo mã và trả thông tin cùng tỉnh thành liên quan.
    public Map<String, Object> getBenXe(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                Map<String, Object> responseData = view(found(em, BenXe.class, id));
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Lọc bến xe theo tỉnh thành, từ khóa và trạng thái hoạt động.
    public List<Map<String, Object>> listBenXe(String cityId, String search, TrangThaiBenXe status) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                List<String> where = new ArrayList<>();
                if (cityId != null && !cityId.isBlank()) {
                    where.add("x.tinhThanh.maTinhThanh = :city");
                }
                if (search != null && !search.isBlank()) {
                    where.add("(lower(x.maBenXe) like :search or lower(x.tenBenXe) like :search)");
                }
                if (status != null) {
                    where.add("x.trangThai = :status");
                }
                String jpql = "select x from BenXe x";
                if (!where.isEmpty()) {
                    jpql += " where " + String.join(" and ", where);
                }
                jpql += " order by x.tenBenXe";
                TypedQuery<BenXe> query = em.createQuery(jpql, BenXe.class);
                if (cityId != null && !cityId.isBlank()) {
                    query.setParameter("city", cityId.trim());
                }
                if (search != null && !search.isBlank()) {
                    query.setParameter("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
                }
                if (status != null) {
                    query.setParameter("status", status);
                }

                List<Map<String, Object>> responseData = new ArrayList<>();
                for (BenXe item : query.getResultList()) {
                    responseData.add(view(item));
                }
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Xóa bến xe khi bến không còn được dùng làm điểm đầu hoặc điểm cuối của tuyến.
    public void deleteBenXe(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                BenXe station = found(em, BenXe.class, id);
                long refs = em.createQuery("select count(t) from TuyenXe t where t.tramKhoiHanh = :x" + " or t.tramDen = :x", Long.class)
                    .setParameter("x", station)
                    .getSingleResult();
                if (refs > 0) {
                    throw new DomainException(409, "Bến xe đang được tuyến sử dụng");
                }
                em.remove(station);
                transaction.commit();
                return;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Kiểm tra và gán cấu hình tuyến về bến đầu cuối, thời gian, giá vé, trạng thái và nhà xe.
    private static void apply(TuyenXe route, Inputs.TuyenXe input, EntityManager em) {
        route.tenTuyen = text(input.tenTuyen(), "Tên tuyến", 160, true);
        route.tramKhoiHanh = found(em, BenXe.class, input.tramKhoiHanh());
        route.tramDen = found(em, BenXe.class, input.tramDen());
        if (route.tramKhoiHanh.maBenXe.equals(route.tramDen.maBenXe)) {
            throw new DomainException(400, "Trạm khởi hành và trạm đến phải khác nhau");
        }
        if (route.tramKhoiHanh.trangThai != TrangThaiBenXe.HOAT_DONG || route.tramDen.trangThai != TrangThaiBenXe.HOAT_DONG) {
            throw new DomainException(409, "Bến khởi hành và bến đến phải đang hoạt động để cấu hình tuyến");
        }
        route.thoiGianKhoiHanh = input.thoiGianKhoiHanh();
        if (input.khoangCachKM() == null) {
            route.khoangCachKM = null;
        } else {
            route.khoangCachKM = decimal(input.khoangCachKM(), "Khoảng cách", 10, false);
        }
        route.thoiGianDuKien = decimal(input.thoiGianDuKien(), "Thời gian dự kiến", 8, false);
        route.giaCoBan = decimal(input.giaCoBan(), "Giá cơ bản", 12, true);
        route.moTa = text(input.moTa(), "Mô tả", 1000, false);
        if (input.trangThai() != null) {
            route.trangThai = input.trangThai();
        }
        String operatorId = RequestIdentity.operatorId();
        if (operatorId != null) {
            route.nhaXe = found(em, NhaXe.class, operatorId);
        } else if (input.maNhaXe() != null) {
            route.nhaXe = found(em, NhaXe.class, input.maNhaXe());
        }
    }

    // Tạo tuyến mới sau khi kiểm tra mã tuyến và các thông tin cấu hình tuyến.
    public Map<String, Object> createTuyenXe(Inputs.TuyenXe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                newId(em, TuyenXe.class, input.maTuyen());
                TuyenXe route = new TuyenXe();
                route.maTuyen = required(input.maTuyen(), "Mã tuyến");
                apply(route, input, em);
                em.persist(route);

                Map<String, Object> responseData = view(route);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Sửa cấu hình tuyến khi tuyến chưa có chuyến để không làm sai lệch lịch sử hành trình.
    public Map<String, Object> updateTuyenXe(String id, Inputs.TuyenXe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, id);
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Không thể sửa cấu hình tuyến đã có chuyến; hãy tạo tuyến mới để giữ"
                        + " nguyên lịch sử");
                }
                apply(route, input, em);

                Map<String, Object> responseData = view(route);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Đổi bến đầu và bến cuối của tuyến khi các điều kiện sửa cấu hình cho phép.
    public Map<String, Object> setTramDauCuoi(String id, Inputs.TramDauCuoi input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, id);
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Không thể đổi bến của tuyến đã có chuyến; hãy tạo tuyến mới để giữ"
                        + " nguyên lịch sử");
                }
                route.tramKhoiHanh = found(em, BenXe.class, input.tramKhoiHanh());
                route.tramDen = found(em, BenXe.class, input.tramDen());
                if (route.tramKhoiHanh.maBenXe.equals(route.tramDen.maBenXe)) {
                    throw new DomainException(400, "Trạm khởi hành và trạm đến phải khác nhau");
                }

                Map<String, Object> responseData = view(route);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Tìm tuyến theo mã và trả thông tin tuyến cùng danh sách điểm dừng.
    public Map<String, Object> getTuyenXe(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                Map<String, Object> responseData = view(found(em, TuyenXe.class, id));
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Đổi trạng thái tuyến và kiểm tra thời lượng cùng hai bến trước khi đưa vào khai thác.
    public Map<String, Object> setTrangThaiTuyenXe(String id, TrangThaiTuyenXe status) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                if (status == null) {
                    throw new DomainException(400, "Trạng thái tuyến là bắt buộc");
                }
                TuyenXe route = found(em, TuyenXe.class, id);
                if (status == TrangThaiTuyenXe.DANG_KHAI_THAC) {
                    if (route.thoiGianDuKien == null || route.thoiGianDuKien.signum() <= 0) {
                        throw new DomainException(409, "Tuyến cần có thời lượng dự kiến hợp lệ trước khi khai thác");
                    }
                    if (route.tramKhoiHanh.trangThai != TrangThaiBenXe.HOAT_DONG || route.tramDen.trangThai != TrangThaiBenXe.HOAT_DONG) {
                        throw new DomainException(409, "Hai bến của tuyến phải đang hoạt động");
                    }
                }
                route.trangThai = status;

                Map<String, Object> responseData = view(route);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Lọc danh sách tuyến theo bến đi, bến đến, từ khóa, trạng thái và phạm vi nhà xe.
    public List<Map<String, Object>> listTuyenXe(String start, String end, String operator, TrangThaiTuyenXe status) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                List<String> where = new ArrayList<>();
                String scopedOperator = RequestIdentity.operatorId();
                if (scopedOperator != null) {
                    where.add("x.nhaXe.maNhaXe = :owner");
                }
                if (start != null && !start.isBlank()) {
                    where.add("x.tramKhoiHanh.maBenXe = :start");
                }
                if (end != null && !end.isBlank()) {
                    where.add("x.tramDen.maBenXe = :end");
                }
                if (status != null) {
                    where.add("x.trangThai = :status");
                }
                if (scopedOperator == null && operator != null && !operator.isBlank()) {
                    where.add("x.nhaXe.maNhaXe = :operator");
                }
                String jpql = "select x from TuyenXe x";
                if (!where.isEmpty()) {
                    jpql += " where " + String.join(" and ", where);
                }
                jpql += " order by x.maTuyen";
                TypedQuery<TuyenXe> query = em.createQuery(jpql, TuyenXe.class);
                if (scopedOperator != null) {
                    query.setParameter("owner", scopedOperator);
                }
                if (start != null && !start.isBlank()) {
                    query.setParameter("start", start.trim());
                }
                if (end != null && !end.isBlank()) {
                    query.setParameter("end", end.trim());
                }
                if (status != null) {
                    query.setParameter("status", status);
                }
                if (scopedOperator == null && operator != null && !operator.isBlank()) {
                    query.setParameter("operator", operator.trim());
                }

                List<Map<String, Object>> responseData = new ArrayList<>();
                for (TuyenXe item : query.getResultList()) {
                    responseData.add(view(item));
                }
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    public List<Map<String, Object>> listTuyenXe() {
        return listTuyenXe(null, null, null, null);
    }

    private static boolean hasTrips(EntityManager em, TuyenXe route) {
        return em.createQuery("select count(c) from ChuyenXe c where c.tuyenXe = :x", Long.class)
            .setParameter("x", route)
            .getSingleResult() > 0;
    }

    // Xóa tuyến khi chưa có chuyến xe tham chiếu đến tuyến đó.
    public void deleteTuyenXe(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, id);
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Tuyến đã có chuyến xe");
                }
                em.remove(route);
                transaction.commit();
                return;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    private static List<DiemDung> stops(EntityManager em, TuyenXe route) {
        return new ArrayList<>(em.createQuery("select d from DiemDung d where d.tuyenXe = :route order by" + " d.thuTu", DiemDung.class)
            .setParameter("route", route).getResultList());
    }

    // Sau khi đưa thứ tự về vùng tạm, đánh lại liên tiếp từ 1 theo danh sách đã sắp xếp.
    private static void renumber(EntityManager em, List<DiemDung> items) {
        park(em, items);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).thuTu = i + 1;
        }
        em.flush();
    }

    // Chuyển thứ tự điểm dừng sang vùng số tạm để sắp xếp lại không bị trùng khóa.
    private static void park(EntityManager em, List<DiemDung> items) {
        int maxPosition = 0;
        if (!items.isEmpty()) {
            maxPosition = items.get(0).thuTu;
            for (DiemDung stop : items) {
                maxPosition = Math.max(maxPosition, stop.thuTu);
            }
        }
        int start = maxPosition + items.size() + 1;
        for (int i = 0; i < items.size(); i++) {
            items.get(i).thuTu = start + i;
        }
        em.flush();
    }

    // Thêm điểm dừng vào vị trí được chọn và điều chỉnh thứ tự các điểm dừng còn lại.
    public Map<String, Object> addDiemDung(String routeId, Inputs.DiemDung input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, routeId);
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Không thể sửa điểm dừng của tuyến đã có chuyến");
                }
                newId(em, DiemDung.class, input.maDiemDung());
                List<DiemDung> items = stops(em, route);
                int position;
                if (input.thuTu() == null) {
                    position = items.size() + 1;
                } else {
                    position = input.thuTu();
                }
                if (position < 1 || position > items.size() + 1) {
                    throw new DomainException(400, "Thứ tự điểm dừng không hợp lệ");
                }
                DiemDung stop = new DiemDung();
                stop.maDiemDung = required(input.maDiemDung(), "Mã điểm dừng");
                stop.tenDiemDung = text(input.tenDiemDung(), "Tên điểm dừng", 160, true);
                stop.diaChi = text(input.diaChi(), "Địa chỉ", 255, true);
                if (input.thoiGianDung() == null) {
                    stop.thoiGianDung = 0;
                } else {
                    stop.thoiGianDung = input.thoiGianDung();
                }
                if (stop.thoiGianDung < 0) {
                    throw new DomainException(400, "Thời gian dừng không hợp lệ");
                }
                stop.loaiDiemDung = input.loaiDiemDung();
                if (stop.loaiDiemDung == null) {
                    stop.loaiDiemDung = LoaiDiemDung.CA_HAI;
                }
                stop.tuyenXe = route;
                park(em, items);
                for (int i = 0; i < items.size(); i++) {
                    if (i < position - 1) {
                        items.get(i).thuTu = i + 1;
                    } else {
                        items.get(i).thuTu = i + 2;
                    }
                }
                stop.thuTu = position;
                em.persist(stop);
                em.flush();

                Map<String, Object> responseData = view(stop);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Xóa điểm dừng của tuyến được phép sửa rồi đánh lại thứ tự các điểm còn lại.
    public void deleteDiemDung(String routeId, String stopId) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, routeId);
                DiemDung stop = found(em, DiemDung.class, stopId);
                if (!stop.tuyenXe.maTuyen.equals(route.maTuyen)) {
                    throw new DomainException(404, "Điểm dừng không thuộc tuyến");
                }
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Không thể sửa điểm dừng của tuyến đã có chuyến");
                }
                em.remove(stop);
                em.flush();
                renumber(em, stops(em, route));
                transaction.commit();
                return;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Sửa thông tin điểm dừng khi tuyến chưa có chuyến sử dụng.
    public Map<String, Object> updateDiemDung(String routeId, String stopId, Inputs.CapNhatDiemDung input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, routeId);
                DiemDung stop = found(em, DiemDung.class, stopId);
                if (!stop.tuyenXe.maTuyen.equals(route.maTuyen)) {
                    throw new DomainException(404, "Điểm dừng không thuộc tuyến");
                }
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Không thể sửa điểm dừng của tuyến đã có chuyến");
                }
                stop.tenDiemDung = text(input.tenDiemDung(), "Tên điểm dừng", 160, true);
                stop.diaChi = text(input.diaChi(), "Địa chỉ", 255, true);
                if (input.thoiGianDung() == null || input.thoiGianDung() < 0) {
                    throw new DomainException(400, "Thời gian dừng không hợp lệ");
                }
                stop.thoiGianDung = input.thoiGianDung();
                stop.loaiDiemDung = input.loaiDiemDung();
                if (stop.loaiDiemDung == null) {
                    stop.loaiDiemDung = LoaiDiemDung.CA_HAI;
                }

                Map<String, Object> responseData = view(stop);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Chỉ nhận danh sách chứa đủ mỗi điểm dừng đúng một lần để không mất hoặc nhân đôi điểm.
    public List<Map<String, Object>> reorderDiemDung(String routeId, Inputs.SapXepDiemDung input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TuyenXe route = found(em, TuyenXe.class, routeId);
                if (hasTrips(em, route)) {
                    throw new DomainException(409, "Không thể sửa điểm dừng của tuyến đã có chuyến");
                }
                List<DiemDung> current = stops(em, route);
                List<String> ids = input.maDiemDungTheoThuTu();
                if (ids == null || ids.size() != current.size() || new HashSet<>(ids).size() != ids.size()) {
                    throw new DomainException(400, "Danh sách điểm dừng phải chứa mỗi điểm đúng một lần");
                }
                Map<String, DiemDung> byId = new HashMap<>();
                for (DiemDung stop : current) {
                    byId.put(stop.maDiemDung, stop);
                }
                List<DiemDung> ordered = new ArrayList<>();
                for (String id : ids) {
                    DiemDung d = byId.get(id);
                    if (d == null) {
                        throw new DomainException(400, "Điểm dừng không thuộc tuyến: " + id);
                    }
                    ordered.add(d);
                }
                renumber(em, ordered);

                List<Map<String, Object>> responseData = new ArrayList<>();
                for (DiemDung stop : ordered) {
                    responseData.add(view(stop));
                }
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Đổi thời lượng tuyến từ giờ sang phút rồi cộng vào ngày giờ đi, kể cả khi đến vào hôm sau.
    private static LocalDateTime expectedArrival(TuyenXe route, LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            throw new DomainException(400, "Ngày và giờ khởi hành là bắt buộc");
        }
        if (route.thoiGianDuKien == null || route.thoiGianDuKien.signum() <= 0) {
            throw new DomainException(400, "Tuyến chưa có thời gian dự kiến");
        }
        BigDecimal durationInMinutes = route.thoiGianDuKien.multiply(BigDecimal.valueOf(60));
        long minutes = durationInMinutes.setScale(0, RoundingMode.HALF_UP).longValueExact();
        return LocalDateTime.of(date, time).plusMinutes(minutes);
    }

    // Tạo chuyến từ tuyến đang khai thác, tính giờ đến dự kiến và gán xe nếu có yêu cầu.
    public Map<String, Object> createChuyenXe(Inputs.ChuyenXe input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                newId(em, ChuyenXe.class, input.maChuyen());
                TuyenXe route = found(em, TuyenXe.class, input.maTuyen());
                AccessControl.businessAllowed(em, route.nhaXe);
                if (route.trangThai != TrangThaiTuyenXe.DANG_KHAI_THAC) {
                    throw new DomainException(409, "Tuyến chưa hoạt động");
                }
                if (route.tramKhoiHanh.trangThai != TrangThaiBenXe.HOAT_DONG || route.tramDen.trangThai != TrangThaiBenXe.HOAT_DONG) {
                    throw new DomainException(409, "Không thể tạo chuyến mới cho tuyến có bến đã ngừng hoạt động");
                }
                LocalTime time;
                if (input.gioKhoiHanh() == null) {
                    time = route.thoiGianKhoiHanh;
                } else {
                    time = input.gioKhoiHanh();
                }
                ChuyenXe trip = new ChuyenXe();
                trip.maChuyen = required(input.maChuyen(), "Mã chuyến");
                trip.tuyenXe = route;
                trip.ngayKhoiHanh = input.ngayKhoiHanh();
                trip.gioKhoiHanh = time;
                trip.gioDenDuKien = expectedArrival(route, trip.ngayKhoiHanh, time);
                em.persist(trip);
                if (input.maXe() != null) {
                    assignVehicle(em, trip, input.maXe());
                }

                Map<String, Object> responseData = view(trip);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Kiểm tra xe và lịch trước khi gán; lịch sử vé/giữ chỗ đã có sẽ chặn việc đổi xe.
    private static void assignVehicle(EntityManager em, ChuyenXe trip, String xeId) {
        if (!TripSaleRules.scheduled(trip)) {
            throw new DomainException(409, "Chỉ gán xe cho chuyến chưa khởi hành");
        }
        if (trip.trangThai == TrangThaiChuyen.DANG_MO_BAN && (trip.xe == null || !trip.xe.maXe.equals(xeId))) {
            throw new DomainException(409, "Không đổi xe chuyến đang mở bán; cần xử lý vận hành trước.");
        }
        Xe xe = em.find(Xe.class, text(xeId, "Mã xe", 40, true), LockModeType.PESSIMISTIC_WRITE);
        if (xe == null) {
            throw new DomainException(404, "Xe không tồn tại: " + xeId);
        }
        if (xe.trangThai != TrangThaiXe.SAN_SANG) {
            throw new DomainException(409, "Xe không sẵn sàng");
        }
        if (trip.tuyenXe.nhaXe != null && (xe.nhaXe == null || !trip.tuyenXe.nhaXe.maNhaXe.equals(xe.nhaXe.maNhaXe))) {
            throw new DomainException(409, "Xe không thuộc nhà xe khai thác tuyến");
        }
        List<ChuyenXe> other = em.createQuery("select c from ChuyenXe c where c.xe = :xe and c.maChuyen <> :id"
            + " and c.trangThai <> :cancelled and c.trangThai <> :done", ChuyenXe.class)
            .setParameter("xe", xe)
            .setParameter("id", trip.maChuyen)
            .setParameter("cancelled", TrangThaiChuyen.DA_HUY)
            .setParameter("done", TrangThaiChuyen.HOAN_TAT)
            .getResultList();
        LocalDateTime start = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh);
        for (ChuyenXe c : other) {
            LocalDateTime otherStart = LocalDateTime.of(c.ngayKhoiHanh, c.gioKhoiHanh);
            if (start.isBefore(c.gioDenDuKien) && otherStart.isBefore(trip.gioDenDuKien)) {
                throw new DomainException(409, "Xe đã có chuyến trùng thời gian");
            }
        }
        if ((trip.xe == null || !trip.xe.maXe.equals(xe.maXe)) && VehicleRules.hasTicketsOrHolds(em, trip)) {
            throw new DomainException(409, "Không thể đổi xe của chuyến đã có vé hoặc giữ chỗ.");
        }
        VehicleRules.ready(em, xe.maXe, trip);
        AssignmentRules.validateVehicleSchedule(em, trip, xe.maXe);
        trip.xe = xe;
        AssignmentRules.validateTrip(em, trip);
        if (trip.trangThai == TrangThaiChuyen.DANG_MO_BAN) {
            AssignmentRules.readyForOperation(em, trip);
        }
    }

    // Gán xe cho chuyến sau khi kiểm tra trạng thái, lịch xe và các ràng buộc liên quan.
    public Map<String, Object> assignVehicle(String id, String xeId) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = found(em, ChuyenXe.class, id);
                assignVehicle(em, trip, xeId);

                Map<String, Object> responseData = view(trip);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Sửa lịch của chuyến nháp và kiểm tra lại xe cùng phân công bị ảnh hưởng.
    public Map<String, Object> updateChuyenXe(String id, Inputs.CapNhatChuyen input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = found(em, ChuyenXe.class, id);
                if (!TripSaleRules.draft(trip)) {
                    throw new DomainException(409, "Chỉ sửa chuyến chưa mở bán");
                }
                if (VehicleRules.hasTicketsOrHolds(em, trip)) {
                    throw new DomainException(409, "Chuyến có giữ chỗ/vé; không đổi lịch đã cam kết.");
                }
                trip.ngayKhoiHanh = input.ngayKhoiHanh();
                trip.gioKhoiHanh = input.gioKhoiHanh();
                trip.gioDenDuKien = expectedArrival(trip.tuyenXe, trip.ngayKhoiHanh, trip.gioKhoiHanh);
                if (trip.xe != null) {
                    assignVehicle(em, trip, trip.xe.maXe);
                } else {
                    AssignmentRules.validateTrip(em, trip);
                }

                Map<String, Object> responseData = view(trip);
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    public Map<String, Object> transition(String id, TrangThaiChuyen target) {
        if (target == TrangThaiChuyen.DA_HUY) {
            return new TripService(factory).cancel(id);
        }
        return new TripService(factory).transition(id, target, null);
    }

    // Trả chi tiết chuyến cùng số ghế trống và khả năng tiếp tục nhận đặt vé.
    public Map<String, Object> getChuyenXe(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = found(em, ChuyenXe.class, id);
                LocalDateTime now = LocalDateTime.now(TripSaleRules.ZONE);
                Map<String, Object> result = searchView(em, trip, now);
                boolean saleOpen;
                try {
                    TripSaleRules.ensureSellable(em, trip, now);
                    saleOpen = true;
                } catch (DomainException unavailable) {
                    saleOpen = false;
                }
                long remaining = (long) result.get("soGheTrong");
                result.put("coTheDatVe", saleOpen && remaining > 0);
                if (!saleOpen) {
                    result.put("trangThaiNhanDatVe", "NGUNG_NHAN_DAT");
                } else {
                    if (remaining == 0) {
                        result.put("trangThaiNhanDatVe", "HET_CHO");
                    } else {
                        result.put("trangThaiNhanDatVe", "CON_CHO");
                    }
                }

                Map<String, Object> responseData = result;
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    // Lấy ứng viên từ Repository rồi loại từng chuyến không còn đủ điều kiện nhận đặt vé.
    public List<Map<String, Object>> searchChuyenXe(String from, String to, LocalDate date) {
        SearchPoint origin = searchPoint(from, "Điểm đi");
        SearchPoint destination = searchPoint(to, "Điểm đến");
        if (date == null) {
            throw new DomainException(400, "Ngày đi là bắt buộc");
        }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                String originCity = cityOf(em, origin);
                String destinationCity = cityOf(em, destination);
                boolean citySearch = origin.kind().equals("CITY") || destination.kind().equals("CITY");
                if (!citySearch && origin.kind().equals("ANY") && destination.kind().equals("ANY")) {
                    citySearch = em.find(TinhThanh.class, origin.code()) != null && em.find(TinhThanh.class, destination.code()) != null;
                }
                boolean sameCity = citySearch && originCity.equals(destinationCity);
                boolean samePoint = origin.kind().equals(destination.kind()) && origin.code().equals(destination.code());
                if (sameCity || samePoint) {
                    throw new DomainException(400, "Tỉnh đi và tỉnh đến phải khác nhau.");
                }
                LocalDateTime now = LocalDateTime.now(TripSaleRules.ZONE);
                ChuyenXeRepository repository = new ChuyenXeRepository(em);
                List<ChuyenXe> trips = repository.findForSearch(origin.kind(), origin.code(), destination.kind(), destination.code(), date);
                List<Map<String, Object>> responseData = new ArrayList<>();
                for (ChuyenXe trip : trips) {
                    try {
                        TripSaleRules.ensureSellable(em, trip, now);
                    } catch (DomainException unavailable) {
                        continue;
                    }
                    Map<String, Object> item = searchView(em, trip, now);
                    long remaining = (long) item.get("soGheTrong");
                    item.put("coTheDatVe", remaining > 0);
                    if (remaining > 0) {
                        item.put("trangThaiNhanDatVe", "CON_CHO");
                    } else {
                        item.put("trangThaiNhanDatVe", "HET_CHO");
                    }
                    responseData.add(item);
                }
                transaction.commit();
                return responseData;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }
}
