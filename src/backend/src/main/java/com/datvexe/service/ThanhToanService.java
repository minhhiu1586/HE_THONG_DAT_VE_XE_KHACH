package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto;
import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.PricingDto.PriceLine;
import com.datvexe.dto.ThanhToanDto;
import com.datvexe.payment.PaymentContextStore;
import com.datvexe.payment.VNPayClient;
import com.datvexe.payment.VNPayConfig;
import com.datvexe.repository.IdempotencyRepository;
import com.datvexe.repository.DonDatVeRepository;
import com.datvexe.repository.GiuChoRepository;
import com.datvexe.repository.ThanhToanRepository;
import com.datvexe.security.AccessControl;
import com.datvexe.security.AccountException;
import com.datvexe.security.CurrentUser;
import com.datvexe.security.RequestIdentity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** T18 payment orchestration. Provider calls happen before/after the DB transaction. */
public final class ThanhToanService {
    private static final ZoneId ZONE = TripSaleRules.ZONE;
    private static final String VND = "VND";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final PaymentContextStore SHARED_CONTEXTS = new PaymentContextStore();
    private final EntityManagerFactory factory;
    private final Clock clock;
    private final PaymentContextStore contexts;
    private final Gateway gateway;

    public ThanhToanService() {
        this(Jpa.factory(), Clock.system(ZONE), SHARED_CONTEXTS, defaultGateway());
    }

    public ThanhToanService(EntityManagerFactory factory) {
        this(factory, Clock.system(ZONE), SHARED_CONTEXTS, defaultGateway());
    }

    public ThanhToanService(EntityManagerFactory factory, Gateway gateway) {
        this(factory, Clock.system(ZONE), SHARED_CONTEXTS, gateway);
    }

    public ThanhToanService(EntityManagerFactory factory, Clock clock,
                            PaymentContextStore contexts, Gateway gateway) {
        this.factory = Objects.requireNonNull(factory);
        this.clock = Objects.requireNonNull(clock);
        this.contexts = Objects.requireNonNull(contexts);
        this.gateway = gateway;
    }

    /** Provider abstraction keeps the service independent from the VNPay HTTP client. */
    public interface Gateway {
        String payUrl(String reference, BigDecimal amount, Instant created, Instant expiry, String ip);
        VNPayClient.VerifiedPayment queryDr(String reference, BigDecimal expectedAmount, Instant originalCreateDate, String ip);
        VNPayClient.VerifiedPayment verifyCallback(Map<String, String> parameters);
    }

    private static Gateway defaultGateway() {
        VNPayClient client = new VNPayClient(VNPayConfig.fromEnvironment());
        return new Gateway() {
            @Override public String payUrl(String reference, BigDecimal amount, Instant created, Instant expiry, String ip) {
                return client.payUrl(reference, amount, created, expiry, ip);
            }
            @Override public VNPayClient.VerifiedPayment queryDr(String reference, BigDecimal amount, Instant created, String ip) {
                return client.queryDr(reference, amount, created, ip);
            }
            @Override public VNPayClient.VerifiedPayment verifyCallback(Map<String, String> parameters) {
                return client.verifyCallback(parameters);
            }
        };
    }

