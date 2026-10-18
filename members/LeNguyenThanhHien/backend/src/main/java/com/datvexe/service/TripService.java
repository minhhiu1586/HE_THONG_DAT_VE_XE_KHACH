package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.TripInputs;
import com.datvexe.repository.ChuyenXeRepository;
import com.datvexe.security.*;

import jakarta.persistence.*;

import java.math.*;
import java.time.*;
import java.util.*;

/** Tạo, mở bán và chuyển trạng thái chuyến trong transaction của từng thao tác. */
public final class TripService {
    private final EntityManagerFactory factory;
    private final Clock clock;

    public TripService(EntityManagerFactory factory) {
        this(factory, Clock.system(TripSaleRules.ZONE));
    }

    public TripService(EntityManagerFactory factory, Clock clock) {
        this.factory = factory;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), TripSaleRules.ZONE);
    }

    private static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new DomainException(
                    400,
                    "Mã phải gồm chữ không dấu, số, gạch ngang hoặc gạch dưới, tối đa 40 ký tự.");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : id(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainException(409, message);
        }
    }

    private static void scope(EntityManager em, NhaXe owner) {
        var context = RequestIdentity.get();
        if (context != null) {
            var caller = AccessControl.authenticated(em, context.user(), Instant.now());
            if (caller.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE) {
                AccessControl.operator(caller, owner);
            }
        }
    }

    /** Khóa nhà xe trước chuyến để các thao tác cùng nhà xe được thực hiện tuần tự. */
    private static ChuyenXe locked(EntityManager em, String code) {
        ChuyenXe found = em.find(ChuyenXe.class, id(code));
        if (found == null) {
            throw new DomainException(404, "Không tìm thấy chuyến.");
        }
        found = (ChuyenXe) org.hibernate.Hibernate.unproxy(found);
        if (found.tuyenXe.nhaXe != null) {
            AccessControl.resource(em, NhaXe.class, found.tuyenXe.nhaXe.maNhaXe);
        }
        scope(em, found.tuyenXe.nhaXe);
        return new ChuyenXeRepository(em).lock(code);
    }

    public Map<String, Object> create(TripInputs.Draft input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                if (input == null) {
                    throw new DomainException(400, "Thiếu thông tin chuyến.");
                }
                String code = id(input.maChuyen());
                if (em.find(ChuyenXe.class, code) != null) {
                    throw new DomainException(409, "Mã chuyến đã tồn tại.");
                }
                ChuyenXe trip = new ChuyenXe();
                trip.maChuyen = code;
                trip.trangThai = TrangThaiChuyen.NHAP;
                apply(em, trip, input, false);
                em.persist(trip);
                em.flush();

                Map<String, Object> responseData = view(em, trip);
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

    private void apply(EntityManager em, ChuyenXe trip, TripInputs.Draft input, boolean updating) {
        if (input == null) {
            throw new DomainException(400, "Thiếu thông tin chuyến.");
        }
        if (updating && input.maChuyen() != null && !trip.maChuyen.equals(input.maChuyen())) {
            throw new DomainException(400, "Không được đổi mã chuyến.");
        }
        TuyenXe route = AccessControl.resource(em, TuyenXe.class, id(input.maTuyen()));
        require(route.nhaXe != null, "Tuyến chưa có nhà xe vận hành.");
        NhaXe owner = AccessControl.resource(em, NhaXe.class, route.nhaXe.maNhaXe);
        scope(em, owner);
        if (updating && trip.tuyenXe.nhaXe != null) {
            require(
                    trip.tuyenXe.nhaXe.maNhaXe.equals(owner.maNhaXe),
                    "Không chuyển chuyến sang nhà xe khác.");
        }
        if (input.ngayKhoiHanh() == null) {
            throw new DomainException(400, "Vui lòng nhập ngày khởi hành.");
        }
        LocalTime departure =
                input.gioKhoiHanh() == null ? route.thoiGianKhoiHanh : input.gioKhoiHanh();
        if (departure == null) {
            throw new DomainException(400, "Vui lòng nhập giờ khởi hành.");
        }
        LocalDateTime start = LocalDateTime.of(input.ngayKhoiHanh(), departure),
                arrival = input.gioDenDuKien();
        if (arrival == null) {
            if (route.thoiGianDuKien == null || route.thoiGianDuKien.signum() <= 0) {
                throw new DomainException(400, "Tuyến chưa có thời lượng hợp lệ.");
            }
            try {
                arrival =
                        start.plusMinutes(
                                route.thoiGianDuKien
                                        .multiply(BigDecimal.valueOf(60))
                                        .setScale(0, RoundingMode.HALF_UP)
                                        .longValueExact());
            } catch (ArithmeticException | DateTimeException error) {
                throw new DomainException(400, "Thời lượng tuyến vượt giới hạn.");
            }
        }
        if (!arrival.isAfter(start)) {
            throw new DomainException(400, "Ngày giờ đến phải sau khởi hành.");
        }
        if (!TripSaleRules.validPrice(route.giaCoBan)) {
            throw new DomainException(
                    400, "Giá vé phải là số nguyên VND không âm, tối đa 12 chữ số.");
        }
        if (input.giaVeDuKien() != null
                && (input.giaVeDuKien().signum() < 0
                        || input.giaVeDuKien().compareTo(route.giaCoBan) != 0)) {
            throw new DomainException(
                    400,
                    "Giá dự kiến phải không âm và khớp giá tuyến; chuyến không lưu giá riêng.");
        }
        Xe vehicle =
                input.maXe() == null || input.maXe().isBlank()
                        ? null
                        : AccessControl.resource(em, Xe.class, id(input.maXe()));
        if (vehicle != null) {
            scope(em, vehicle.nhaXe);
            require(
                    vehicle.nhaXe != null && vehicle.nhaXe.maNhaXe.equals(owner.maNhaXe),
                    "Xe không thuộc nhà xe của tuyến.");
        }
        trip.tuyenXe = route;
        trip.xe = vehicle;
        trip.ngayKhoiHanh = input.ngayKhoiHanh();
        trip.gioKhoiHanh = departure;
        trip.gioDenDuKien = arrival;
        if (updating) {
            if (vehicle != null) {
                AssignmentRules.validateVehicleSchedule(em, trip, vehicle.maXe);
            }
            AssignmentRules.validateTrip(em, trip);
        }
    }

    public Map<String, Object> update(String code, TripInputs.Draft input) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                require(
                        TripSaleRules.draft(trip),
                        "Chỉ sửa chuyến nháp hoặc chuyến cũ chưa mở bán.");
                require(
                        !VehicleRules.hasTicketsOrHolds(em, trip),
                        "Chuyến đã có giữ chỗ/vé; không sửa hành trình hoặc lịch đã cam kết.");
                apply(em, trip, input, true);
                em.flush();

                Map<String, Object> responseData = view(em, trip);
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

    public Map<String, Object> get(String code) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                Map<String, Object> responseData = view(em, locked(em, code));
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

    public Map<String, Object> list(
            String owner,
            String route,
            String vehicle,
            LocalDate from,
            LocalDate to,
            TrangThaiChuyen state,
            int page,
            int size) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                if (page < 1
                        || size < 1
                        || size > 100
                        || ((long) page - 1) * size > Integer.MAX_VALUE) {
                    throw new DomainException(400, "Phân trang không hợp lệ.");
                }
                if (from != null && to != null && from.isAfter(to)) {
                    throw new DomainException(400, "Khoảng ngày không hợp lệ.");
                }
                String scope = RequestIdentity.operatorId(), requested = optional(owner);
                if (scope != null && requested != null && !scope.equals(requested)) {
                    throw AccessControl.denied();
                }
                String routeCodes = null;
                if (route != null && !route.isBlank()) {
                    String[] codes = route.split(",", -1);
                    if (codes.length > 100) {
                        throw new DomainException(400, "Chỉ lọc tối đa 100 mã tuyến.");
                    }
                    Set<String> uniqueCodes = new LinkedHashSet<>();
                    for (String routeCode : codes) {
                        uniqueCodes.add(id(routeCode));
                    }
                    routeCodes = String.join(",", uniqueCodes);
                }
                ChuyenXeRepository.Filters filters =
                        new ChuyenXeRepository.Filters(
                                scope == null ? requested : scope,
                                routeCodes,
                                optional(vehicle),
                                from,
                                to,
                                state);
                ChuyenXeRepository repo = new ChuyenXeRepository(em);
                long total = repo.count(filters);
                List<Map<String, Object>> values = new ArrayList<>();
                for (ChuyenXe trip : repo.search(filters, (page - 1) * size, size)) {
                    values.add(view(em, trip));
                }
                Map<String, Object> responseData = new LinkedHashMap<>();
                responseData.put("items", values);
                responseData.put("page", page);
                responseData.put("size", size);
                responseData.put("totalItems", total);
                responseData.put("totalPages", (total + size - 1) / size);
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

    public TripSaleRules.Result eligibility(String code) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                TripSaleRules.Result responseData =
                        TripSaleRules.inspect(em, locked(em, code), now());
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

    public Map<String, Object> open(String code) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                TripSaleRules.ensureOpenable(em, trip, now());
                require(
                        now().isBefore(TripSaleRules.cutoff(trip)),
                        "Đã đến thời điểm ngừng nhận đặt vé trong khi kiểm tra điều kiện.");
                if (trip.trangThai != TrangThaiChuyen.DANG_MO_BAN) {
                    require(TripSaleRules.draft(trip), "Chuyến không thể chuyển sang mở bán.");
                    trip.trangThai = TrangThaiChuyen.DANG_MO_BAN;
                }
                em.flush();

                Map<String, Object> responseData = view(em, trip);
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

    public Map<String, Object> transition(String code, TrangThaiChuyen target, String reason) {
        Map<String, Object> result;
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                authorizeLifecycle(em, trip, target);
                TrangThaiChuyen before = trip.trangThai;
                if (target == TrangThaiChuyen.DANG_CHAY) {
                    require(
                            before == TrangThaiChuyen.DANG_MO_BAN,
                            "Chỉ bắt đầu chuyến đang mở bán.");
                    require(trip.xe != null, "Chuyến chưa được gán xe.");
                    VehicleRules.ready(em, trip.xe.maXe, trip);
                    AssignmentRules.readyForOperation(em, trip);
                    trip.trangThai = TrangThaiChuyen.DANG_CHAY;
                    history(em, trip, before, target, null);
                } else if (target == TrangThaiChuyen.HOAN_TAT) {
                    require(before == TrangThaiChuyen.DANG_CHAY, "Chỉ hoàn tất chuyến đang chạy.");
                    trip.trangThai = TrangThaiChuyen.HOAN_TAT;
                    history(em, trip, before, target, null);
                } else if (target == TrangThaiChuyen.DA_HUY) {
                    String clean = reason == null ? "" : reason.strip();
                    if (clean.isEmpty()) {
                        throw new DomainException(400, "Vui lòng nhập lý do hủy chuyến.");
                    }
                    if (clean.length() > 1000) {
                        throw new DomainException(400, "Lý do hủy không được vượt quá 1000 ký tự.");
                    }
                    require(
                            before == TrangThaiChuyen.NHAP
                                    || before == TrangThaiChuyen.DANG_MO_BAN
                                    || before == TrangThaiChuyen.CHUA_KHOI_HANH,
                            "Không hủy chuyến đang chạy, đã hoàn tất hoặc đã hủy.");
                    cancelAffectedOrders(em, trip, clean);
                    trip.trangThai = TrangThaiChuyen.DA_HUY;
                    history(em, trip, before, target, clean);
                    em.flush();
                } else throw new DomainException(400, "Trạng thái đích không hợp lệ.");
                em.flush();
                result = view(em, trip);

                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
        if (target == TrangThaiChuyen.DA_HUY) {
            processReadyCancellationRefunds(code);
            processCancellationNotifications(code);
        }
        return result;
    }

    private void processReadyCancellationRefunds(String tripId) {
        try (EntityManager em = factory.createEntityManager()) {
            List<?> ids =
                    em.createNativeQuery(
                                    "select ma_don from trip_cancellation_work where"
                                            + " ma_chuyen=:trip and trang_thai='CHO_XU_LY' order by"
                                            + " ma_don")
                            .setParameter("trip", tripId)
                            .getResultList();
            for (Object value : ids)
                try {
                    new HoanVeService(factory)
                            .processTripCancellation(
                                    value.toString(), tripId, "Nhà xe hủy chuyến", null);
                } catch (RuntimeException failure) {
                    markCancellationReconciliation(value.toString(), tripId, failure);
                }
        }
    }

    private void processCancellationNotifications(String tripId) {
        try (EntityManager em = factory.createEntityManager()) {
            boolean pending =
                    !em.createNativeQuery(
                                    "select ma_don from trip_cancellation_notification where"
                                            + " ma_chuyen=:trip and status='PENDING'")
                            .setParameter("trip", tripId)
                            .setMaxResults(1)
                            .getResultList()
                            .isEmpty();
            if (pending) {
                var client =
                        new com.datvexe.email.BrevoClient(
                                com.datvexe.email.BrevoClient.Config.fromEnvironment());
                if (client.ready()) {
                    new com.datvexe.email.TripCancellationEmailDispatcher(
                                    factory, client::send, clock)
                            .runOnce();
                }
            }
        } catch (RuntimeException ignored) {
            /* Thông báo đã lưu trong outbox để EmailWorker thử lại. */
        }
    }

    public Map<String, Object> retryCancellationWork(String code) {
        List<String> orders;
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                authorizeLifecycle(em, trip, TrangThaiChuyen.DA_HUY);
                if (trip.trangThai != TrangThaiChuyen.DA_HUY) {
                    throw new DomainException(409, "Chỉ xử lý hoàn cho chuyến đã hủy.");
                }
                em.createNativeQuery(
                                "update trip_cancellation_notification set"
                                    + " status='PENDING',next_attempt_at=:now where ma_chuyen=:trip"
                                    + " and status='FAILED'")
                        .setParameter("now", clock.instant())
                        .setParameter("trip", trip.maChuyen)
                        .executeUpdate();
                orders = new ArrayList<>();
                List<?> orderRows =
                        em.createNativeQuery(
                                        "select ma_don from trip_cancellation_work where"
                                                + " ma_chuyen=:trip and trang_thai in"
                                                + " ('CHO_XU_LY','CAN_DOI_SOAT') order by ma_don")
                                .setParameter("trip", trip.maChuyen)
                                .getResultList();
                for (Object orderRow : orderRows) {
                    orders.add(orderRow.toString());
                }
                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
        for (String order : orders)
            try {
                new HoanVeService(factory)
                        .processTripCancellation(order, code, "Nhà xe hủy chuyến", null);
            } catch (RuntimeException failure) {
                markCancellationReconciliation(order, code, failure);
            }
        processCancellationNotifications(code);
        return lifecycleReport(code);
    }

    private void markCancellationReconciliation(
            String orderId, String tripId, RuntimeException failure) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            em.createNativeQuery(
                            "update trip_cancellation_work set"
                                    + " trang_thai='CAN_DOI_SOAT',last_error=:error,updated_at=:now"
                                    + " where ma_don=:order and ma_chuyen=:trip and"
                                    + " trang_thai<>'HOAN_TAT'")
                    .setParameter("now", clock.instant())
                    .setParameter(
                            "error",
                            failure instanceof AccountException e
                                    ? e.code()
                                    : "CANCELLATION_PROCESSING_FAILED")
                    .setParameter("order", orderId)
                    .setParameter("trip", tripId)
                    .executeUpdate();
            tx.commit();
        }
    }

    /** Tương thích lời gọi nội bộ cũ; API hủy chuyến luôn yêu cầu lý do. */
    public Map<String, Object> cancel(String code) {
        if (RequestIdentity.get() != null) {
            throw new DomainException(400, "Vui lòng nhập lý do hủy chuyến.");
        }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                require(
                        TripSaleRules.scheduled(trip),
                        "Không hủy chuyến đã chạy, hoàn tất hoặc đã hủy.");
                require(
                        !VehicleRules.hasTicketsOrHolds(em, trip),
                        "Chuyến có giữ chỗ/vé; cần quy trình hủy chuyến có lý do.");
                trip.trangThai = TrangThaiChuyen.DA_HUY;

                Map<String, Object> responseData = view(em, trip);
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

    public Map<String, Object> cancel(String code, String reason) {
        return transition(code, TrangThaiChuyen.DA_HUY, reason);
    }

    private void authorizeLifecycle(EntityManager em, ChuyenXe trip, TrangThaiChuyen target) {
        var request = RequestIdentity.get();
        if (request == null || request.user() == null) {
            return;
        } // Lời gọi nội bộ hoặc kiểm thử.
        NguoiDung user = AccessControl.authenticated(em, request.user(), Instant.now());
        if (user.loaiTaiKhoan == LoaiTaiKhoan.ADMIN) {
            return;
        }
        if (user.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE) {
            AccessControl.operator(user, trip.tuyenXe.nhaXe);
            return;
        }
        if (user.loaiTaiKhoan == LoaiTaiKhoan.NHAN_VIEN
                && target != TrangThaiChuyen.DA_HUY
                && user.nhanVien != null) {
            long assigned =
                    em.createQuery(
                                    "select count(p) from PhanCongChuyenXe p where p.chuyenXe=:trip"
                                            + " and p.nhanVien=:employee and p.vaiTro in :roles",
                                    Long.class)
                            .setParameter("trip", trip)
                            .setParameter("employee", user.nhanVien)
                            .setParameter(
                                    "roles",
                                    Set.of(
                                            VaiTroChuyen.TAI_XE_CHINH,
                                            VaiTroChuyen.TAI_XE_PHU,
                                            VaiTroChuyen.TAI_XE))
                            .getSingleResult();
            if (assigned == 0) {
                throw AccessControl.denied();
            }
            if (user.nhanVien.nhaXe == null
                    || trip.tuyenXe.nhaXe == null
                    || !user.nhanVien.nhaXe.maNhaXe.equals(trip.tuyenXe.nhaXe.maNhaXe)) {
                throw AccessControl.denied();
            }
            if (user.nhanVien.nhaXe.trangThai != TrangThaiNhaXe.HOAT_DONG) {
                throw AccessControl.denied();
            }
            if (user.nhanVien.trangThai != TrangThaiNhanVien.DANG_LAM_VIEC) {
                throw AccessControl.denied();
            }
            return;
        }
        throw AccessControl.denied();
    }

    private void history(
            EntityManager em,
            ChuyenXe trip,
            TrangThaiChuyen before,
            TrangThaiChuyen after,
            String reason) {
        var request = RequestIdentity.get();
        var actor = request == null ? null : request.user();
        if (actor == null) {
            return;
        }
        em.createNativeQuery(
                        "insert into"
                            + " trip_lifecycle_history(id,ma_chuyen,trang_thai_cu,trang_thai_moi,ma_nguoi_thao_tac,vai_tro,ly_do,created_at)"
                            + " values (:id,:trip,:before,:after,:actor,:role,:reason,:created)")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("trip", trip.maChuyen)
                .setParameter("before", before.name())
                .setParameter("after", after.name())
                .setParameter("actor", actor.maNguoiDung())
                .setParameter("role", actor.loaiTaiKhoan().name())
                .setParameter("reason", reason)
                .setParameter("created", clock.instant())
                .executeUpdate();
    }

    private void cancelAffectedOrders(EntityManager em, ChuyenXe trip, String reason) {
        Set<String> orderIds = new TreeSet<>();
        orderIds.addAll(
                em.createQuery(
                                "select distinct h.donDatVe.maDon from GiuCho h where"
                                        + " h.chuyenXe=:trip and h.donDatVe is not null",
                                String.class)
                        .setParameter("trip", trip)
                        .getResultList());
        orderIds.addAll(
                em.createQuery(
                                "select distinct v.donDatVe.maDon from VeXe v where"
                                        + " v.chuyenXe=:trip",
                                String.class)
                        .setParameter("trip", trip)
                        .getResultList());
        em.createQuery(
                        "update GiuCho h set h.trangThai=:cancelled where h.chuyenXe=:trip and"
                                + " h.donDatVe is null and h.trangThai=:active")
                .setParameter("cancelled", TrangThaiGiuCho.DA_HUY)
                .setParameter("trip", trip)
                .setParameter("active", TrangThaiGiuCho.DANG_GIU)
                .executeUpdate();
        for (String orderId : orderIds) {
            DonDatVe order =
                    (DonDatVe)
                            org.hibernate.Hibernate.unproxy(
                                    em.find(
                                            DonDatVe.class,
                                            orderId,
                                            LockModeType.PESSIMISTIC_WRITE));
            List<GiuCho> holds =
                    em.createQuery(
                                    "select h from GiuCho h where h.donDatVe=:order and"
                                            + " h.chuyenXe=:trip",
                                    GiuCho.class)
                            .setParameter("order", order)
                            .setParameter("trip", trip)
                            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .getResultList();
            boolean hadActiveHold = false;
            for (GiuCho hold : holds) {
                if (hold.trangThai == TrangThaiGiuCho.DANG_GIU) {
                    hadActiveHold = true;
                    hold.trangThai = TrangThaiGiuCho.DA_HUY;
                }
            }
            List<VeXe> tickets =
                    em.createQuery(
                                    "select v from VeXe v where v.donDatVe=:order and"
                                            + " v.chuyenXe=:trip",
                                    VeXe.class)
                            .setParameter("order", order)
                            .setParameter("trip", trip)
                            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .getResultList();
            boolean hasLiveTicket = false;
            for (VeXe ticket : tickets) {
                if (ticket.trangThai == TrangThaiVe.DA_XUAT_VE
                        || ticket.trangThai == TrangThaiVe.DA_LEN_XE) {
                    hasLiveTicket = true;
                    break;
                }
            }
            boolean refundableWithoutTicket = tickets.isEmpty() && !holds.isEmpty();
            List<ThanhToan> payments =
                    em.createQuery(
                                    "select p from ThanhToan p where p.donDatVe=:order",
                                    ThanhToan.class)
                            .setParameter("order", order)
                            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .getResultList();
            boolean captured = false;
            boolean uncertain = false;
            for (ThanhToan payment : payments) {
                if (payment.trangThai == TrangThaiThanhToan.THANH_CONG
                        || payment.trangThai == TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN
                        || payment.trangThai == TrangThaiThanhToan.DA_HOAN_TIEN) {
                    captured = true;
                }
                if (payment.trangThai == TrangThaiThanhToan.CHO_XU_LY) {
                    uncertain = true;
                }
            }
            boolean pendingOrder =
                    Set.of(
                                    TrangThaiDon.CHO_XU_LY,
                                    TrangThaiDon.DA_XAC_NHAN,
                                    TrangThaiDon.CHO_THANH_TOAN)
                            .contains(order.trangThai);
            if (pendingOrder) {
                order.trangThai = TrangThaiDon.DA_HUY;
            }
            if (hadActiveHold && !captured && !uncertain) {
                order.maKhuyenMaiDaApDung = null;
            }
            boolean isMixedOrder =
                    em.createQuery(
                                                    "select count(v) from VeXe v where"
                                                            + " v.donDatVe=:order and"
                                                            + " v.chuyenXe<>:trip",
                                                    Long.class)
                                            .setParameter("order", order)
                                            .setParameter("trip", trip)
                                            .getSingleResult()
                                    > 0
                            || em.createQuery(
                                                    "select count(h) from GiuCho h where"
                                                            + " h.donDatVe=:order and"
                                                            + " h.chuyenXe<>:trip",
                                                    Long.class)
                                            .setParameter("order", order)
                                            .setParameter("trip", trip)
                                            .getSingleResult()
                                    > 0;
            // Hoàn tiền theo đơn: đơn chứa chuyến khác cần đối soát, không tự hoàn toàn bộ.
            String work;
            if (uncertain || isMixedOrder) {
                work = "CAN_DOI_SOAT";
            } else if (captured && (hasLiveTicket || refundableWithoutTicket)) {
                work = "CHO_XU_LY";
            } else {
                work = "KHONG_CAN_HOAN";
            }
            em.createNativeQuery(
                            "insert into"
                                + " trip_cancellation_work(ma_don,ma_chuyen,trang_thai,ly_do,created_at,updated_at)"
                                + " values (:order,:trip,:state,:reason,:created,:created) on"
                                + " conflict (ma_don,ma_chuyen) do nothing")
                    .setParameter("order", orderId)
                    .setParameter("trip", trip.maChuyen)
                    .setParameter("state", work)
                    .setParameter("reason", reason)
                    .setParameter("created", clock.instant())
                    .executeUpdate();
            if ((hasLiveTicket || hadActiveHold)
                    && order.khachDatEmail != null
                    && !order.khachDatEmail.isBlank()) {
                em.createNativeQuery(
                                "insert into"
                                    + " trip_cancellation_notification(ma_don,ma_chuyen,recipient,recipient_name)"
                                    + " values (:order,:trip,:email,:name) on conflict"
                                    + " (ma_don,ma_chuyen) do nothing")
                        .setParameter("order", orderId)
                        .setParameter("trip", trip.maChuyen)
                        .setParameter("email", order.khachDatEmail)
                        .setParameter("name", Objects.toString(order.khachDatTen, "Khách hàng"))
                        .executeUpdate();
            }
        }
    }

    public Map<String, Object> lifecycleReport(String code) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                List<Object[]> history =
                        em.createNativeQuery(
                                        "select"
                                            + " trang_thai_cu,trang_thai_moi,ma_nguoi_thao_tac,vai_tro,ly_do,created_at"
                                            + " from trip_lifecycle_history where ma_chuyen=:trip"
                                            + " order by created_at desc")
                                .setParameter("trip", trip.maChuyen)
                                .getResultList();
                List<Object[]> works =
                        em.createNativeQuery(
                                        "select w.ma_don,w.trang_thai,w.ly_do,w.created_at,(select"
                                            + " count(*) from ve_xe v where v.ma_don=w.ma_don and"
                                            + " v.ma_chuyen=w.ma_chuyen and v.trang_thai in"
                                            + " ('DA_XUAT_VE','DA_LEN_XE')),w.last_error from"
                                            + " trip_cancellation_work w where w.ma_chuyen=:trip"
                                            + " order by w.created_at,w.ma_don")
                                .setParameter("trip", trip.maChuyen)
                                .getResultList();
                List<Object[]> notices =
                        em.createNativeQuery(
                                        "select status,count(*) from trip_cancellation_notification"
                                                + " where ma_chuyen=:trip group by status")
                                .setParameter("trip", trip.maChuyen)
                                .getResultList();
                Object[] refundTotals =
                        (Object[])
                                em.createNativeQuery(
                                                "select count(*) filter(where"
                                                    + " trang_thai='THANH_CONG'),count(*)"
                                                    + " filter(where trang_thai in"
                                                    + " ('CHO_XU_LY','CAN_DOI_SOAT','THAT_BAI')),coalesce(sum(so_tien_hoan),0),coalesce(sum(so_tien_hoan)"
                                                    + " filter(where trang_thai='THANH_CONG'),0)"
                                                    + " from refund_request where ma_don in (select"
                                                    + " ma_don from trip_cancellation_work where"
                                                    + " ma_chuyen=:trip) and ly_do like"
                                                    + " 'NHA_XE_HUY_CHUYEN:%'")
                                        .setParameter("trip", trip.maChuyen)
                                        .getSingleResult();
                Object[] ticketTotals =
                        (Object[])
                                em.createNativeQuery(
                                                "select count(*),count(*) filter(where trang_thai"
                                                    + " in ('DA_XUAT_VE','DA_LEN_XE')),count(*)"
                                                    + " filter(where"
                                                    + " trang_thai='DA_HOAN'),coalesce(sum(gia_ve)"
                                                    + " filter(where trang_thai in"
                                                    + " ('DA_XUAT_VE','DA_LEN_XE','DA_HOAN')),0)"
                                                    + " from ve_xe where ma_chuyen=:trip")
                                        .setParameter("trip", trip.maChuyen)
                                        .getSingleResult();
                List<Map<String, Object>> affected = new ArrayList<>();
                for (Object[] row : works) {
                    List<Object[]> refunds =
                            em.createNativeQuery(
                                            "select trang_thai,so_tien_hoan,ma_hoan_tien,ly_do_loi"
                                                    + " from refund_request where ma_don=:order and"
                                                    + " ly_do like 'NHA_XE_HUY_CHUYEN:%' order by"
                                                    + " created_at")
                                    .setParameter("order", row[0])
                                    .getResultList();
                    List<Object[]> affectedTickets =
                            em.createNativeQuery(
                                            "select ma_ve,trang_thai,gia_ve from ve_xe where"
                                                    + " ma_don=:order and ma_chuyen=:trip order by"
                                                    + " ma_ve")
                                    .setParameter("order", row[0])
                                    .setParameter("trip", trip.maChuyen)
                                    .getResultList();
                    List<Map<String, Object>> ticketItems = new ArrayList<>();
                    for (Object[] ticket : affectedTickets) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("maVe", ticket[0]);
                        item.put("trangThai", ticket[1]);
                        item.put("giaThucTra", ticket[2].toString());
                        ticketItems.add(item);
                    }
                    List<Map<String, Object>> refundItems = new ArrayList<>();
                    for (Object[] refund : refunds) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("maHoanTien", refund[2]);
                        item.put("trangThai", refund[0]);
                        item.put("soTienHoan", refund[1].toString());
                        item.put("lyDoLoi", Objects.toString(refund[3], ""));
                        refundItems.add(item);
                    }
                    Map<String, Object> orderItem = new LinkedHashMap<>();
                    orderItem.put("veBiAnhHuong", ticketItems);
                    orderItem.put("maDon", row[0]);
                    orderItem.put("trangThaiXuLy", row[1]);
                    orderItem.put("lyDo", row[2]);
                    orderItem.put("thoiDiem", row[3].toString());
                    orderItem.put("soVeConHieuLuc", row[4]);
                    orderItem.put("lyDoXuLy", Objects.toString(row[5], ""));
                    orderItem.put("yeuCauHoan", refundItems);
                    affected.add(orderItem);
                }
                Map<String, Object> refundSummary = new LinkedHashMap<>();
                refundSummary.put("tongSoVeBiAnhHuong", ticketTotals[0]);
                refundSummary.put("soVeCanHoan", ticketTotals[1]);
                refundSummary.put("soVeDaHoan", ticketTotals[2]);
                refundSummary.put("soYeuCauThanhCong", refundTotals[0]);
                refundSummary.put("soYeuCauCanTheoDoi", refundTotals[1]);
                refundSummary.put("tongTienDuKien", ticketTotals[3].toString());
                refundSummary.put("tongTienDaHoan", refundTotals[3].toString());
                List<Map<String, Object>> historyItems = new ArrayList<>();
                for (Object[] row : history) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("trangThaiCu", row[0]);
                    item.put("trangThaiMoi", row[1]);
                    item.put("maNguoiThaoTac", row[2]);
                    item.put("vaiTro", row[3]);
                    item.put("lyDo", Objects.toString(row[4], ""));
                    item.put("thoiDiem", row[5].toString());
                    historyItems.add(item);
                }
                List<Map<String, Object>> notificationItems = new ArrayList<>();
                for (Object[] row : notices) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("trangThai", row[0]);
                    item.put("soLuong", row[1]);
                    notificationItems.add(item);
                }
                Map<String, Object> responseData = new LinkedHashMap<>();
                responseData.put("maChuyen", trip.maChuyen);
                responseData.put("trangThai", trip.trangThai);
                responseData.put("tongQuanHoan", refundSummary);
                responseData.put("lyLich", historyItems);
                responseData.put("khachBiAnhHuong", affected);
                responseData.put("thongBao", notificationItems);
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

    public void delete(String code) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                ChuyenXe trip = locked(em, code);
                require(TripSaleRules.draft(trip), "Chỉ xóa cứng chuyến nháp chưa được sử dụng.");
                require(
                        !VehicleRules.hasTicketsOrHolds(em, trip) && trip.danhSachDanhGia.isEmpty(),
                        "Chuyến có dữ liệu liên quan; không xóa cứng.");
                em.remove(trip);
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

    private Map<String, Object> view(EntityManager em, ChuyenXe trip) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("maChuyen", trip.maChuyen);
        value.put("maTuyen", trip.tuyenXe.maTuyen);
        value.put("tenTuyen", trip.tuyenXe.tenTuyen);
        value.put("maNhaXe", trip.tuyenXe.nhaXe == null ? null : trip.tuyenXe.nhaXe.maNhaXe);
        value.put("tenNhaXe", trip.tuyenXe.nhaXe == null ? null : trip.tuyenXe.nhaXe.tenNhaXe);
        value.put("maXe", trip.xe == null ? null : trip.xe.maXe);
        value.put("bienSo", trip.xe == null ? null : trip.xe.bienSo);
        value.put("ngayKhoiHanh", trip.ngayKhoiHanh);
        value.put("gioKhoiHanh", trip.gioKhoiHanh);
        value.put("gioDenDuKien", trip.gioDenDuKien);
        value.put(
                "giaVeDuKien",
                trip.tuyenXe.giaCoBan == null
                        ? null
                        : trip.tuyenXe.giaCoBan.stripTrailingZeros().toPlainString());
        value.put("trangThai", trip.trangThai);
        value.put(
                "ngungNhanDatVeLuc",
                TripSaleRules.cutoff(trip).atZone(TripSaleRules.ZONE).toOffsetDateTime());
        value.put("phutDungNhanDatVe", TripSaleRules.cutoffMinutes());
        value.put("serverTime", now().atZone(TripSaleRules.ZONE).toOffsetDateTime());
        boolean expired = !now().isBefore(TripSaleRules.cutoff(trip));
        value.put("daHetHanDatVe", expired);
        value.put(
                "coTheDatVe",
                trip.trangThai == TrangThaiChuyen.DANG_MO_BAN
                        && !expired
                        && TripSaleRules.inspect(em, trip, now()).duDieuKien());
        value.put(
                "coTheSua", TripSaleRules.draft(trip) && !VehicleRules.hasTicketsOrHolds(em, trip));
        value.put(
                "coTheXoa",
                TripSaleRules.draft(trip)
                        && !VehicleRules.hasTicketsOrHolds(em, trip)
                        && trip.danhSachDanhGia.isEmpty());
        value.put(
                "coTheHuy",
                Set.of(
                                TrangThaiChuyen.NHAP,
                                TrangThaiChuyen.DANG_MO_BAN,
                                TrangThaiChuyen.CHUA_KHOI_HANH)
                        .contains(trip.trangThai));
        List<PhanCongChuyenXe> crew =
                em.createQuery(
                                "select p from PhanCongChuyenXe p where p.chuyenXe=:trip order by"
                                        + " p.maPhanCong",
                                PhanCongChuyenXe.class)
                        .setParameter("trip", trip)
                        .getResultList();
        List<Map<String, Object>> crewItems = new ArrayList<>();
        for (PhanCongChuyenXe assignment : crew) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("maPhanCong", assignment.maPhanCong);
            item.put("maNhanVien", assignment.nhanVien.maNhanVien);
            item.put("hoTen", assignment.nhanVien.hoTen);
            item.put("vaiTro", assignment.vaiTro);
            item.put("trangThai", assignment.nhanVien.trangThai);
            item.put("ngayHetHanBangLai", assignment.nhanVien.ngayHetHanBangLai);
            item.put("ghiChu", assignment.ghiChu);
            crewItems.add(item);
        }
        value.put("nhanVienPhanCong", crewItems);
        return value;
    }
}
