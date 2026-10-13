package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto;
import com.datvexe.dto.BookingDto.*;
import com.datvexe.dto.HoldSeatRequest;
import com.datvexe.dto.HoldSeatResponse;
import com.datvexe.repository.*;
import com.datvexe.security.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Booking rules. Every change and its idempotency result share one JPA transaction. */
public final class BookingService {
    private static final ZoneId ZONE = TripSaleRules.ZONE;
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EntityManagerFactory factory;
    private final Clock clock;

    public BookingService() { this(Jpa.factory()); }
    public BookingService(EntityManagerFactory factory) { this(factory, Clock.system(ZONE)); }
    public BookingService(EntityManagerFactory factory, Clock clock) {
        this.factory = Objects.requireNonNull(factory);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result holdSeats(HoldSeatRequest request, String key) {
        return execute(key, em -> {
            if (request == null) throw invalid("body", "Thiếu thông tin giữ ghế.");
            String tripId = code(request.getMaChuyen(), "maChuyen");
            List<String> seatIds = request.getDanhSachMaGhe();
            if (seatIds == null || seatIds.isEmpty()) throw invalid("danhSachMaGhe", "Phải chọn ít nhất một ghế.");
            Set<String> unique = new HashSet<>();
            for (String seatId : seatIds) {
                if (!unique.add(code(seatId, "danhSachMaGhe"))) throw invalid("danhSachMaGhe", "Danh sách ghế không được trùng.");
            }
            ChuyenXe trip = lockTrip(em, tripId);
            expireTrip(em, trip, now());
            TripSaleRules.ensureSellable(em, trip, now());
            LocalDateTime checkedAt = now();
            List<Ghe> seats = seatIds.stream().sorted().map(id -> availableSeat(em, trip, id, checkedAt)).toList();
            NguoiDung buyer = em.find(NguoiDung.class, identity().maNguoiDung());
            DonDatVe order = new DonDatVe();
            order.maDon = id();
            order.nguoiDat = buyer;
            order.khachDatTen = text(buyer.hoTen, "khachDatTen", 120);
            order.khachDatSoDienThoai = text(buyer.soDienThoai, "khachDatSoDienThoai", 20);
            // The window starts after all locks and sale checks have completed.
            LocalDateTime startsAt = now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (!startsAt.isBefore(TripSaleRules.cutoff(trip))) throw conflict("Đã đến thời điểm ngừng nhận đặt vé.");
            order.ngayDat = startsAt;
            order.trangThai = TrangThaiDon.CHO_XU_LY;
            order.tongTien = baseFare(trip).multiply(BigDecimal.valueOf(seats.size()));
            validateTotal(order.tongTien);
            new DonDatVeRepository(em).save(order);
            for (Ghe seat : seats) em.persist(newHold(order, trip, seat, startsAt, startsAt.plusMinutes(15)));
            em.flush();
            OrderContext context = context(em, order, trip);
            return new Result(201, new HoldSeatResponse(order.maDon, trip.maChuyen, order.trangThai,
                    offset(startsAt), offset(startsAt), offset(deadline(context)),
                    active(context).stream().map(h -> h.ghe.maGhe).toList(), holdResponses(context)));
        });
    }

    public Result getOrder(String maDon, List<PassengerRequest> passengers) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            checkExpiry(context, now());
            return result(em, context, List.of());
        });
    }

    public Result updateContact(String maDon, JsonNode body, String key, List<PassengerRequest> passengers) {
        return execute(key, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(context);
            if (body == null || !body.isObject() || body.isEmpty()) throw invalid("body", "Cần ít nhất một trường liên hệ.");
            Set<String> fields = Set.of("khachDatTen", "khachDatSoDienThoai", "ghiChu");
            body.fieldNames().forEachRemaining(field -> {
                if (!fields.contains(field)) throw invalid(field, "Trường không được hỗ trợ.");
                JsonNode value = body.get(field);
                if (!value.isTextual() && !(field.equals("ghiChu") && value.isNull()))
                    throw invalid(field, "Trường phải là chuỗi; chỉ ghiChu được phép null.");
            });
            if (body.has("khachDatTen")) context.order.khachDatTen = text(body.get("khachDatTen").asText(), "khachDatTen", 120);
            if (body.has("khachDatSoDienThoai")) context.order.khachDatSoDienThoai = text(body.get("khachDatSoDienThoai").asText(), "khachDatSoDienThoai", 20);
            if (body.has("ghiChu")) context.order.ghiChu = optional(body.get("ghiChu").isNull() ? null : body.get("ghiChu").asText(), "ghiChu", 500);
            return updated(em, context);
        });
    }

    public Result savePassengers(String maDon, PassengersRequest request) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon);
            editable(context);
            if (request == null || request.hanhKhach() == null || request.hanhKhach().isEmpty())
                throw invalid("hanhKhach", "Cần thông tin của toàn bộ ghế đang giữ.");
            Map<String, PassengerRequest> passengers = new LinkedHashMap<>();
            for (PassengerRequest passenger : request.hanhKhach()) {
                if (passenger == null) throw invalid("hanhKhach", "Thông tin hành khách không được null.");
                String seat = code(passenger.maGhe(), "maGhe");
                PassengerRequest valid = new PassengerRequest(seat, text(passenger.hoTen(), "hoTen", 120),
                        text(passenger.soDienThoai(), "soDienThoai", 20), optional(passenger.cccd(), "cccd", 20));
                if (passengers.put(seat, valid) != null) throw invalid("maGhe", "Không được lặp ghế trong danh sách hành khách.");
            }
            Set<String> heldSeats = new HashSet<>();
            active(context).forEach(hold -> heldSeats.add(hold.ghe.maGhe));
            if (!heldSeats.equals(passengers.keySet())) throw invalid("hanhKhach", "Danh sách phải khớp toàn bộ ghế đang giữ của đơn.");
            // Temporary DTOs only. The controller stores them in HttpSession after commit.
            context.passengers.clear();
            context.passengers.addAll(passengers.values());
            return updated(em, context);
        });
    }

    public Result removeSeat(String maDon, String maGhe, List<PassengerRequest> passengers) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(context);
            GiuCho hold = heldSeat(context, code(maGhe, "maGhe"));
            hold.trangThai = TrangThaiGiuCho.DA_HUY;
            context.passengers.removeIf(passenger -> passenger.maGhe().equals(maGhe));
            if (active(context).isEmpty()) cancel(context);
            return updated(em, context);
        });
    }

    public Result changeSeat(String maDon, ChangeSeatRequest request, List<PassengerRequest> passengers) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(context);
            if (request == null) throw invalid("body", "Thiếu thông tin đổi ghế.");
            String oldId = code(request.maGheCu(), "maGheCu"), newId = code(request.maGheMoi(), "maGheMoi");
            GiuCho oldHold = heldSeat(context, oldId);
            if (oldId.equals(newId)) throw invalid("maGheMoi", "Ghế mới phải khác ghế cũ.");
            expireTrip(em, context.trip, now());
            TripSaleRules.ensureSellable(em, context.trip, now());
            Ghe newSeat = availableSeat(em, context.trip, newId, now());
            // Recheck the original deadline after potentially waiting on sale-resource locks.
            checkExpiry(context, now());
            oldHold.trangThai = TrangThaiGiuCho.DA_HUY;
            GiuCho replacement = newHold(context.order, context.trip, newSeat, start(context), deadline(context));
            em.persist(replacement);
            context.holds.add(replacement);
            context.passengers.replaceAll(passenger -> passenger.maGhe().equals(oldId)
                    ? new PassengerRequest(newId, passenger.hoTen(), passenger.soDienThoai(), passenger.cccd())
                    : passenger);
            return updated(em, context);
        });
    }

    public Result applyPromotion(String maDon, PromotionRequest request, List<PassengerRequest> passengers) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(context);
            if (request == null) throw invalid("body", "Thiếu mã khuyến mãi.");
            context.order.maKhuyenMaiDaApDung = code(request.maKhuyenMai(), "maKhuyenMai");
            PricePreview price = preview(em, context, now());
            if (price.lyDoKhongApDung() != null)
                throw new AccountException(409, "PROMOTION_NOT_APPLICABLE", price.lyDoKhongApDung());
            return updated(em, context);
        });
    }

    public Result removePromotion(String maDon, List<PassengerRequest> passengers) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(context);
            context.order.maKhuyenMaiDaApDung = null;
            return updated(em, context);
        });
    }

    public Result cancelOrder(String maDon, String key) {
        return execute(key, em -> {
            OrderContext context = lockOrder(em, maDon);
            if (context.order.trangThai == TrangThaiDon.DA_HUY || context.order.trangThai == TrangThaiDon.HET_HAN)
                return result(em, context, List.of());
            editable(context);
            cancel(context);
            return updated(em, context);
        });
    }

    /** Sweep one trip per transaction, using the same trip -> order lock order as requests. */
    public void releaseExpiredHolds() {
        List<String> tripIds;
        try (EntityManager em = factory.createEntityManager()) {
            tripIds = new DonDatVeRepository(em).expiredTripIds(now()).stream().sorted().toList();
        }
        for (String tripId : tripIds) {
            try (EntityManager em = factory.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                try {
                    expireTrip(em, lockTrip(em, tripId), now());
                    tx.commit();
                } catch (RuntimeException error) {
                    if (tx.isActive()) tx.rollback();
                    throw error;
                }
            }
        }
    }

    private Result execute(String key, Function<EntityManager, Result> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                CurrentUser current = identity();
                var request = RequestIdentity.get();
                boolean needsKey = request.method().equals("PATCH")
                        || (request.method().equals("POST") && (request.path().equals("/api/booking/hold") || request.path().endsWith("/huy")));
                IdempotencyRepository repository = new IdempotencyRepository(em);
                IdempotencyRepository.Scope scope = null;
                String hash = null;
                if (needsKey) {
                    if (key == null || key.isBlank() || key.length() > 128 || !key.matches("[!-~]+"))
                        throw invalid("Idempotency-Key", "Cần Idempotency-Key gồm 1–128 ký tự ASCII không có khoảng trắng.");
                    scope = new IdempotencyRepository.Scope(current.maNguoiDung(), request.method(), request.path(), key);
                    // Acquire before the account row lock, so a concurrent same-key request gets 409 promptly.
                    repository.lock(scope);
                    hash = IdempotencyRepository.hash(canonical(request.body() == null || request.body().isMissingNode()
                            ? JSON.createObjectNode() : request.body()).toString());
                }
                AccessPolicy.guard(em);
                if (identity().loaiTaiKhoan() != LoaiTaiKhoan.KHACH_HANG) throw AccessControl.denied();
                if (scope != null) {
                    var saved = repository.find(scope, clock.instant());
                    if (saved != null) {
                        if (!saved.hash().equals(hash)) throw new AccountException(409, "IDEMPOTENCY_KEY_REUSED", "Khóa đã được dùng cho nội dung khác.");
                        Result replay = new Result(saved.status(), parse(saved.body()));
                        tx.commit();
                        return replay;
                    }
                }
                Result result = action.apply(em);
                em.flush();
                if (scope != null) repository.save(scope, hash, result.status(), storedResponse(result.body()), clock.instant());
                tx.commit();
                return result;
            } catch (ExpiredOrder expired) {
                // Expiry is a valid transition, even though the attempted operation is rejected.
                tx.commit();
                throw new AccountException(409, "HOLD_EXPIRED", "Đơn đã hết thời gian giữ ghế.", Map.of("maDon", expired.maDon));
            } catch (RuntimeException error) {
                if (tx.isActive()) tx.rollback();
                throw error;
            }
        }
    }

    private OrderContext lockOrder(EntityManager em, String orderId) {
        return lockOrder(em, orderId, List.of());
    }

    private OrderContext lockOrder(EntityManager em, String orderId, List<PassengerRequest> passengers) {
        String validId = code(orderId, "maDon");
        DonDatVe initial = new DonDatVeRepository(em).find(validId);
        if (initial == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy đơn đặt vé.");
        own(initial);
        Set<String> trips = new HashSet<>(em.createQuery("select distinct h.chuyenXe.maChuyen from GiuCho h where h.donDatVe.maDon=:id", String.class)
                .setParameter("id", validId).getResultList());
        trips.addAll(em.createQuery("select distinct v.chuyenXe.maChuyen from VeXe v where v.donDatVe.maDon=:id", String.class)
                .setParameter("id", validId).getResultList());
        if (trips.size() != 1) throw conflict("Đơn phải có giữ chỗ hoặc vé thuộc đúng một chuyến.");
        ChuyenXe trip = lockTrip(em, trips.iterator().next());
        DonDatVe order = AccessControl.resource(em, DonDatVe.class, validId);
        own(order);
        OrderContext context = context(em, order, trip);
        context.passengers.addAll(passengers);
        return context;
    }

    private ChuyenXe lockTrip(EntityManager em, String tripId) {
        // Match T12's operator -> trip order before taking any booking-order lock.
        ChuyenXe initial = em.find(ChuyenXe.class, tripId);
        if (initial == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy chuyến.");
        initial = (ChuyenXe) org.hibernate.Hibernate.unproxy(initial);
        if (initial.tuyenXe.nhaXe != null)
            AccessControl.resource(em, NhaXe.class, initial.tuyenXe.nhaXe.maNhaXe);
        return AccessControl.resource(em, ChuyenXe.class, tripId);
    }

    private OrderContext context(EntityManager em, DonDatVe order, ChuyenXe trip) {
        List<GiuCho> holds = new ArrayList<>(new GiuChoRepository(em).forOrder(order.maDon));
        List<VeXe> tickets = new ArrayList<>(new VeXeRepository(em).forOrder(order.maDon));
        holds.forEach(em::refresh);
        tickets.forEach(em::refresh);
        if (holds.stream().anyMatch(h -> !h.chuyenXe.maChuyen.equals(trip.maChuyen))
                || tickets.stream().anyMatch(v -> !v.chuyenXe.maChuyen.equals(trip.maChuyen)))
            throw conflict("Dữ liệu đơn không cùng chuyến.");
        return new OrderContext(order, trip, holds, tickets, new ArrayList<>());
    }

    private void expireTrip(EntityManager em, ChuyenXe trip, LocalDateTime now) {
        List<String> orders = new DonDatVeRepository(em).orderIdsForTrip(trip.maChuyen);
        for (String orderId : orders) {
            DonDatVe order = AccessControl.resource(em, DonDatVe.class, orderId);
            OrderContext context = context(em, order, trip);
            if (pending(order) && !now.isBefore(deadline(context))) expire(context);
        }
        for (GiuCho orphan : new GiuChoRepository(em).forTrip(trip.maChuyen)) {
            if (orphan.donDatVe == null && orphan.trangThai == TrangThaiGiuCho.DANG_GIU && !now.isBefore(orphan.thoiDiemHetHan))
                orphan.trangThai = TrangThaiGiuCho.HET_HAN;
        }
        // Make expired holds unavailable to subsequent operations in this transaction.
        em.flush();
    }

    private void editable(OrderContext context) {
        checkExpiry(context, now());
        if (context.order.trangThai != TrangThaiDon.CHO_XU_LY)
            throw conflict("Chỉ được sửa đơn nháp chưa chốt giá, chưa thanh toán.");
        if (active(context).isEmpty()) throw conflict("Đơn không còn ghế đang giữ.");
    }

    private void checkExpiry(OrderContext context, LocalDateTime now) {
        if (context.order.trangThai == TrangThaiDon.HET_HAN) throw new ExpiredOrder(context.order.maDon);
        if (pending(context.order) && !now.isBefore(deadline(context))) {
            expire(context);
            throw new ExpiredOrder(context.order.maDon);
        }
    }

    private static boolean pending(DonDatVe order) {
        return Set.of(TrangThaiDon.CHO_XU_LY, TrangThaiDon.DA_XAC_NHAN, TrangThaiDon.CHO_THANH_TOAN).contains(order.trangThai);
    }

    private void expire(OrderContext context) {
        context.order.trangThai = TrangThaiDon.HET_HAN;
        context.holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU).forEach(h -> h.trangThai = TrangThaiGiuCho.HET_HAN);
        context.passengers.clear();
        context.order.maKhuyenMaiDaApDung = null;
    }

    private void cancel(OrderContext context) {
        context.order.trangThai = TrangThaiDon.DA_HUY;
        active(context).forEach(h -> h.trangThai = TrangThaiGiuCho.DA_HUY);
        context.passengers.clear();
        context.order.maKhuyenMaiDaApDung = null;
        context.order.tongTien = BigDecimal.ZERO;
        context.order.tienGiam = BigDecimal.ZERO;
    }

    private Result updated(EntityManager em, OrderContext context) {
        BigDecimal fare = baseFare(context.trip);
        context.order.tongTien = fare.multiply(BigDecimal.valueOf(active(context).size()));
        validateTotal(context.order.tongTien);
        context.order.tienGiam = BigDecimal.ZERO;
        PricePreview price = preview(em, context, now());
        List<String> notices = List.of();
        if (context.order.maKhuyenMaiDaApDung != null && price.lyDoKhongApDung() != null) {
            notices = List.of("Đã bỏ mã khuyến mãi: " + price.lyDoKhongApDung());
            context.order.maKhuyenMaiDaApDung = null;
        }
        return result(em, context, notices);
    }

    private Result result(EntityManager em, OrderContext context, List<String> notices) {
        LocalDateTime now = now();
        List<TicketResponse> tickets = context.tickets.stream()
                .sorted(Comparator.comparing((VeXe v) -> v.ghe.maGhe).thenComparing(v -> v.maVe))
                .map(v -> new TicketResponse(v.maVe, v.ghe.maGhe, v.hoTenKhachHang, v.soDienThoaiKhachHang,
                        v.cccdHanhKhach, amount(v.giaVe), v.trangThai, offset(v.ngayXuatVe))).toList();
        Set<String> heldSeats = new HashSet<>();
        active(context).forEach(h -> heldSeats.add(h.ghe.maGhe));
        List<PassengerRequest> passengers = context.order.trangThai == TrangThaiDon.CHO_XU_LY
                ? context.passengers.stream().filter(passenger -> heldSeats.contains(passenger.maGhe()))
                    .sorted(Comparator.comparing(PassengerRequest::maGhe)).toList()
                : List.of();
        Set<String> passengerSeats = new HashSet<>();
        passengers.forEach(passenger -> passengerSeats.add(passenger.maGhe()));
        boolean ready = context.order.trangThai == TrangThaiDon.CHO_XU_LY && now.isBefore(deadline(context))
                && !heldSeats.isEmpty() && heldSeats.equals(passengerSeats) && passengers.size() == heldSeats.size();
        return new Result(200, new OrderResponse(context.order.maDon, context.trip.maChuyen, context.order.trangThai,
                context.order.khachDatTen, context.order.khachDatSoDienThoai, context.order.ghiChu, offset(now),
                offset(start(context)), offset(deadline(context)), holdResponses(context), passengers, tickets,
                preview(em, context, now), ready, null, notices));
    }

    private PricePreview preview(EntityManager em, OrderContext context, LocalDateTime now) {
        boolean draft = context.order.trangThai == TrangThaiDon.CHO_XU_LY;
        BigDecimal total = draft ? baseFare(context.trip).multiply(BigDecimal.valueOf(active(context).size())) : context.order.tongTien;
        String selected = context.order.maKhuyenMaiDaApDung;
        BigDecimal discount = draft ? BigDecimal.ZERO : context.order.tienGiam;
        String applied = null, reason = null;
        if (draft && selected != null) {
            KhuyenMai promotion = new KhuyenMaiRepository(em, now).find(selected);
            if (promotion == null) reason = "Mã khuyến mãi không tồn tại.";
            else if (!promotion.kiemTraHieuLuc(now)) reason = "Mã chưa có hiệu lực, đã hết hạn hoặc hết lượt.";
            else if (!promotion.kiemTraDieuKien(total, now)) reason = "Tổng tiền chưa đạt điều kiện khuyến mãi.";
            else { discount = promotion.tinhTienGiam(total, now); applied = selected; }
        }
        return new PricePreview(amount(total), amount(discount), amount(total.subtract(discount)), selected, applied, reason);
    }

    private List<BookingDto.HoldResponse> holdResponses(OrderContext context) {
        return context.holds.stream().sorted(Comparator.comparing((GiuCho h) -> h.ghe.maGhe).thenComparing(h -> h.maGiuCho))
                .map(h -> new BookingDto.HoldResponse(h.maGiuCho, h.ghe.maGhe, h.trangThai, offset(h.thoiDiemBatDau), offset(h.thoiDiemHetHan))).toList();
    }

    private Ghe availableSeat(EntityManager em, ChuyenXe trip, String seatId, LocalDateTime now) {
        Ghe seat = em.find(Ghe.class, seatId);
        if (seat == null || trip.xe == null || !seat.xe.maXe.equals(trip.xe.maXe))
            throw invalid("maGhe", "Ghế không tồn tại hoặc không thuộc xe của chuyến.");
        em.refresh(seat);
        if (!VehicleRules.availability(em, trip, seat, now).equals("TRONG"))
            throw new AccountException(409, "SEAT_UNAVAILABLE", "Ghế không còn khả dụng.", Map.of("maGhe", seatId));
        return seat;
    }

    private GiuCho newHold(DonDatVe order, ChuyenXe trip, Ghe seat, LocalDateTime start, LocalDateTime end) {
        GiuCho hold = new GiuCho();
        hold.maGiuCho = id(); hold.donDatVe = order; hold.chuyenXe = trip; hold.ghe = seat;
        hold.ngayDat = start; hold.thoiDiemBatDau = start; hold.thoiDiemHetHan = end;
        hold.trangThai = TrangThaiGiuCho.DANG_GIU;
        return hold;
    }

    private GiuCho heldSeat(OrderContext context, String seatId) {
        return active(context).stream().filter(h -> h.ghe.maGhe.equals(seatId)).findFirst()
                .orElseThrow(() -> invalid("maGhe", "Ghế không thuộc danh sách đang giữ của đơn."));
    }
    private List<GiuCho> active(OrderContext context) {
        return context.holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU).toList();
    }
    private LocalDateTime start(OrderContext context) {
        return context.holds.stream().map(h -> h.thoiDiemBatDau).min(Comparator.naturalOrder()).orElse(null);
    }
    private LocalDateTime deadline(OrderContext context) {
        LocalDateTime expiresAt = context.holds.stream().map(h -> h.thoiDiemHetHan).min(Comparator.naturalOrder()).orElse(null);
        if (expiresAt == null && pending(context.order)) throw conflict("Đơn chưa thanh toán thiếu hạn giữ ghế.");
        return expiresAt;
    }
    private void own(DonDatVe order) {
        if (!order.nguoiDat.maNguoiDung.equals(identity().maNguoiDung())) throw AccessControl.denied();
    }
    private CurrentUser identity() {
        var request = RequestIdentity.get();
        if (request == null || request.user() == null) throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        return request.user();
    }
    private BigDecimal baseFare(ChuyenXe trip) {
        BigDecimal price = trip.tuyenXe.giaCoBan;
        if (!TripSaleRules.validPrice(price)) throw conflict("Giá gốc của chuyến không hợp lệ.");
        return price;
    }
    private void validateTotal(BigDecimal total) {
        if (total.signum() < 0 || total.compareTo(new BigDecimal("999999999999")) > 0)
            throw invalid("tongTien", "Tổng tiền vượt giới hạn lưu trữ.");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZONE); }
    private static OffsetDateTime offset(LocalDateTime time) { return time == null ? null : time.atZone(ZONE).toOffsetDateTime(); }
    private static String amount(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String code(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}")) throw invalid(field, "Mã phải gồm 1–40 ký tự chữ, số, gạch dưới hoặc gạch ngang.");
        return value;
    }
    private static String text(String value, String field, int max) {
        String normalized = optional(value, field, max);
        if (normalized == null) throw invalid(field, "Trường này là bắt buộc.");
        return normalized;
    }
    private static String optional(String value, String field, int max) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.length() > max) throw invalid(field, "Trường này tối đa " + max + " ký tự.");
        return normalized.isEmpty() ? null : normalized;
    }
    private static AccountException invalid(String field, String message) { return AccountException.invalid(field, message); }
    private static AccountException conflict(String message) { return new AccountException(409, "INVALID_STATE_TRANSITION", message); }
    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            List<String> fields = new ArrayList<>(); value.fieldNames().forEachRemaining(fields::add);
            fields.stream().sorted().forEach(field -> sorted.set(field, canonical(value.get(field))));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = JSON.createArrayNode(); value.forEach(child -> array.add(canonical(child))); return array;
        }
        return value;
    }
    private static String stringify(Object body) {
        try { return JSON.writeValueAsString(body); }
        catch (JsonProcessingException error) { throw new IllegalStateException(error); }
    }
    private static String storedResponse(Object body) {
        // Passenger drafts must remain volatile even when PATCH results are persisted for retries.
        JsonNode snapshot = JSON.valueToTree(body);
        if (snapshot.isObject() && snapshot.has("hanhKhach")) {
            ((ObjectNode) snapshot).set("hanhKhach", JSON.createArrayNode());
            ((ObjectNode) snapshot).put("coTheChotGia", false);
        }
        return stringify(snapshot);
    }
    private static JsonNode parse(String body) {
        try { return JSON.readTree(body); }
        catch (JsonProcessingException error) { throw new IllegalStateException(error); }
    }
    private record OrderContext(DonDatVe order, ChuyenXe trip, List<GiuCho> holds, List<VeXe> tickets,
                                List<PassengerRequest> passengers) {}
    private static final class ExpiredOrder extends RuntimeException {
        private final String maDon;
        private ExpiredOrder(String maDon) { this.maDon = maDon; }
    }
}