    public BookingDto.Result create(String orderId, ThanhToanDto.CreateRequest request,
                                    String idempotencyKey, List<PassengerRequest> passengers, String clientIp) {
        requireKey(idempotencyKey);
        if (request == null || request.maPhuongThuc() == null || request.maPhuongThuc().isBlank())
            throw AccountException.invalid("maPhuongThuc", "Phương thức thanh toán là bắt buộc.");
        java.util.concurrent.atomic.AtomicReference<PaymentContextStore.Attempt> createdContext = new java.util.concurrent.atomic.AtomicReference<>();
        BookingDto.Result result = transaction(em -> {
            CurrentUser user = currentUser();
            IdemContext idem = prepareIdempotency(em, user, orderId, request, idempotencyKey);
            NguoiDung caller = AccessControl.authenticated(em, user, clock.instant());
            AccessControl.role(caller, LoaiTaiKhoan.KHACH_HANG);
            IdempotencyRepository.Saved saved = idem.repository().find(idem.scope(), clock.instant());
            if (saved != null) {
                if (!saved.hash().equals(idem.hash()))
                    throw new AccountException(409, "IDEMPOTENCY_KEY_REUSED", "Khóa đã được dùng cho nội dung khác.");
                return replay(em, saved);
            }
            OrderTrip pair = lockOrderTrip(em, orderId, caller);
            DonDatVe order = pair.order();
            ensurePayable(order, pair.holds());
            if (!em.createQuery("select t.maGiaoDich from ThanhToan t where t.donDatVe = :order and t.trangThai = :state", String.class)
                    .setParameter("order", order).setParameter("state", TrangThaiThanhToan.THANH_CONG).getResultList().isEmpty())
                throw new AccountException(409, "PAYMENT_PROCESSING", "Đơn đã có giao dịch thành công cần xử lý, không tạo lần thu tiền mới.");

            ThanhToanRepository payments = new ThanhToanRepository(em);
            ThanhToan pending = payments.pendingForOrder(order.maDon);
            if (pending != null) {
                PaymentContextStore.Attempt old = contexts.get(pending.maGiaoDich);
                if (old == null && "VNPAY".equalsIgnoreCase(pending.phuongThucThanhToan.maPhuongThuc))
                    throw new AccountException(409, "PAYMENT_CONTEXT_MISSING", "Không còn dữ liệu thanh toán tạm; cần đối soát giao dịch hiện có.");
                return remember(em, idem, new BookingDto.Result(202, toPayment(pending, old, false)));
            }

            PhuongThucThanhToan method = em.find(PhuongThucThanhToan.class, request.maPhuongThuc().trim().toUpperCase(Locale.ROOT));
            if (method == null) throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED", "Phương thức thanh toán chưa được hỗ trợ.");
            if (method.trangThai != TrangThaiPhuongThuc.HOAT_DONG)
                throw new AccountException(409, "PAYMENT_METHOD_INACTIVE", "Phương thức thanh toán hiện không khả dụng.");
            if (!Set.of("VNPAY", "MIEN_PHI").contains(method.maPhuongThuc))
                throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED", "Phương thức này chưa được tích hợp.");

            BigDecimal amount = payable(order);
            String reference = "PAY-" + UUID.randomUUID();
            Instant created = clock.instant();
            Instant expiry = holdExpiry(pair.holds());
            ThanhToan payment = new ThanhToan();
            payment.maGiaoDich = reference;
            payment.donDatVe = order;
            payment.phuongThucThanhToan = method;
            payment.soTien = amount;
            payment.noiDungThanhToan = "maDon=" + order.maDon + ";phuongThuc=" + method.maPhuongThuc;
            payment.trangThai = TrangThaiThanhToan.CHO_XU_LY;
            List<PassengerRequest> copy = copyPassengers(passengers);
            validatePassengers(active(pair.holds()), copy);
            String url = null;
            if ("MIEN_PHI".equalsIgnoreCase(method.maPhuongThuc)) {
                if (amount.signum() != 0) throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED", "Đơn có tiền không thể dùng phương thức miễn phí.");
            } else {
                if (!"VNPAY".equalsIgnoreCase(method.maPhuongThuc) || gateway == null)
                    throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Cổng thanh toán hiện chưa được cấu hình.");
                url = gateway.payUrl(reference, amount, created, expiry, clientIp);
            }
            PaymentContextStore.Attempt attempt = new PaymentContextStore.Attempt(reference, order.maDon,
                    caller.maNguoiDung, method.maPhuongThuc, amount, created, expiry,
                    created.plus(PaymentContextStore.RETENTION), clientIp, url, seatIds(active(pair.holds())),
                    copy, frozenLines(order, pair.holds()));
            ensurePayable(order, pair.holds());
            em.persist(payment);
            if ("MIEN_PHI".equalsIgnoreCase(method.maPhuongThuc)) {
                BookingDto.Result done = finish(em, payment, new VNPayClient.VerifiedPayment(reference, 0, "00", "00", created, "FREE"), attempt);
                if (((ThanhToanDto.PaymentResponse) done.body()).canDoiSoat()) {
                    // There is no external money to reconcile on the internal zero-value path.
                    em.remove(payment);
                    if (order.trangThai == TrangThaiDon.HET_HAN) throw new ExpiredOrder();
                    throw new AccountException(409, "INVALID_STATE_TRANSITION", "Đơn không còn đủ điều kiện phát hành vé miễn phí.");
                }
                createdContext.set(attempt);
                return remember(em, idem, new BookingDto.Result(201, done.body()));
            }
            createdContext.set(attempt);
            return remember(em, idem, new BookingDto.Result(201, toPayment(payment, attempt, url, false)));
        });
        // Publish volatile data only after the durable payment/idempotency commit succeeds.
        if (createdContext.get() != null) contexts.put(createdContext.get());
        return result;
    }

