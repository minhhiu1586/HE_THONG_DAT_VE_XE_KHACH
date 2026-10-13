package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto;
import com.datvexe.dto.BookingDto.*;
import com.datvexe.dto.HoldSeatRequest;
import com.datvexe.dto.HoldSeatResponse;
import com.datvexe.dto.PricingDto.*;
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
            order.maDon = OrderCode.next();
            order.nguoiDat = buyer;
            order.khachDatEmail = buyer.email;
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

    public QuoteSnapshot baoGia(String maDon, QuoteRequest request, List<PassengerRequest> passengers) {
        Result result = execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            pricingReady(em, context);
            if (request == null) throw invalid("body", "Thiếu yêu cầu báo giá.");
            String promotionId = request.maKhuyenMai() == null ? null : code(request.maKhuyenMai(), "maKhuyenMai");
            KhuyenMai promotion = promotionForPricing(em, context, promotionId, false);
            LocalDateTime checkedAt = now();
            PriceSummary price = calculate(context, promotion, checkedAt);
            String fingerprint = priceFingerprint(context, promotion);
            checkExpiry(context, now());
            QuoteResponse quote = new QuoteResponse(id(), context.order.maDon, "VND", offset(checkedAt),
                    offset(deadline(context)), price.tongTienGoc(), price.tongTienGiam(), price.tongThanhToan(),
                    price.chiTietGia(), promotionResult(promotion, price));
            return new Result(200, new QuoteSnapshot(identity().maNguoiDung(), context.order.maDon,
                    promotionId, fingerprint, quote));
        });
        return (QuoteSnapshot) result.body();
    }

    public Result chotGia(String maDon, FreezeRequest request, String key,
                         List<PassengerRequest> passengers, QuoteSnapshot quote) {
        return execute(key, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            checkExpiry(context, now());
            if (request == null) throw invalid("body", "Thiếu yêu cầu chốt giá.");
            String quoteId = code(request.maBaoGia(), "maBaoGia");
            if (quote == null || !quote.userId().equals(identity().maNguoiDung())
                    || !quote.maDon().equals(context.order.maDon)
                    || !quote.response().maBaoGia().equals(quoteId)
                    || !clock.instant().isBefore(quote.response().thoiDiemHetHan().toInstant()))
                throw new AccountException(409, "QUOTE_EXPIRED", "Báo giá không còn trong phiên hoặc đã hết hạn.");
            // A new key cannot consume another use for an already frozen order.
            if (context.order.trangThai == TrangThaiDon.CHO_THANH_TOAN) {
                PriceSummary frozen = frozenPrice(context);
                if (!Objects.equals(context.order.maKhuyenMaiDaApDung, quote.maKhuyenMai())
                        || !frozen.equals(summary(quote.response())))
                    throw new AccountException(409, "PRICE_CHANGED", "Đơn đã có giá chốt khác; hãy đọc lại đơn.");
                return new Result(200, freezeResponse(context, quoteId, frozen));
            }
            pricingReady(em, context);
            KhuyenMai promotion = promotionForPricing(em, context, quote.maKhuyenMai(), true);
            // Use a fresh clock after waiting for the promotion lock.
            LocalDateTime checkedAt = now();
            checkExpiry(context, checkedAt);
            PriceSummary price = calculate(context, promotion, checkedAt);
            if (!quote.fingerprint().equals(priceFingerprint(context, promotion))
                    || !price.equals(summary(quote.response())))
                throw new AccountException(409, "PRICE_CHANGED", "Dữ liệu hoặc giá đã thay đổi; vui lòng lấy báo giá mới.");
            context.order.tongTien = new BigDecimal(price.tongTienGoc());
            context.order.tienGiam = new BigDecimal(price.tongTienGiam());
            context.order.maKhuyenMaiDaApDung = quote.maKhuyenMai();
            context.order.trangThai = TrangThaiDon.CHO_THANH_TOAN;
            checkExpiry(context, now());
            return new Result(200, freezeResponse(context, quoteId, price));
        });
    }

    public Result updateContact(String maDon, JsonNode body, String key, List<PassengerRequest> passengers) {
        return execute(key, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(em, context);
            if (body == null || !body.isObject() || body.isEmpty()) throw invalid("body", "Cần ít nhất một trường liên hệ.");
            Set<String> fields = Set.of("khachDatTen", "khachDatSoDienThoai", "khachDatEmail", "ghiChu");
            body.fieldNames().forEachRemaining(field -> {
                if (!fields.contains(field)) throw invalid(field, "Trường không được hỗ trợ.");
                JsonNode value = body.get(field);
                if (!value.isTextual() && !(field.equals("ghiChu") && value.isNull()))
                    throw invalid(field, "Trường phải là chuỗi; chỉ ghiChu được phép null.");
            });
            if (body.has("khachDatTen")) context.order.khachDatTen = text(body.get("khachDatTen").asText(), "khachDatTen", 120);
            if (body.has("khachDatSoDienThoai")) context.order.khachDatSoDienThoai = text(body.get("khachDatSoDienThoai").asText(), "khachDatSoDienThoai", 20);
            if (body.has("khachDatEmail")) {
                String email = text(body.get("khachDatEmail").asText(), "khachDatEmail", 254);
                if (!email.matches("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$"))
                    throw invalid("khachDatEmail", "Địa chỉ email nhận vé không hợp lệ.");
                context.order.khachDatEmail = email;
            }
            if (body.has("ghiChu")) context.order.ghiChu = optional(body.get("ghiChu").isNull() ? null : body.get("ghiChu").asText(), "ghiChu", 500);
            return updated(em, context);
        });
    }

    public Result savePassengers(String maDon, PassengersRequest request) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon);
            editable(em, context);
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
            editable(em, context);
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
            editable(em, context);
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
            editable(em, context);
            if (request == null) throw invalid("body", "Thiếu mã khuyến mãi.");
            context.order.maKhuyenMaiDaApDung = code(request.maKhuyenMai(), "maKhuyenMai");
            PricePreview price = preview(em, context, now(), true);
            if (price.lyDoKhongApDung() != null)
                throw new AccountException(409, "PROMOTION_NOT_APPLICABLE", price.lyDoKhongApDung());
            return updated(em, context);
        });
    }

    public Result removePromotion(String maDon, List<PassengerRequest> passengers) {
        return execute(null, em -> {
            OrderContext context = lockOrder(em, maDon, passengers);
            editable(em, context);
            context.order.maKhuyenMaiDaApDung = null;
            return updated(em, context);
        });
    }

    public Result cancelOrder(String maDon, String key) {
        return execute(key, em -> {
            OrderContext context = lockOrder(em, maDon);
            if (context.order.trangThai == TrangThaiDon.DA_HUY || context.order.trangThai == TrangThaiDon.HET_HAN)
                return result(em, context, List.of());
            editable(em, context);
            cancel(context);
            return result(em, context, List.of());
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
                        || (request.method().equals("POST") && (request.path().equals("/api/booking/hold")
                        || request.path().endsWith("/huy") || request.path().endsWith("/chot-gia")));
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

    private void editable(EntityManager em, OrderContext context) {
        checkExpiry(context, now());
        if (context.order.trangThai != TrangThaiDon.CHO_XU_LY
                && context.order.trangThai != TrangThaiDon.CHO_THANH_TOAN)
            throw conflict("Chỉ được sửa đơn chưa thanh toán.");
        if (new DonDatVeRepository(em).hasBlockingPayment(context.order.maDon))
            throw new AccountException(409, "PAYMENT_PROCESSING", "Đơn đã có giao dịch đang xử lý hoặc thành công.");
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
        boolean frozen = context.order.trangThai == TrangThaiDon.CHO_THANH_TOAN;
        context.order.trangThai = TrangThaiDon.HET_HAN;
        context.holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU).forEach(h -> h.trangThai = TrangThaiGiuCho.HET_HAN);
        context.passengers.clear();
        if (!frozen) context.order.maKhuyenMaiDaApDung = null;
    }

    private void cancel(OrderContext context) {
        boolean frozen = context.order.trangThai == TrangThaiDon.CHO_THANH_TOAN;
        context.order.trangThai = TrangThaiDon.DA_HUY;
        active(context).forEach(h -> h.trangThai = TrangThaiGiuCho.DA_HUY);
        context.passengers.clear();
        if (!frozen) {
            context.order.maKhuyenMaiDaApDung = null;
            context.order.tongTien = BigDecimal.ZERO;
            context.order.tienGiam = BigDecimal.ZERO;
        }
    }

    private Result updated(EntityManager em, OrderContext context) {
        if (context.order.trangThai == TrangThaiDon.DA_HUY) return result(em, context, List.of());
        // Thaw only after the requested edit has passed validation; failures roll back the entire transaction.
        if (context.order.trangThai == TrangThaiDon.CHO_THANH_TOAN)
            context.order.trangThai = TrangThaiDon.CHO_XU_LY;
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
                .map(v -> new TicketResponse(v.maVe, v.maTraCuu, (v.trangThai == TrangThaiVe.DA_XUAT_VE || v.trangThai == TrangThaiVe.DA_LEN_XE) ? com.datvexe.ticket.TicketQr.dataUri(v.maQR) : null, v.ghe.maGhe, v.hoTenKhachHang, v.soDienThoaiKhachHang,
                        v.cccdHanhKhach, amount(v.giaVe), v.trangThai, offset(v.ngayXuatVe))).toList();
        Set<String> heldSeats = new HashSet<>();
        active(context).forEach(h -> heldSeats.add(h.ghe.maGhe));
        List<PassengerRequest> passengers = Set.of(TrangThaiDon.CHO_XU_LY, TrangThaiDon.CHO_THANH_TOAN).contains(context.order.trangThai)
                ? context.passengers.stream().filter(passenger -> heldSeats.contains(passenger.maGhe()))
                    .sorted(Comparator.comparing(PassengerRequest::maGhe)).toList()
                : List.of();
        Set<String> passengerSeats = new HashSet<>();
        passengers.forEach(passenger -> passengerSeats.add(passenger.maGhe()));
        boolean ready = context.order.trangThai == TrangThaiDon.CHO_XU_LY && now.isBefore(deadline(context))
                && !heldSeats.isEmpty() && heldSeats.equals(passengerSeats) && passengers.size() == heldSeats.size();
        return new Result(200, new OrderResponse(context.order.maDon, context.trip.maChuyen, context.order.trangThai,
                context.order.khachDatTen, context.order.khachDatSoDienThoai, context.order.khachDatEmail, context.order.ghiChu, offset(now),
                offset(start(context)), offset(deadline(context)), holdResponses(context), passengers, tickets,
                preview(em, context, now), ready, frozenPrice(context), notices, emailStatus(em, context.order)));
    }

    private BookingDto.EmailStatus emailStatus(EntityManager em, DonDatVe order) {
        com.datvexe.domain.EmailOutbox notification = em.find(com.datvexe.domain.EmailOutbox.class, order.maDon);
        return notification == null ? null : new BookingDto.EmailStatus(notification.status, notification.recipient, offset(notification.sentAt));
    }

    private PricePreview preview(EntityManager em, OrderContext context, LocalDateTime now) {
        return preview(em, context, now, context.order.trangThai == TrangThaiDon.CHO_XU_LY);
    }

    private PricePreview preview(EntityManager em, OrderContext context, LocalDateTime now, boolean draft) {
        BigDecimal total = draft ? baseFare(context.trip).multiply(BigDecimal.valueOf(active(context).size())) : context.order.tongTien;
        String selected = context.order.maKhuyenMaiDaApDung;
        BigDecimal discount = draft ? BigDecimal.ZERO : context.order.tienGiam;
        String applied = context.order.trangThai == TrangThaiDon.CHO_THANH_TOAN
                || context.order.trangThai == TrangThaiDon.DA_THANH_TOAN ? selected : null, reason = null;
        if (draft && selected != null) {
            KhuyenMai promotion = new KhuyenMaiRepository(em, now).find(selected);
            if (promotion == null) reason = "Mã khuyến mãi không tồn tại.";
            else if (!promotion.kiemTraHieuLuc(now)) reason = "Mã chưa có hiệu lực, đã hết hạn hoặc hết lượt.";
            else if (!promotion.kiemTraDieuKien(total, now)) reason = "Tổng tiền chưa đạt điều kiện khuyến mãi.";
            else if (promotion.soLuong - (long) promotion.soLuongDaDung
                    - new DonDatVeRepository(em).reservedPromotionUses(selected, context.order.maDon, now) <= 0)
                reason = "Các lượt khuyến mãi còn lại đang được giữ bởi đơn khác.";
            else { discount = promotion.tinhTienGiam(total, now); applied = selected; }
        }
        return new PricePreview(amount(total), amount(discount), amount(total.subtract(discount)), selected, applied, reason);
    }

    private void pricingReady(EntityManager em, OrderContext context) {
        checkExpiry(context, now());
        if (context.order.trangThai != TrangThaiDon.CHO_XU_LY || !context.tickets.isEmpty())
            throw conflict("Chỉ được báo và chốt giá cho đơn nháp chưa phát hành vé.");
        if (new DonDatVeRepository(em).hasBlockingPayment(context.order.maDon))
            throw new AccountException(409, "PAYMENT_PROCESSING", "Đơn đã có giao dịch đang xử lý hoặc thành công.");
        TripSaleRules.ensureSellable(em, context.trip, now());
        List<GiuCho> holds = active(context);
        if (holds.isEmpty()) throw conflict("Đơn không còn ghế đang giữ.");
        Set<String> seats = new HashSet<>();
        for (GiuCho hold : holds) {
            if (!seats.add(hold.ghe.maGhe) || context.trip.xe == null
                    || !hold.ghe.xe.maXe.equals(context.trip.xe.maXe)
                    || hold.ghe.trangThai != TrangThaiGhe.HOAT_DONG)
                throw new AccountException(409, "SEAT_UNAVAILABLE", "Ghế giữ không còn hợp lệ.");
        }
        long sold = em.createQuery("select count(v) from VeXe v where v.chuyenXe = :trip "
                + "and v.ghe.maGhe in :seats and v.trangThai in :states", Long.class)
                .setParameter("trip", context.trip).setParameter("seats", seats)
                .setParameter("states", List.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE)).getSingleResult();
        long otherHolds = em.createQuery("select count(h) from GiuCho h where h.chuyenXe = :trip "
                + "and h.ghe.maGhe in :seats and h.trangThai = :holding and h.thoiDiemHetHan > :now "
                + "and (h.donDatVe is null or h.donDatVe.maDon <> :order)", Long.class)
                .setParameter("trip", context.trip).setParameter("seats", seats)
                .setParameter("holding", TrangThaiGiuCho.DANG_GIU).setParameter("now", now())
                .setParameter("order", context.order.maDon).getSingleResult();
        if (sold > 0 || otherHolds > 0) throw new AccountException(409, "SEAT_UNAVAILABLE", "Ghế không còn thuộc lượt giữ hợp lệ của đơn.");
        Set<String> passengerSeats = new HashSet<>();
        for (PassengerRequest passenger : context.passengers) {
            if (passenger == null || !passengerSeats.add(code(passenger.maGhe(), "maGhe")))
                throw invalid("hanhKhach", "Danh sách hành khách không hợp lệ.");
            text(passenger.hoTen(), "hoTen", 120);
            text(passenger.soDienThoai(), "soDienThoai", 20);
            optional(passenger.cccd(), "cccd", 20);
        }
        if (!seats.equals(passengerSeats) || context.passengers.size() != holds.size())
            throw invalid("hanhKhach", "Cần đủ hành khách cho toàn bộ ghế đang giữ trong phiên hiện tại.");
        text(context.order.khachDatTen, "khachDatTen", 120);
        text(context.order.khachDatSoDienThoai, "khachDatSoDienThoai", 20);
        checkExpiry(context, now());
    }

    private KhuyenMai promotionForPricing(EntityManager em, OrderContext context, String code, boolean lock) {
        if (code == null) return null;
        KhuyenMaiRepository repository = new KhuyenMaiRepository(em, now());
        KhuyenMai promotion = lock ? repository.lock(code) : repository.find(code);
        if (promotion == null) throw new AccountException(409, "PROMOTION_NOT_APPLICABLE", "Mã khuyến mãi không tồn tại.");
        if (lock) em.refresh(promotion, LockModeType.PESSIMISTIC_WRITE);
        LocalDateTime checkedAt = now();
        checkExpiry(context, checkedAt);
        if (checkedAt.isBefore(promotion.ngayBatDau) || !checkedAt.isBefore(promotion.ngayKetThuc))
            throw new AccountException(409, "PROMOTION_NOT_APPLICABLE", "Mã khuyến mãi chưa có hiệu lực hoặc đã hết hạn.");
        long reserved = new DonDatVeRepository(em).reservedPromotionUses(code, context.order.maDon, checkedAt);
        if (promotion.soLuong - (long) promotion.soLuongDaDung - reserved <= 0)
            throw new AccountException(409, "PROMOTION_EXHAUSTED", "Mã đã hết lượt khả dụng.");
        BigDecimal total = baseFare(context.trip).multiply(BigDecimal.valueOf(active(context).size()));
        validateTotal(total);
        if (!promotion.kiemTraDieuKien(total, checkedAt))
            throw new AccountException(409, "PROMOTION_NOT_APPLICABLE", "Đơn chưa đạt điều kiện khuyến mãi.");
        return promotion;
    }

    private PriceSummary calculate(OrderContext context, KhuyenMai promotion, LocalDateTime checkedAt) {
        BigDecimal fare = baseFare(context.trip);
        BigDecimal total = fare.multiply(BigDecimal.valueOf(active(context).size()));
        validateTotal(total);
        if (promotion != null && !promotion.kiemTraDieuKien(total, checkedAt))
            throw new AccountException(409, "PROMOTION_NOT_APPLICABLE", "Khuyến mãi không còn đủ điều kiện.");
        BigDecimal discount = promotion == null ? BigDecimal.ZERO : promotion.tinhTienGiam(total, checkedAt);
        return PriceCalculator.calculate(active(context).stream()
                .map(h -> new PriceCalculator.SeatPrice(h.ghe.maGhe, fare)).toList(), discount);
    }

    private String priceFingerprint(OrderContext context, KhuyenMai promotion) {
        ObjectNode input = JSON.createObjectNode();
        input.put("maDon", context.order.maDon).put("maChuyen", context.trip.maChuyen)
                .put("giaCoBan", amount(baseFare(context.trip)))
                .put("khachDatTen", context.order.khachDatTen)
                .put("khachDatSoDienThoai", context.order.khachDatSoDienThoai)
                .put("ghiChu", context.order.ghiChu).put("email", context.order.khachDatEmail).put("expiresAt", deadline(context).toString());
        input.set("seats", JSON.valueToTree(active(context).stream().map(h -> h.ghe.maGhe).sorted().toList()));
        input.set("passengers", JSON.valueToTree(context.passengers.stream()
                .sorted(Comparator.comparing(PassengerRequest::maGhe)).toList()));
        if (promotion == null) input.putNull("promotion");
        else {
            ObjectNode rules = input.putObject("promotion");
            rules.put("code", promotion.maKhuyenMai).put("type", promotion.loaiGiam.name())
                    .put("value", amount(promotion.giaTriGiam))
                    .put("maximum", promotion.giaTriToiDa == null ? null : amount(promotion.giaTriToiDa))
                    .put("minimum", amount(promotion.donToiThieu)).put("quantity", promotion.soLuong)
                    .put("starts", promotion.ngayBatDau.toString()).put("ends", promotion.ngayKetThuc.toString());
        }
        return IdempotencyRepository.hash(stringify(canonical(input)));
    }

    private PriceSummary frozenPrice(OrderContext context) {
        if (context.order.trangThai == TrangThaiDon.CHO_THANH_TOAN) {
            List<GiuCho> holds = active(context);
            if (holds.isEmpty()) throw conflict("Đơn đã chốt không còn ghế giữ.");
            BigDecimal fare = originalFare(context.order, holds.size());
            return PriceCalculator.calculate(holds.stream()
                    .map(h -> new PriceCalculator.SeatPrice(h.ghe.maGhe, fare)).toList(), context.order.tienGiam);
        }
        if (context.order.trangThai != TrangThaiDon.DA_THANH_TOAN || context.tickets.isEmpty()) return null;
        BigDecimal fare = originalFare(context.order, context.tickets.size());
        List<PriceLine> lines = context.tickets.stream().sorted(Comparator.comparing(v -> v.ghe.maGhe)).map(v -> {
            if (!TripSaleRules.validPrice(v.giaVe) || v.giaVe.compareTo(fare) > 0)
                throw conflict("Giá vé đã phát hành không khớp giá gốc của đơn.");
            return new PriceLine(v.ghe.maGhe, amount(fare), amount(fare.subtract(v.giaVe)), amount(v.giaVe));
        }).toList();
        BigDecimal net = context.tickets.stream().map(v -> v.giaVe).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (net.compareTo(context.order.tongTien.subtract(context.order.tienGiam)) != 0)
            throw conflict("Tổng giá vé đã phát hành không khớp tổng tiền đơn.");
        return new PriceSummary("VND", amount(context.order.tongTien), amount(context.order.tienGiam), amount(net), lines);
    }

    private BigDecimal originalFare(DonDatVe order, int count) {
        if (!TripSaleRules.validPrice(order.tongTien) || !TripSaleRules.validPrice(order.tienGiam)
                || order.tienGiam.compareTo(order.tongTien) > 0)
            throw conflict("Giá chốt của đơn không hợp lệ.");
        try { return order.tongTien.divide(BigDecimal.valueOf(count), 0, java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw conflict("Tổng gốc không khớp số ghế cùng giá."); }
    }

    private static PriceSummary summary(QuoteResponse quote) {
        return new PriceSummary(quote.donViTienTe(), quote.tongTienGoc(), quote.tongTienGiam(),
                quote.tongThanhToan(), quote.chiTietGia());
    }

    private static PromotionResult promotionResult(KhuyenMai promotion, PriceSummary price) {
        return promotion == null ? null : new PromotionResult(true, price.tongTienGiam());
    }

    private FreezePriceResponse freezeResponse(OrderContext context, String quoteId, PriceSummary price) {
        return new FreezePriceResponse(quoteId, context.order.maDon, context.order.trangThai, "VND", offset(deadline(context)),
                price.tongTienGoc(), price.tongTienGiam(), price.tongThanhToan(), price.chiTietGia(),
                context.order.maKhuyenMaiDaApDung == null ? null : new PromotionResult(true, price.tongTienGiam()));
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
