package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.ticket.TicketCode;
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
import com.datvexe.repository.ThanhToanRepository;
import com.datvexe.repository.VeXeRepository;
import com.datvexe.repository.PaymentGatewayMetadataRepository;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Điều phối thanh toán và phát hành vé; QueryDR được gọi ngoài transaction. */
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

    public ThanhToanService(EntityManagerFactory factory, Clock clock,
                            PaymentContextStore contexts, Gateway gateway) {
        this.factory = Objects.requireNonNull(factory);
        this.clock = Objects.requireNonNull(clock);
        this.contexts = Objects.requireNonNull(contexts);
        this.gateway = gateway;
    }

    /** Provider abstraction keeps the service independent from the VNPay HTTP client. */
    public interface Gateway {
        default String merchantCode() { return VNPayConfig.fromEnvironment().tmnCode(); }
        String payUrl(String reference, BigDecimal amount, Instant created, Instant expiry, String ip);
        VNPayClient.VerifiedPayment queryDr(String reference, BigDecimal expectedAmount, Instant originalCreateDate, String ip);
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
        };
    }

    public BookingDto.Result create(
            String orderId,
            ThanhToanDto.CreateRequest request,
            String idempotencyKey,
            List<PassengerRequest> passengers,
            String clientIp) {

        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128
                || !idempotencyKey.matches("[!-~]+")) {
            throw new DomainException(400, "Cần Idempotency-Key hợp lệ.");
        }
        if (request == null || request.maPhuongThuc() == null
                || request.maPhuongThuc().isBlank()) {
            throw new DomainException(400, "Phương thức thanh toán là bắt buộc.");
        }

        PaymentContextStore.Attempt attempt;
        BookingDto.Result result;

        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                // Kiểm tra yêu cầu gửi lặp.
                CurrentUser user = currentUser();
                RequestIdentity.Context requestContext = RequestIdentity.get();
                String path = "/api/booking/" + orderId + "/thanh-toan";
                if (requestContext != null && requestContext.path() != null) {
                    path = requestContext.path();
                }

                IdempotencyRepository idempotency = new IdempotencyRepository(em);
                IdempotencyRepository.Scope scope = new IdempotencyRepository.Scope(
                        user.maNguoiDung(), "POST", path, idempotencyKey);
                idempotency.lock(scope);
                String hash = IdempotencyRepository.hash(
                        orderId + "|" + request.maPhuongThuc());

                NguoiDung caller = AccessControl.authenticated(em, user, clock.instant());
                AccessControl.role(caller, LoaiTaiKhoan.KHACH_HANG);
                IdempotencyRepository.Saved saved = idempotency.find(scope, clock.instant());
                if (saved != null) {
                    if (!saved.hash().equals(hash)) {
                        throw new AccountException(409, "IDEMPOTENCY_KEY_REUSED",
                                "Khóa đã được dùng cho nội dung khác.");
                    }
                    result = replay(em, saved);
                    tx.commit();
                    return result;
                }

                // Kiểm tra đơn và giao dịch hiện có.
                OrderTrip pair = lockOrderTrip(em, orderId, caller);
                DonDatVe order = pair.order();
                ensurePayable(order, pair.holds());

                List<GiuCho> activeHolds = active(pair.holds());
                ThanhToanRepository payments = new ThanhToanRepository(em);
                if (payments.hasCollectedForOrder(order.maDon)) {
                    throw new AccountException(409, "PAYMENT_PROCESSING",
                            "Đơn đã có giao dịch thành công cần xử lý, không tạo lần thu tiền mới.");
                }

                ThanhToan pending = payments.pendingForOrder(order.maDon);
                if (pending != null) {
                    PaymentContextStore.Attempt pendingContext = contexts.get(pending.maGiaoDich);
                    if (pendingContext == null && "VNPAY".equalsIgnoreCase(
                            pending.phuongThucThanhToan.maPhuongThuc)) {
                        throw new AccountException(409, "PAYMENT_CONTEXT_MISSING",
                                "Không còn dữ liệu thanh toán tạm; cần đối soát giao dịch hiện có.");
                    }
                    result = new BookingDto.Result(202, toPayment(pending, pendingContext, false));
                    remember(idempotency, scope, hash, result);
                    tx.commit();
                    return result;
                }

                // Kiểm tra phương thức, hành khách và số tiền.
                String methodId = request.maPhuongThuc().trim().toUpperCase(Locale.ROOT);
                PhuongThucThanhToan method = payments.findMethod(methodId);
                if (method == null) {
                    throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED",
                            "Phương thức thanh toán chưa được hỗ trợ.");
                }
                if (method.trangThai != TrangThaiPhuongThuc.HOAT_DONG) {
                    throw new AccountException(409, "PAYMENT_METHOD_INACTIVE",
                            "Phương thức thanh toán hiện không khả dụng.");
                }
                if (!Set.of("VNPAY", "MIEN_PHI").contains(method.maPhuongThuc)) {
                    throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED",
                            "Phương thức này chưa được tích hợp.");
                }

                boolean freePayment = "MIEN_PHI".equals(method.maPhuongThuc);
                BigDecimal amount = payable(order);
                List<PassengerRequest> passengerList = passengers == null ? List.of() : passengers;
                validatePassengers(activeHolds, passengerList);

                if (freePayment && amount.signum() != 0) {
                    throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED",
                            "Đơn có tiền không thể dùng phương thức miễn phí.");
                }
                if (!freePayment && gateway == null) {
                    throw new AccountException(503, "PAYMENT_NOT_CONFIGURED",
                            "Cổng thanh toán hiện chưa được cấu hình.");
                }

                // Chuẩn bị dữ liệu thanh toán.
                String reference = "PAY-" + UUID.randomUUID();
                Instant created = clock.instant();
                Instant expiry = activeHolds.stream()
                        .map(hold -> hold.thoiDiemHetHan.atZone(ZONE).toInstant())
                        .min(Comparator.naturalOrder())
                        .orElseThrow();

                List<String> seats = seatIds(activeHolds);
                List<PriceLine> priceLines = PriceCalculator.calculateFromFrozenTotals(
                        seats, order.tongTien, order.tienGiam).chiTietGia();

                String url = null;
                if (!freePayment) {
                    url = gateway.payUrl(reference, amount, created, expiry, clientIp);
                }
                attempt = new PaymentContextStore.Attempt(
                        reference, order.maDon, caller.maNguoiDung,
                        method.maPhuongThuc, amount, created, expiry,
                        created.plus(PaymentContextStore.RETENTION),
                        clientIp, url, seats, passengerList, priceLines);

                // Thời gian có thể đã trôi qua trong lúc chuẩn bị thanh toán.
                ensurePayable(order, pair.holds());

                ThanhToan payment = new ThanhToan();
                payment.maGiaoDich = reference;
                payment.donDatVe = order;
                payment.phuongThucThanhToan = method;
                payment.soTien = amount;
                payment.noiDungThanhToan = "maDon=" + order.maDon
                        + ";phuongThuc=" + method.maPhuongThuc;
                payment.trangThai = TrangThaiThanhToan.CHO_XU_LY;
                payments.save(payment);
                em.flush();

                if (!freePayment) {
                    new PaymentGatewayMetadataRepository(em).save(
                            reference, gateway.merchantCode(), created);
                }

                ThanhToanDto.PaymentResponse paymentResult;
                if (freePayment) {
                    VNPayClient.VerifiedPayment verified = new VNPayClient.VerifiedPayment(
                            reference, 0, "00", "00", created, "FREE");
                    paymentResult = finish(em, payment, verified, attempt);
                    if (paymentResult.canDoiSoat()) {
                        payments.delete(payment);
                        if (order.trangThai == TrangThaiDon.HET_HAN) {
                            throw new ExpiredOrder();
                        }
                        throw new AccountException(409, "INVALID_STATE_TRANSITION",
                                "Đơn không còn đủ điều kiện phát hành vé miễn phí.");
                    }
                } else {
                    paymentResult = toPayment(payment, attempt, false);
                }

                result = new BookingDto.Result(201, paymentResult);
                remember(idempotency, scope, hash, result);
                tx.commit();
            } catch (ExpiredOrder expired) {
                tx.commit();
                throw new AccountException(409, "HOLD_EXPIRED", "Đơn đã hết thời gian giữ ghế.");
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }

        // Chỉ lưu context khi dữ liệu DB đã commit thành công.
        contexts.put(attempt);
        return result;
    }

    public ThanhToanDto.PaymentResponse get(String id) {
        return readPayment(id, false);
    }

    public ThanhToanDto.PageResponse history(String orderId, int page, int size, String state, String sort) {
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new DomainException(400, "page >= 1 và size trong khoảng 1..100, không vượt giới hạn phân trang.");
        }
        if (sort != null && !sort.isBlank()
                && !Set.of("maGiaoDich", "maGiaoDich,asc", "maGiaoDich,desc").contains(sort)) {
            throw new DomainException(400, "Chỉ hỗ trợ sắp xếp theo maGiaoDich.");
        }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung caller = authenticate(em);
                lockOrderTrip(em, orderId, caller);
                TrangThaiThanhToan filter = null;
                if (state != null && !state.isBlank()) {
                    try {
                        filter = TrangThaiThanhToan.valueOf(state.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException error) {
                        throw new DomainException(400, "Trạng thái giao dịch không hợp lệ.");
                    }
                }
                ThanhToanRepository repository = new ThanhToanRepository(em);
                long total = repository.countForOrder(orderId, filter);
                boolean descending = sort == null || sort.isBlank() || "maGiaoDich,desc".equals(sort);
                List<ThanhToanDto.PaymentResponse> items = repository.pageForOrder(orderId, filter, (page - 1) * size, size, descending)
                        .stream().map(payment -> toPayment(payment, contexts.get(payment.maGiaoDich), false)).toList();
                var response = new ThanhToanDto.PageResponse(items, page, size, total,
                        (int) Math.ceil(total / (double) size));
                tx.commit();
                return response;
            } catch (RuntimeException error) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw error;
            }
        }
    }

    /** Đọc giao dịch có kiểm tra quyền trước, rồi mới gọi QueryDR ngoài transaction. */
    public ThanhToanDto.PaymentResponse verify(String id, String clientIp) {
        ThanhToanDto.PaymentResponse current = readPayment(id, true);
        if (current.trangThai() != TrangThaiThanhToan.CHO_XU_LY) {
            return current;
        }

        PaymentContextStore.Attempt context = contexts.get(id);
        if (gateway == null) {
            throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Cổng thanh toán hiện chưa được cấu hình.");
        }
        if (context == null) {
            throw new AccountException(409, "PAYMENT_CONTEXT_MISSING", "Không còn dữ liệu tạm để xác minh giao dịch; cần đối soát.");
        }

        BigDecimal amount = new BigDecimal(current.soTien());
        VNPayClient.VerifiedPayment verified = gateway.queryDr(id, amount, context.createdAt(), clientIp);
        return receive(verified).payment();
    }

    /** Called only after the controller/provider adapter has verified the callback signature. */
    public ProcessingResult receive(VNPayClient.VerifiedPayment verified) {
        if (verified == null || verified.reference() == null) {
            throw new AccountException(400, "INVALID_PAYMENT_RESULT", "Thiếu kết quả thanh toán từ cổng.");
        }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                ThanhToanRepository repository = new ThanhToanRepository(em);
                ThanhToan raw = repository.find(verified.reference());
                if (raw == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy giao dịch.");
                }
                lockOrderTrip(em, raw.donDatVe.maDon, null);
                ThanhToan payment = repository.lockById(verified.reference());
                if (payment == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy giao dịch.");
                }
                if (!"VNPAY".equals(payment.phuongThucThanhToan.maPhuongThuc)) {
                    throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED", "Giao dịch này không thuộc cổng VNPay.");
                }

                PaymentContextStore.Attempt context = contexts.get(payment.maGiaoDich);
                long expectedAmount = payment.soTien.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
                if (verified.amountVnd() != expectedAmount) {
                    throw new AccountException(409, "PAYMENT_AMOUNT_MISMATCH", "Số tiền từ cổng không khớp đơn.");
                }
                boolean duplicate = payment.trangThai != TrangThaiThanhToan.CHO_XU_LY;
                ProcessingResult response;
                if (duplicate) {
                    response = new ProcessingResult(toPayment(payment, context, false), true);
                } else {
                    boolean success = "00".equals(verified.responseCode())
                            && "00".equals(verified.transactionStatus());
                    boolean failed = "02".equals(verified.transactionStatus());
                    if (success) {
                        ThanhToanDto.PaymentResponse result = finish(em, payment, verified, context);
                        response = new ProcessingResult(result, false);
                    } else {
                        if (failed) {
                            payment.trangThai = TrangThaiThanhToan.THAT_BAI;
                        }
                        response = new ProcessingResult(toPayment(payment, context, false), false);
                    }
                }
                tx.commit();
                return response;
            } catch (RuntimeException error) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw error;
            }
        }
    }

    public record ProcessingResult(ThanhToanDto.PaymentResponse payment, boolean duplicate) {}

    private ThanhToanDto.PaymentResponse finish(EntityManager em, ThanhToan payment, VNPayClient.VerifiedPayment verified,
                                     PaymentContextStore.Attempt context) {
        DonDatVe order = payment.donDatVe;
        ThanhToanRepository repository = new ThanhToanRepository(em);
        List<GiuCho> holds = repository.holdsForOrder(order.maDon);
        List<GiuCho> activeHolds = active(holds);
        LocalDateTime serverNow = local(clock.instant());
        LocalDateTime paidAt = local(verified.paidAt() == null ? clock.instant() : verified.paidAt());
        boolean expired = order.trangThai == TrangThaiDon.HET_HAN
                || hasExpiredHold(activeHolds, serverNow);

        payment.trangThai = TrangThaiThanhToan.THANH_CONG;
        payment.thoiGianThanhToan = paidAt;
        if ("VNPAY".equals(payment.phuongThucThanhToan.maPhuongThuc)) {
            new PaymentGatewayMetadataRepository(em).confirmed(payment.maGiaoDich, verified.providerTransaction());
        }
        if (expired && order.trangThai != TrangThaiDon.DA_THANH_TOAN) {
            expireOrder(order, activeHolds);
        }
        if (expired || context == null || context.passengers().isEmpty()) {
            return toPayment(payment, context, true);
        }
        // Vẫn ghi nhận tiền đã thu nếu đơn bị hủy trong lúc cổng đang xử lý.
        if (order.trangThai != TrangThaiDon.CHO_THANH_TOAN || !order.danhSachVe.isEmpty()) {
            return toPayment(payment, context, true);
        }
        if (activeHolds.isEmpty()) {
            return toPayment(payment, context, true);
        }
        if (!context.maDon().equals(order.maDon)
                || !context.userId().equals(order.nguoiDat.maNguoiDung)
                || context.soTien().compareTo(payment.soTien) != 0
                || context.soTien().compareTo(payable(order)) != 0) {
            return toPayment(payment, context, true);
        }

        Set<String> seats = new HashSet<>(seatIds(activeHolds));
        if (!new HashSet<>(context.maGhe()).equals(seats)) {
            return toPayment(payment, context, true);
        }

        // Context do backend tạo đã được kiểm tra khi tạo thanh toán.
        // Chỉ kiểm tra đủ dữ liệu cho đúng tập ghế trước khi phát hành.
        Map<String, PassengerRequest> passengersBySeat = new HashMap<>();
        for (PassengerRequest passenger : context.passengers()) {
            if (passenger == null || passenger.maGhe() == null
                    || passenger.hoTen() == null || passenger.soDienThoai() == null
                    || passengersBySeat.put(passenger.maGhe(), passenger) != null) {
                return toPayment(payment, context, true);
            }
        }
        if (!passengersBySeat.keySet().equals(seats)) {
            return toPayment(payment, context, true);
        }

        Map<String, PriceLine> pricesBySeat = new HashMap<>();
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (PriceLine line : context.chiTietGia()) {
            if (line == null || line.maGhe() == null || line.thanhTien() == null
                    || pricesBySeat.put(line.maGhe(), line) != null) {
                return toPayment(payment, context, true);
            }
            totalPaid = totalPaid.add(new BigDecimal(line.thanhTien()));
        }
        if (!pricesBySeat.keySet().equals(seats)
                || totalPaid.compareTo(payment.soTien) != 0) {
            return toPayment(payment, context, true);
        }

        ChuyenXe trip = activeHolds.getFirst().chuyenXe;
        boolean invalidSeat = false;
        for (GiuCho hold : activeHolds) {
            if (trip.xe == null || !hold.ghe.xe.maXe.equals(trip.xe.maXe)
                    || hold.ghe.trangThai != TrangThaiGhe.HOAT_DONG) {
                invalidSeat = true;
                break;
            }
        }
        VeXeRepository tickets = new VeXeRepository(em);
        long sold = tickets.countSoldForTripSeats(trip.maChuyen, seats);
        long competingHolds = repository.countCompetingHolds(trip.maChuyen, seats, order.maDon, serverNow);
        boolean tripClosed = Set.of(TrangThaiChuyen.DA_HUY, TrangThaiChuyen.DANG_CHAY,
                TrangThaiChuyen.HOAN_TAT).contains(trip.trangThai);
        if (invalidSeat || sold > 0 || competingHolds > 0 || tripClosed) {
            return toPayment(payment, context, true);
        }

        KhuyenMai promotion = null;
        if (order.maKhuyenMaiDaApDung != null) {
            promotion = repository.lockPromotion(order.maKhuyenMaiDaApDung);
            long otherReservations = new DonDatVeRepository(em).reservedPromotionUses(order.maKhuyenMaiDaApDung, order.maDon, serverNow);
            if (promotion == null || promotion.soLuong - (long) promotion.soLuongDaDung - otherReservations <= 0) {
                return toPayment(payment, context, true);
            }
        }
        LocalDateTime issuedAt = local(clock.instant());
        if (hasExpiredHold(activeHolds, issuedAt)) {
            expireOrder(order, activeHolds);
            return toPayment(payment, context, true);
        }

        for (GiuCho hold : activeHolds) {
            PassengerRequest passenger = passengersBySeat.get(hold.ghe.maGhe);
            PriceLine line = pricesBySeat.get(hold.ghe.maGhe);
            VeXe ticket = new VeXe();
            ticket.maVe = "TKT-" + UUID.randomUUID();
            ticket.maTraCuu = TicketCode.next(em);
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
            tickets.save(ticket);
            hold.trangThai = TrangThaiGiuCho.DA_XAC_NHAN;
        }
        order.trangThai = TrangThaiDon.DA_THANH_TOAN;
        if (promotion != null) {
            promotion.soLuongDaDung++;
        }
        em.flush();
        com.datvexe.email.ReceiptQueue.enqueue(em, order, payment);
        return toPayment(payment, context, false);
    }

    private ThanhToanDto.PaymentResponse toPayment(ThanhToan payment, PaymentContextStore.Attempt context, boolean reconcile) {
        String actualUrl = context == null ? null : context.urlThanhToan();
        if (payment.trangThai != TrangThaiThanhToan.CHO_XU_LY
                || payment.donDatVe.trangThai != TrangThaiDon.CHO_THANH_TOAN
                || context == null || !clock.instant().isBefore(context.expiresAt())) {
            actualUrl = null;
        }

        boolean resultKnown = payment.trangThai != TrangThaiThanhToan.CHO_XU_LY;
        boolean collected = payment.trangThai == TrangThaiThanhToan.THANH_CONG
                || payment.trangThai == TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN
                || payment.trangThai == TrangThaiThanhToan.DA_HOAN_TIEN;
        boolean needsReconciliation = reconcile
                || (collected && payment.donDatVe.trangThai != TrangThaiDon.DA_THANH_TOAN);

        return new ThanhToanDto.PaymentResponse(payment.maGiaoDich, payment.donDatVe.maDon,
                payment.phuongThucThanhToan.maPhuongThuc, VND, money(payment.soTien), payment.trangThai,
                resultKnown,
                context == null ? null : offset(context.createdAt()), actualUrl,
                context == null ? null : offset(context.expiresAt()), offset(payment.thoiGianThanhToan),
                needsReconciliation);
    }

    private ThanhToanDto.PaymentResponse readPayment(String id, boolean customerOnly) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung caller = authenticate(em);
                if (customerOnly) {
                    AccessControl.role(caller, LoaiTaiKhoan.KHACH_HANG);
                }
                ThanhToan payment = new ThanhToanRepository(em).find(id);
                if (payment == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy giao dịch.");
                }
                lockOrderTrip(em, payment.donDatVe.maDon, caller);
                payment = new ThanhToanRepository(em).lockById(id);
                var response = toPayment(payment, contexts.get(payment.maGiaoDich), false);
                tx.commit();
                return response;
            } catch (RuntimeException error) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw error;
            }
        }
    }

    private OrderTrip lockOrderTrip(EntityManager em, String orderId, NguoiDung caller) {
        if (orderId == null || orderId.isBlank()) {
            throw new DomainException(400, "Mã đơn là bắt buộc.");
        }
        String id = orderId.trim();
        ThanhToanRepository repository = new ThanhToanRepository(em);
        DonDatVe initial = repository.findOrder(id);
        if (initial == null) {
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy đơn.");
        }
        Set<String> trips = new HashSet<>(repository.tripIdsFromHolds(id));
        trips.addAll(new VeXeRepository(em).tripIdsForOrder(id));
        if (trips.size() != 1) {
            throw new AccountException(409, "INVALID_STATE_TRANSITION", "Đơn phải thuộc đúng một chuyến.");
        }
        String tripId = trips.iterator().next();
        ChuyenXe initialTrip = repository.findTrip(tripId);
        String operatorId = initialTrip.tuyenXe.nhaXe == null ? null : initialTrip.tuyenXe.nhaXe.maNhaXe;
        if (operatorId != null) {
            AccessControl.resource(em, NhaXe.class, operatorId);
        }
        AccessControl.resource(em, ChuyenXe.class, tripId);
        DonDatVe order;
        if (caller != null) {
            AccessControl.orderAccess(em, caller, id);
            order = repository.findOrder(id);
        } else {
            order = AccessControl.resource(em, DonDatVe.class, id);
        }
        List<GiuCho> holds = repository.refreshedHoldsForOrder(id);
        return new OrderTrip(order, holds);
    }

    private void ensurePayable(DonDatVe order, List<GiuCho> holds) {
        LocalDateTime now = local(clock.instant());
        List<GiuCho> activeHolds = active(holds);
        boolean expired = order.trangThai == TrangThaiDon.HET_HAN
                || (order.trangThai == TrangThaiDon.CHO_THANH_TOAN
                    && hasExpiredHold(activeHolds, now));
        if (expired) {
            expireOrder(order, activeHolds);
            throw new ExpiredOrder();
        }
        if (order.trangThai != TrangThaiDon.CHO_THANH_TOAN) {
            throw new AccountException(409, "INVALID_STATE_TRANSITION", "Đơn chưa chốt giá để thanh toán.");
        }
        if (activeHolds.isEmpty()) {
            throw new AccountException(409, "HOLD_EXPIRED", "Đơn không còn ghế đang giữ.");
        }
    }

    private static boolean hasExpiredHold(List<GiuCho> holds, LocalDateTime now) {
        for (GiuCho hold : holds) {
            if (hold.trangThai == TrangThaiGiuCho.DANG_GIU
                    && !now.isBefore(hold.thoiDiemHetHan)) {
                return true;
            }
        }
        return false;
    }

    private static void expireOrder(DonDatVe order, List<GiuCho> holds) {
        order.trangThai = TrangThaiDon.HET_HAN;
        for (GiuCho hold : holds) {
            if (hold.trangThai == TrangThaiGiuCho.DANG_GIU) {
                hold.trangThai = TrangThaiGiuCho.HET_HAN;
            }
        }
    }

    private static void validatePassengers(List<GiuCho> holds, List<PassengerRequest> passengers) {
        Set<String> seats = new HashSet<>();
        for (GiuCho hold : holds) {
            if (hold.trangThai == TrangThaiGiuCho.DANG_GIU) {
                seats.add(hold.ghe.maGhe);
            }
        }

        Set<String> supplied = new HashSet<>();
        String message = "Hành khách phải đủ và không trùng ghế.";
        if (passengers == null) {
            passengers = List.of();
        }
        for (PassengerRequest passenger : passengers) {
            if (passenger == null || passenger.maGhe() == null) {
                throw new AccountException(409, "PASSENGER_REQUIRED", message);
            }

            String name = passenger.hoTen();
            if (name == null || name.isBlank() || name.length() > 120) {
                throw new AccountException(409, "PASSENGER_REQUIRED", message);
            }
            String phone = passenger.soDienThoai();
            if (phone == null || phone.isBlank() || phone.length() > 20) {
                throw new AccountException(409, "PASSENGER_REQUIRED", message);
            }
            String identityNumber = passenger.cccd();
            if (identityNumber != null && identityNumber.length() > 20) {
                throw new AccountException(409, "PASSENGER_REQUIRED", message);
            }
            if (!supplied.add(passenger.maGhe())) {
                throw new AccountException(409, "PASSENGER_REQUIRED", message);
            }
        }
        if (seats.isEmpty() || !seats.equals(supplied)) {
            throw new AccountException(409, "PASSENGER_REQUIRED", "Thông tin hành khách chưa khớp các ghế giữ.");
        }
    }

    private static List<String> seatIds(List<GiuCho> holds) {
        return holds.stream().map(hold -> hold.ghe.maGhe).sorted().toList();
    }

    private NguoiDung authenticate(EntityManager em) {
        return AccessControl.authenticated(em, currentUser(), clock.instant());
    }

    private CurrentUser currentUser() {
        RequestIdentity.Context context = RequestIdentity.get();
        if (context == null || context.user() == null) {
            throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        }
        return context.user();
    }

    private BookingDto.Result replay(EntityManager em, IdempotencyRepository.Saved saved) {
        try {
            JsonNode body = JSON.readTree(saved.body());
            String paymentId = body.path("maGiaoDich").asText();
            ThanhToan payment = new ThanhToanRepository(em).find(paymentId);
            if (payment != null && "VNPAY".equals(payment.phuongThucThanhToan.maPhuongThuc)) {
                PaymentContextStore.Attempt context = contexts.get(paymentId);
                if (context == null && payment.trangThai == TrangThaiThanhToan.CHO_XU_LY) {
                    throw new AccountException(409, "PAYMENT_CONTEXT_MISSING", "Không còn dữ liệu thanh toán tạm; cần xử lý giao dịch hiện có.");
                }
                if (context == null || payment.trangThai != TrangThaiThanhToan.CHO_XU_LY
                        || payment.donDatVe.trangThai != TrangThaiDon.CHO_THANH_TOAN
                        || !clock.instant().isBefore(context.expiresAt())) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) body).putNull("urlThanhToan");
                }
            }
            return new BookingDto.Result(saved.status(), body);
        } catch (AccountException error) {
            throw error;
        } catch (Exception error) {
            throw new AccountException(500, "INTERNAL_ERROR", "Không đọc được kết quả yêu cầu đã lưu.");
        }
    }

    private BookingDto.Result remember(IdempotencyRepository repository,
                                       IdempotencyRepository.Scope scope, String hash,
                                       BookingDto.Result result) {
        String body;
        try {
            body = JSON.writeValueAsString(result.body());
        } catch (JsonProcessingException error) {
            throw new AccountException(500, "INTERNAL_ERROR", "Không chuyển được kết quả yêu cầu sang JSON.");
        }
        repository.save(scope, hash, result.status(), body, clock.instant());
        return result;
    }

    private static BigDecimal payable(DonDatVe order) {
        BigDecimal value = Objects.requireNonNull(order.tongTien).subtract(Objects.requireNonNull(order.tienGiam));
        if (value.signum() < 0) {
            throw new AccountException(409, "PRICE_CHANGED", "Giá đã chốt không hợp lệ.");
        }
        return value.setScale(0, RoundingMode.UNNECESSARY);
    }
    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZONE);
    }

    private static OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0" : value.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static List<GiuCho> active(List<GiuCho> holds) {
        return holds.stream().filter(hold -> hold.trangThai == TrangThaiGiuCho.DANG_GIU).toList();
    }
    private record OrderTrip(DonDatVe order, List<GiuCho> holds) {}
    private static final class ExpiredOrder extends RuntimeException {}
}