    public ThanhToanDto.PaymentResponse get(String id) {
        return transaction(em -> {
            NguoiDung caller = authenticate(em);
            ThanhToan payment = new ThanhToanRepository(em).find(id);
            if (payment == null) throw notFound();
            lockOrderTrip(em, payment.donDatVe.maDon, caller);
            payment = new ThanhToanRepository(em).lockById(id);
            return toPayment(payment, contexts.get(payment.maGiaoDich), false);
        });
    }

    public ThanhToanDto.PageResponse history(String orderId, int page, int size, String state, String sort) {
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE)
            throw AccountException.invalid("page", "page >= 1 và size trong khoảng 1..100, không vượt giới hạn phân trang.");
        if (sort != null && !sort.isBlank() && !Set.of("maGiaoDich", "maGiaoDich,asc", "maGiaoDich,desc").contains(sort))
            throw AccountException.invalid("sort", "Chỉ hỗ trợ sắp xếp theo maGiaoDich.");
        return transaction(em -> {
            NguoiDung caller = authenticate(em);
            lockOrderTrip(em, orderId, caller);
            TrangThaiThanhToan filter = parseState(state);
            ThanhToanRepository repository = new ThanhToanRepository(em);
            long total = repository.countForOrder(orderId, filter);
            boolean descending = sort == null || sort.isBlank() || "maGiaoDich,desc".equals(sort);
            List<ThanhToanDto.PaymentResponse> items = new ArrayList<>(repository.pageForOrder(orderId, filter, (page - 1) * size, size, descending)
                    .stream().map(payment -> toPayment(payment, contexts.get(payment.maGiaoDich), false)).toList());
            return new ThanhToanDto.PageResponse(items, page, size, total,
                    (int) Math.ceil(total / (double) size));
        });
    }

    /** QueryDR is deliberately outside the transaction; result is then processed atomically. */
    public ThanhToanDto.PaymentResponse verify(String id, String clientIp) {
        // Validate account and ownership before exposing volatile context or calling a provider.
        ThanhToanDto.PaymentResponse current = getOwnedPayment(id);
        if (current.trangThai() != TrangThaiThanhToan.CHO_XU_LY) return current;
        PaymentContextStore.Attempt context = contexts.get(id);
        if (gateway == null) throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Cổng thanh toán hiện chưa được cấu hình.");
        if (context == null) throw new AccountException(409, "PAYMENT_CONTEXT_MISSING", "Không còn dữ liệu tạm để xác minh giao dịch; cần đối soát.");
        VNPayClient.VerifiedPayment verified = gateway.queryDr(id, new BigDecimal(current.soTien()), context.createdAt(), clientIp);
        return receive(verified).payment();
    }

    /** Called only after the controller/provider adapter has verified the callback signature. */
    public ProcessingResult receive(VNPayClient.VerifiedPayment verified) {
        if (verified == null || verified.reference() == null) throw new AccountException(400, "INVALID_PAYMENT_RESULT", "Thiếu kết quả thanh toán từ cổng.");
        return transaction(em -> {
            ThanhToanRepository repository = new ThanhToanRepository(em);
            ThanhToan raw = repository.find(verified.reference());
            if (raw == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy giao dịch.");
            lockOrderTrip(em, raw.donDatVe.maDon, null);
            ThanhToan payment = repository.lockById(verified.reference());
            if (payment == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy giao dịch.");
            if (!"VNPAY".equals(payment.phuongThucThanhToan.maPhuongThuc))
                throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED", "Giao dịch này không thuộc cổng VNPay.");
            PaymentContextStore.Attempt context = contexts.get(payment.maGiaoDich);
            if (verified.amountVnd() != payment.soTien.setScale(0, RoundingMode.UNNECESSARY).longValueExact())
                throw new AccountException(409, "PAYMENT_AMOUNT_MISMATCH", "Số tiền từ cổng không khớp đơn.");
            if (payment.trangThai != TrangThaiThanhToan.CHO_XU_LY)
                return new ProcessingResult(toPayment(payment, context, false), true);
            boolean success = "00".equals(verified.responseCode()) && "00".equals(verified.transactionStatus());
            boolean explicitFailure = "02".equals(verified.transactionStatus());
            if (!success && !explicitFailure) return new ProcessingResult(toPayment(payment, context, false), false);
            if (explicitFailure) {
                payment.trangThai = TrangThaiThanhToan.THAT_BAI;
                return new ProcessingResult(toPayment(payment, context, false), false);
            }
            BookingDto.Result result = finish(em, payment, verified, context);
            return new ProcessingResult((ThanhToanDto.PaymentResponse) result.body(), false);
        });
    }

    public record ProcessingResult(ThanhToanDto.PaymentResponse payment, boolean duplicate) {}

    public ThanhToanDto.TicketResponse ticket(String ticketId) {
        return transaction(em -> {
            NguoiDung caller = authenticate(em);
            VeXe ticket = em.find(VeXe.class, ticketId);
            if (ticket == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy vé.");
            ticket.donDatVe = (DonDatVe) org.hibernate.Hibernate.unproxy(ticket.donDatVe);
            lockOrderTrip(em, ticket.donDatVe.maDon, null);
            AccessControl.ticketAccess(em, caller, ticketId);
            ticket = em.find(VeXe.class, ticketId);
            long originalCount = em.createQuery("select count(v) from VeXe v where v.donDatVe = :order", Long.class)
                    .setParameter("order", ticket.donDatVe).getSingleResult();
            BigDecimal gross = ticket.donDatVe.tongTien.divide(BigDecimal.valueOf(originalCount), 0, RoundingMode.UNNECESSARY);
            BigDecimal net = ticket.giaVe == null ? BigDecimal.ZERO : ticket.giaVe;
            return new ThanhToanDto.TicketResponse(ticket.maVe, ticket.donDatVe.maDon, ticket.chuyenXe.maChuyen,
                    ticket.ghe.maGhe, ticket.hoTenKhachHang, ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach,
                    money(gross), money(gross.subtract(net).max(BigDecimal.ZERO)), money(net), VND,
                    ticket.maQR, offset(ticket.ngayXuatVe), ticket.trangThai);
        });
    }

    private BookingDto.Result finish(EntityManager em, ThanhToan payment, VNPayClient.VerifiedPayment verified,
                                     PaymentContextStore.Attempt context) {
        DonDatVe order = payment.donDatVe;
        List<GiuCho> holds = new GiuChoRepository(em).forOrder(order.maDon);
        LocalDateTime serverNow = local(clock.instant());
        LocalDateTime paidAt = local(verified.paidAt() == null ? clock.instant() : verified.paidAt());
        boolean expired = order.trangThai == TrangThaiDon.HET_HAN || holds.stream().anyMatch(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU && !serverNow.isBefore(h.thoiDiemHetHan));
        payment.trangThai = TrangThaiThanhToan.THANH_CONG;
        payment.thoiGianThanhToan = paidAt;
        if (expired && order.trangThai != TrangThaiDon.DA_THANH_TOAN) {
            order.trangThai = TrangThaiDon.HET_HAN;
            holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU)
                    .forEach(h -> h.trangThai = TrangThaiGiuCho.HET_HAN);
        }
        if (expired || context == null || context.passengers().isEmpty()) {
            return new BookingDto.Result(200, toPayment(payment, context, true));
        }
        // Collected money remains true even if the order was cancelled while the gateway was processing.
        if (order.trangThai != TrangThaiDon.CHO_THANH_TOAN || !order.danhSachVe.isEmpty())
            return new BookingDto.Result(200, toPayment(payment, context, true));
        List<GiuCho> active = holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU).toList();
        if (active.isEmpty()) return new BookingDto.Result(200, toPayment(payment, context, true));
        if (!context.maDon().equals(order.maDon)
                || !context.userId().equals(order.nguoiDat.maNguoiDung)
                || context.soTien().compareTo(payment.soTien) != 0
                || context.soTien().compareTo(payable(order)) != 0)
            throw new AccountException(409, "PAYMENT_CONTEXT_MISMATCH", "Dữ liệu giá hoặc chủ đơn không khớp giao dịch.");
        if (!new HashSet<>(context.maGhe()).equals(new HashSet<>(seatIds(active))))
            throw new AccountException(409, "PAYMENT_CONTEXT_MISMATCH", "Ghế giữ đã thay đổi; không thể phát hành vé.");
        validatePassengers(active, context.passengers());
        List<PriceLine> lines = frozenLines(order, active);
        if (!context.chiTietGia().isEmpty() && !context.chiTietGia().equals(lines))
            throw new AccountException(409, "PAYMENT_CONTEXT_MISMATCH", "Phân bổ giá không khớp giá đã chốt.");
        Set<String> seats = new HashSet<>(seatIds(active));
        ChuyenXe trip = active.getFirst().chuyenXe;
        boolean invalidSeat = active.stream().anyMatch(h -> trip.xe == null
                || !h.ghe.xe.maXe.equals(trip.xe.maXe) || h.ghe.trangThai != TrangThaiGhe.HOAT_DONG);
        long sold = em.createQuery("select count(v) from VeXe v where v.chuyenXe = :trip and v.ghe.maGhe in :seats and v.trangThai in :states", Long.class)
                .setParameter("trip", trip).setParameter("seats", seats)
                .setParameter("states", List.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE)).getSingleResult();
        long competingHolds = em.createQuery("select count(h) from GiuCho h where h.chuyenXe = :trip and h.ghe.maGhe in :seats "
                + "and h.trangThai = :state and h.thoiDiemHetHan > :now and (h.donDatVe is null or h.donDatVe <> :order)", Long.class)
                .setParameter("trip", trip).setParameter("seats", seats).setParameter("state", TrangThaiGiuCho.DANG_GIU)
                .setParameter("now", serverNow).setParameter("order", order).getSingleResult();
        if (invalidSeat || sold > 0 || competingHolds > 0
                || Set.of(TrangThaiChuyen.DA_HUY, TrangThaiChuyen.DANG_CHAY, TrangThaiChuyen.HOAN_TAT).contains(trip.trangThai))
            return new BookingDto.Result(200, toPayment(payment, context, true));

        KhuyenMai promotion = null;
        if (order.maKhuyenMaiDaApDung != null) {
            promotion = em.find(KhuyenMai.class, order.maKhuyenMaiDaApDung, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (promotion != null) em.refresh(promotion, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            long otherReservations = new DonDatVeRepository(em).reservedPromotionUses(order.maKhuyenMaiDaApDung, order.maDon, serverNow);
            if (promotion == null || promotion.soLuong - (long) promotion.soLuongDaDung - otherReservations <= 0)
                return new BookingDto.Result(200, toPayment(payment, context, true));
        }
        LocalDateTime issuedAt = local(clock.instant());
        if (active.stream().anyMatch(h -> !issuedAt.isBefore(h.thoiDiemHetHan))) {
            order.trangThai = TrangThaiDon.HET_HAN;
            active.forEach(h -> h.trangThai = TrangThaiGiuCho.HET_HAN);
            return new BookingDto.Result(200, toPayment(payment, context, true));
        }
        Map<String, PriceLine> bySeat = new HashMap<>();
        lines.forEach(line -> bySeat.put(line.maGhe(), line));
        for (GiuCho hold : active) {
            PassengerRequest passenger = context.passengers().stream().filter(p -> p.maGhe().equals(hold.ghe.maGhe)).findFirst().orElseThrow();
            PriceLine line = bySeat.get(hold.ghe.maGhe);
            VeXe ticket = new VeXe();
            ticket.maVe = "TKT-" + UUID.randomUUID();
            ticket.chuyenXe = hold.chuyenXe;
            ticket.ghe = hold.ghe;
            ticket.donDatVe = order;
            ticket.hoTenKhachHang = passenger.hoTen();
            ticket.soDienThoaiKhachHang = passenger.soDienThoai();
            ticket.cccdHanhKhach = passenger.cccd();
            ticket.giaVe = new BigDecimal(line.thanhTien());
            ticket.ngayXuatVe = issuedAt;
            ticket.trangThai = TrangThaiVe.DA_XUAT_VE;
            ticket.maQR = UUID.randomUUID().toString();
            em.persist(ticket);
            hold.trangThai = TrangThaiGiuCho.DA_XAC_NHAN;
        }
        order.trangThai = TrangThaiDon.DA_THANH_TOAN;
        if (promotion != null) promotion.soLuongDaDung++;
        em.flush();
        return new BookingDto.Result(200, toPayment(payment, context, false));
    }

    private ThanhToanDto.PaymentResponse toPayment(ThanhToan payment, PaymentContextStore.Attempt context, boolean reconcile) {
        return toPayment(payment, context, (String) null, reconcile);
    }

    private ThanhToanDto.PaymentResponse toPayment(ThanhToan payment, PaymentContextStore.Attempt context, String url, boolean reconcile) {
        String actualUrl = url == null && context != null ? context.urlThanhToan() : url;
        if (payment.trangThai != TrangThaiThanhToan.CHO_XU_LY
                || payment.donDatVe.trangThai != TrangThaiDon.CHO_THANH_TOAN
                || context == null || !clock.instant().isBefore(context.expiresAt())) actualUrl = null;
        return new ThanhToanDto.PaymentResponse(payment.maGiaoDich, payment.donDatVe.maDon,
                payment.phuongThucThanhToan.maPhuongThuc, VND, money(payment.soTien), payment.trangThai,
                payment.trangThai == TrangThaiThanhToan.CHO_XU_LY ? false : true,
                context == null ? null : offset(context.createdAt()), actualUrl,
                context == null ? null : offset(context.expiresAt()), offset(payment.thoiGianThanhToan),
                reconcile || (payment.trangThai == TrangThaiThanhToan.THANH_CONG
                        && payment.donDatVe.trangThai != TrangThaiDon.DA_THANH_TOAN));
    }

    private ThanhToanDto.PaymentResponse getOwnedPayment(String id) {
        return transaction(em -> {
            NguoiDung caller = authenticate(em);
            AccessControl.role(caller, LoaiTaiKhoan.KHACH_HANG);
            ThanhToan payment = new ThanhToanRepository(em).find(id);
            if (payment == null) throw notFound();
            lockOrderTrip(em, payment.donDatVe.maDon, caller);
            em.refresh(payment);
            return toPayment(payment, contexts.get(id), false);
        });
    }

    private OrderTrip lockOrderTrip(EntityManager em, String orderId, NguoiDung caller) {
        String id = required(orderId, "maDon");
        DonDatVe initial = em.find(DonDatVe.class, id);
        if (initial == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy đơn.");
        Set<String> trips = new HashSet<>(em.createQuery("select distinct h.chuyenXe.maChuyen from GiuCho h where h.donDatVe.maDon = :id", String.class)
                .setParameter("id", id).getResultList());
        trips.addAll(em.createQuery("select distinct v.chuyenXe.maChuyen from VeXe v where v.donDatVe.maDon = :id", String.class)
                .setParameter("id", id).getResultList());
        List<String> tripIds = new ArrayList<>(trips);
        if (tripIds.size() != 1) throw new AccountException(409, "INVALID_STATE_TRANSITION", "Đơn phải thuộc đúng một chuyến.");
        ChuyenXe initialTrip = em.find(ChuyenXe.class, tripIds.getFirst());
        String operatorId = initialTrip.tuyenXe.nhaXe == null ? null : initialTrip.tuyenXe.nhaXe.maNhaXe;
        if (operatorId != null) AccessControl.resource(em, NhaXe.class, operatorId);
        ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, tripIds.getFirst());
        DonDatVe order = AccessControl.resource(em, DonDatVe.class, id);
        if (caller != null) AccessControl.orderAccess(em, caller, id);
        List<GiuCho> holds = new GiuChoRepository(em).forOrder(id);
        holds.forEach(em::refresh);
        return new OrderTrip(order, trip, holds);
    }

    private void ensurePayable(DonDatVe order, List<GiuCho> holds) {
        LocalDateTime now = local(clock.instant());
        List<GiuCho> active = active(holds);
        if (order.trangThai == TrangThaiDon.HET_HAN
                || (order.trangThai == TrangThaiDon.CHO_THANH_TOAN
                && active.stream().anyMatch(h -> !now.isBefore(h.thoiDiemHetHan)))) {
            order.trangThai = TrangThaiDon.HET_HAN;
            active.forEach(h -> h.trangThai = TrangThaiGiuCho.HET_HAN);
            throw new ExpiredOrder();
        }
        if (order.trangThai != TrangThaiDon.CHO_THANH_TOAN)
            throw new AccountException(409, "INVALID_STATE_TRANSITION", "Đơn chưa chốt giá để thanh toán.");
        if (active.isEmpty()) throw new AccountException(409, "HOLD_EXPIRED", "Đơn không còn ghế đang giữ.");
    }

    private static void validatePassengers(List<GiuCho> holds, List<PassengerRequest> passengers) {
        Set<String> seats = new HashSet<>();
        holds.forEach(h -> { if (h.trangThai == TrangThaiGiuCho.DANG_GIU) seats.add(h.ghe.maGhe); });
        Set<String> supplied = new HashSet<>();
        for (PassengerRequest passenger : passengers == null ? List.<PassengerRequest>of() : passengers) {
            if (passenger == null || passenger.maGhe() == null || passenger.hoTen() == null || passenger.hoTen().isBlank()
                    || passenger.hoTen().length() > 120 || passenger.soDienThoai() == null || passenger.soDienThoai().isBlank()
                    || passenger.soDienThoai().length() > 20 || (passenger.cccd() != null && passenger.cccd().length() > 20)
                    || !supplied.add(passenger.maGhe()))
                throw new AccountException(409, "PASSENGER_REQUIRED", "Hành khách phải đủ và không trùng ghế.");
        }
        if (seats.isEmpty() || !seats.equals(supplied)) throw new AccountException(409, "PASSENGER_REQUIRED", "Thông tin hành khách chưa khớp các ghế giữ.");
    }

    private static List<PassengerRequest> copyPassengers(List<PassengerRequest> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<String> seatIds(List<GiuCho> holds) { return holds.stream().map(h -> h.ghe.maGhe).sorted().toList(); }

    private static List<PriceLine> frozenLines(DonDatVe order, List<GiuCho> holds) {
        List<GiuCho> active = holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU).sorted(Comparator.comparing(h -> h.ghe.maGhe)).toList();
        if (active.isEmpty()) return List.of();
        BigDecimal fare = order.tongTien.divide(BigDecimal.valueOf(active.size()), 0, RoundingMode.UNNECESSARY);
        BigDecimal discount = order.tienGiam == null ? BigDecimal.ZERO : order.tienGiam;
        return PriceCalculator.calculate(active.stream()
                        .map(hold -> new PriceCalculator.SeatPrice(hold.ghe.maGhe, fare.setScale(0, RoundingMode.UNNECESSARY)))
                .toList(), discount.setScale(0, RoundingMode.UNNECESSARY))
                .chiTietGia();
    }

    private NguoiDung authenticate(EntityManager em) { return AccessControl.authenticated(em, currentUser(), clock.instant()); }
    private CurrentUser currentUser() {
        RequestIdentity.Context context = RequestIdentity.get();
        if (context == null || context.user() == null) throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        return context.user();
    }

    private IdemContext prepareIdempotency(EntityManager em, CurrentUser caller, String orderId,
                                           ThanhToanDto.CreateRequest request, String key) {
        RequestIdentity.Context requestContext = RequestIdentity.get();
        String path = requestContext == null || requestContext.path() == null
                ? "/api/booking/" + orderId + "/thanh-toan" : requestContext.path();
        IdempotencyRepository.Scope scope = new IdempotencyRepository.Scope(caller.maNguoiDung(), "POST", path, key);
        IdempotencyRepository repository = new IdempotencyRepository(em);
        repository.lock(scope);
        String hash = IdempotencyRepository.hash(orderId + "|" + request.maPhuongThuc());
        return new IdemContext(repository, scope, hash);
    }

    private BookingDto.Result replay(EntityManager em, IdempotencyRepository.Saved saved) {
        try {
            JsonNode body = JSON.readTree(saved.body());
            String paymentId = body.path("maGiaoDich").asText();
            ThanhToan payment = new ThanhToanRepository(em).find(paymentId);
            if (payment != null && "VNPAY".equals(payment.phuongThucThanhToan.maPhuongThuc)) {
                PaymentContextStore.Attempt context = contexts.get(paymentId);
                if (context == null && payment.trangThai == TrangThaiThanhToan.CHO_XU_LY)
                    throw new AccountException(409, "PAYMENT_CONTEXT_MISSING", "Không còn dữ liệu thanh toán tạm; cần xử lý giao dịch hiện có.");
                if (context == null || payment.trangThai != TrangThaiThanhToan.CHO_XU_LY
                        || payment.donDatVe.trangThai != TrangThaiDon.CHO_THANH_TOAN
                        || !clock.instant().isBefore(context.expiresAt()))
                    ((com.fasterxml.jackson.databind.node.ObjectNode) body).putNull("urlThanhToan");
            }
            return new BookingDto.Result(saved.status(), body);
        } catch (AccountException error) {
            throw error;
        } catch (Exception error) {
            throw new AccountException(500, "INTERNAL_ERROR", "Không đọc được kết quả yêu cầu đã lưu.");
        }
    }

    private BookingDto.Result remember(EntityManager em, IdemContext context, BookingDto.Result result) {
        try {
            context.repository().save(context.scope(), context.hash(), result.status(),
                    JSON.writeValueAsString(result.body()), clock.instant());
            return result;
        } catch (Exception error) {
            throw new AccountException(500, "INTERNAL_ERROR", "Không lưu được kết quả yêu cầu.");
        }
    }

    private <T> T transaction(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try { T result = action.apply(em); em.flush(); tx.commit(); return result; }
            catch (ExpiredOrder expired) {
                tx.commit();
                throw new AccountException(409, "HOLD_EXPIRED", "Đơn đã hết thời gian giữ ghế.");
            }
            catch (RuntimeException error) { if (tx.isActive()) tx.rollback(); throw error; }
        }
    }

    private static TrangThaiThanhToan parseState(String state) {
        if (state == null || state.isBlank()) return null;
        try { return TrangThaiThanhToan.valueOf(state.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw AccountException.invalid("trangThai", "Trạng thái giao dịch không hợp lệ."); }
    }

    private static void requireKey(String key) { if (key == null || key.isBlank() || key.length() > 128 || !key.matches("[!-~]+")) throw AccountException.invalid("Idempotency-Key", "Cần Idempotency-Key hợp lệ."); }
    private static String required(String value, String field) { if (value == null || value.isBlank()) throw AccountException.invalid(field, "Trường bắt buộc."); return value.trim(); }
    private static BigDecimal payable(DonDatVe order) {
        BigDecimal value = Objects.requireNonNull(order.tongTien).subtract(Objects.requireNonNull(order.tienGiam));
        if (value.signum() < 0) throw new AccountException(409, "PRICE_CHANGED", "Giá đã chốt không hợp lệ.");
        return value.setScale(0, RoundingMode.UNNECESSARY);
    }
    private static LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value, ZONE); }
    private static OffsetDateTime offset(LocalDateTime value) { return value == null ? null : value.atZone(ZONE).toOffsetDateTime(); }
    private static OffsetDateTime offset(Instant value) { return value == null ? null : value.atZone(ZONE).toOffsetDateTime(); }
    private static String money(BigDecimal value) { return value == null ? "0" : value.setScale(0, RoundingMode.UNNECESSARY).toPlainString(); }
    private static AccountException notFound() { return new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy giao dịch."); }
    private static List<GiuCho> active(List<GiuCho> holds) { return holds.stream().filter(h -> h.trangThai == TrangThaiGiuCho.DANG_GIU).toList(); }
    private static Instant holdExpiry(List<GiuCho> holds) { return active(holds).stream().map(h -> h.thoiDiemHetHan.atZone(ZONE).toInstant()).min(Comparator.naturalOrder()).orElseThrow(); }
    private record OrderTrip(DonDatVe order, ChuyenXe trip, List<GiuCho> holds) {}
    private record IdemContext(IdempotencyRepository repository, IdempotencyRepository.Scope scope,
                               String hash) {}
    private static final class ExpiredOrder extends RuntimeException {}
}
