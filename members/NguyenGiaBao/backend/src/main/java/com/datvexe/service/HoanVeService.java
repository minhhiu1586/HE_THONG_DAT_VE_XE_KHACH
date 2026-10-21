package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto;
import com.datvexe.dto.HoanVeDto.*;
import com.datvexe.payment.*;
import com.datvexe.repository.*;
import com.datvexe.repository.HoanVeRepository.Entry;
import com.datvexe.security.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Lưu yêu cầu trước khi gọi cổng; xác nhận hoàn và cập nhật vé trong transaction riêng. */
public final class HoanVeService {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Set<TrangThaiThanhToan> CAPTURED = Set.of(TrangThaiThanhToan.THANH_CONG,
            TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN, TrangThaiThanhToan.DA_HOAN_TIEN);
    private final EntityManagerFactory factory;
    private final Clock clock;
    private final Gateway gateway;

    public interface Gateway {
        String merchantCode();
        VNPayClient.RefundResult refund(VNPayClient.RefundCommand command);
        VNPayClient.RefundResult query(VNPayClient.RefundCommand command, String expectedProviderId);
    }

    public HoanVeService(EntityManagerFactory factory) {
        this(factory, Clock.systemUTC(), defaultGateway());
    }

    public HoanVeService(EntityManagerFactory factory, Clock clock, Gateway gateway) {
        this.factory = factory;
        this.clock = clock;
        this.gateway = gateway;
    }

    private static Gateway defaultGateway() {
        var config = VNPayConfig.fromEnvironment();
        var client = new VNPayClient(config);
        return new Gateway() {
            public String merchantCode() {
                return config.tmnCode();
            }
            public VNPayClient.RefundResult refund(VNPayClient.RefundCommand c) {
                return client.refund(c);
            }
            public VNPayClient.RefundResult query(VNPayClient.RefundCommand c, String id) {
                return client.queryRefund(c, id);
            }
        };
    }

    public Response get(String id) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                Entry entry = new HoanVeRepository(em).find(kiemTraMa(id));
                if (entry == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
                }
                khoaDon(em, entry.orderId(), user, false);
                var response = toResponse(entry);
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    public Page history(String ticketId, int page, int size, String sort) {
        if (page < 1 || size < 1 || size > 100 || ((long) page - 1) * size > Integer.MAX_VALUE) {
            throw AccountException.invalid("page", "Phân trang không hợp lệ.");
        }
        if (sort != null && !Set.of("thoiDiemTao,asc", "thoiDiemTao,desc").contains(sort)) {
            throw AccountException.invalid("sort", "Sort không hợp lệ.");
        }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                VeXe ticket = layVe(em, ticketId);
                DonDatVe order = khoaDon(em, ticket.donDatVe.maDon, user, false);
                List<Entry> values = new ArrayList<>();
                for (Entry entry : new HoanVeRepository(em).forOrder(order.maDon)) {
                    for (Line line : entry.lines()) {
                        if (line.maVe().equals(ticketId)) {
                            values.add(entry);
                            break;
                        }
                    }
                }
                if ("thoiDiemTao,desc".equals(sort)) {
                    Collections.reverse(values);
                }
                long total = values.size();
                int from = (int) Math.min((long) (page - 1) * size, total);
                int to = (int) Math.min((long) from + size, total);
                List<Response> items = new ArrayList<>();
                for (int i = from; i < to; i++) {
                    items.add(toResponse(values.get(i)));
                }
                Page response = new Page(items, page, size, total, (total + size - 1) / size);
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    public Preview preview(String orderId, Request input) {
        Request request = kiemTraRequest(input);
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                DonDatVe order = khoaDon(em, orderId, user, true);
                var response = tinhDuKienHoan(em, order, request.danhSachMaVe());
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    public PolicyResponse policy(String ticketId) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                VeXe initial = layVe(em, ticketId);
                DonDatVe order = khoaDon(em, initial.donDatVe.maDon, user, true);
                var policy = order.chinhSachHoanVe;
                boolean duocHoan = true;
                String soTienHoan = "0";
                String lyDo = null;
                try {
                    Preview preview = tinhDuKienHoan(em, order, List.of(ticketId));
                    soTienHoan = preview.soTienHoan();
                } catch (AccountException error) {
                    if (error.status() != 409) {
                        throw error;
                    }
                    duocHoan = false;
                    lyDo = error.getMessage();
                }
                var response = new PolicyResponse(ticketId,
                        policy == null ? null : com.datvexe.dto.ChinhSachHoanVeDto.view(policy),
                        duocHoan, soTienHoan, "VND", lyDo, thoiDiemHienTai());
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    public BookingDto.Result createTicket(String ticketId, SingleRequest input, String key, String ip) {
        if (input == null) {
            throw AccountException.invalid("body", "Body JSON là bắt buộc.");
        }
        String orderId;
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                VeXe ticket = layVe(em, ticketId);
                orderId = khoaDon(em, ticket.donDatVe.maDon, user, true).maDon;
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
        return create(orderId, new Request(List.of(ticketId), input.lyDo()), key, ip);
    }

    public BookingDto.Result create(String orderId, Request input, String key, String ip) {
        Request request = kiemTraRequest(input);
        kiemTraKey(key);
        Entry yeuCau;
        VNPayClient.RefundCommand lenh = null;
        BookingDto.Result accepted;

        // 1. Kiểm tra và lưu yêu cầu trong DB, chưa gọi cổng.
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                AccessControl.role(user, LoaiTaiKhoan.KHACH_HANG);
                Idem idem = taoIdempotency(em, user, key, json(request));
                var saved = idem.repository().find(idem.scope(), clock.instant());
                if (saved != null) {
                    BookingDto.Result result = docKetQuaDaLuu(saved, idem.hash());
                    tx.commit();
                    return result;
                }

                DonDatVe order = khoaDon(em, orderId, user, true);
                Preview duKien = tinhDuKienHoan(em, order, request.danhSachMaVe());
                ThanhToan payment = khoaThanhToan(em, duKien.maGiaoDichGoc());
                HoanVeRepository repository = new HoanVeRepository(em);
                BigDecimal amount = new BigDecimal(duKien.soTienHoan());
                boolean full = amount.compareTo(payment.soTien) == 0;
                yeuCau = new Entry(
                        "HT-" + UUID.randomUUID(), order.maDon, payment.maGiaoDich,
                        user.maNguoiDung, duKien.maChinhSach(), request.lyDo(), amount,
                        "CHO_XU_LY", duKien.chiTiet(),
                        List.of(new History("TAO_YEU_CAU", null, null, null, thoiDiemHienTai())),
                        UUID.randomUUID().toString().replace("-", ""),
                        null, full, null, clock.instant(), null);
                accepted = new BookingDto.Result(202, toResponse(yeuCau));
                if (amount.signum() > 0) {
                    var metadata = kiemTraMetadataCong(em, payment);
                    yeuCau = capNhatYeuCau(yeuCau, "CHO_XU_LY", null, null, "GUI_CONG", null, null);
                    lenh = taoLenhHoan(yeuCau, payment, metadata, ip);
                }
                repository.insert(yeuCau);
                idem.repository().save(idem.scope(), idem.hash(), 202, json(accepted.body()), clock.instant());
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }

        // 2. Gọi cổng ngoài transaction; GET cung cấp kết quả xử lý hiện tại.
        guiHoanTien(yeuCau, lenh);
        return accepted;
    }

    public Response verify(String id, String ip) {
        Entry yeuCau;
        VNPayClient.RefundCommand lenh = null;
        VNPayClient.RefundResult ketQua = null;

        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                HoanVeRepository repository = new HoanVeRepository(em);
                Entry raw = repository.find(kiemTraMa(id));
                if (raw == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy yêu cầu hoàn.");
                }
                khoaDon(em, raw.orderId(), user, false);
                ThanhToan payment = khoaThanhToan(em, raw.paymentId());
                Entry entry = repository.lock(id);
                if (entry == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy yêu cầu hoàn.");
                }
                if (!HoanVeRepository.BLOCKING.contains(entry.state())) {
                    Response response = toResponse(entry);
                    tx.commit();
                    return response;
                }

                yeuCau = entry;
                if (entry.amount().signum() == 0) {
                    ketQua = new VNPayClient.RefundResult(
                            entry.requestId(), entry.paymentId(), BigDecimal.ZERO,
                            "00", "00", "LOCAL", clock.instant(), true);
                } else {
                    History lanGuiCuoi = null;
                    History bangChung = null;
                    for (History history : entry.history()) {
                        if (!entry.requestId().equals(history.maRequestCong())) {
                            continue;
                        }
                        if ("GUI_CONG".equals(history.thaoTac())) {
                            lanGuiCuoi = history;
                        }
                        if ("DA_XAC_MINH_CONG".equals(history.thaoTac())
                                && Set.of("00/00", "00/05", "00/06").contains(history.maKetQua())) {
                            bangChung = history;
                        }
                    }
                    if (bangChung != null) {
                        // Xác minh thủ công có thể dùng bằng chứng đã lưu khi cập nhật vé từng lỗi.
                        ketQua = new VNPayClient.RefundResult(
                                entry.requestId(), entry.paymentId(), entry.amount(),
                                "00", "00", bangChung.maGiaoDichCong(), null, true);
                    } else {
                        if (lanGuiCuoi != null && clock.instant().isBefore(
                                lanGuiCuoi.thoiDiem().toInstant().plusSeconds(30))) {
                            throw new AccountException(409, "REQUEST_IN_PROGRESS",
                                    "Yêu cầu hoàn đang được gửi tới cổng.");
                        }
                        var metadata = kiemTraMetadataCong(em, payment);
                        yeuCau = capNhatYeuCau(entry, "CAN_DOI_SOAT", entry.providerId(),
                                "AWAITING_VERIFICATION", "XAC_MINH", null, null);
                        repository.update(yeuCau);
                        lenh = taoLenhHoan(yeuCau, payment, metadata, ip);
                    }
                }
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }

        try {
            // Chỉ truy vấn khi chưa có kết quả; tuyệt đối không gửi lại lệnh hoàn.
            if (ketQua == null) {
                ketQua = gateway.query(lenh, yeuCau.providerId());
            }
            luuBangChungCong(id, ketQua);
            return hoanTatYeuCau(id, ketQua);
        } catch (RuntimeException e) {
            return danhDauCanDoiSoat(id, yeuCau.requestId(), e);
        }
    }

    public BookingDto.Result retry(String id, String key, String ip) {
        kiemTraKey(key);
        Entry yeuCau;
        VNPayClient.RefundCommand lenh = null;
        BookingDto.Result accepted;

        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung user = xacThuc(em);
                AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
                Idem idem = taoIdempotency(em, user, key, "retry:" + kiemTraMa(id));
                var saved = idem.repository().find(idem.scope(), clock.instant());
                if (saved != null) {
                    BookingDto.Result result = docKetQuaDaLuu(saved, idem.hash());
                    tx.commit();
                    return result;
                }

                HoanVeRepository repository = new HoanVeRepository(em);
                Entry raw = repository.find(id);
                if (raw == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy yêu cầu hoàn.");
                }
                DonDatVe order = khoaDon(em, raw.orderId(), user, false);
                ThanhToan payment = khoaThanhToan(em, raw.paymentId());
                Entry entry = repository.lock(id);
                if (entry == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy yêu cầu hoàn.");
                }
                if (!"THAT_BAI".equals(entry.state())) {
                    throw new AccountException(409, "REFUND_RETRY_NOT_ALLOWED",
                            "Chỉ thử lại khoản hoàn đã xác định thất bại.");
                }
                List<String> ticketIds = new ArrayList<>();
                for (Line line : entry.lines()) {
                    ticketIds.add(line.maVe());
                }
                Preview duKien = tinhDuKienHoan(em, order, ticketIds);
                if (!duKien.maGiaoDichGoc().equals(entry.paymentId())
                        || !duKien.chiTiet().equals(entry.lines())) {
                    throw new AccountException(409, "REFUND_NOT_ALLOWED", "Dữ liệu hoàn đã thay đổi.");
                }
                yeuCau = new Entry(
                        entry.id(), entry.orderId(), entry.paymentId(), entry.userId(),
                        entry.policyId(), entry.reason(), entry.amount(), "CHO_XU_LY",
                        entry.lines(), entry.history(), UUID.randomUUID().toString().replace("-", ""), null,
                        entry.fullRefund(), null, entry.created(), null);
                yeuCau = capNhatYeuCau(yeuCau, "CHO_XU_LY", null, null, "THU_LAI", null, null);
                accepted = new BookingDto.Result(202, toResponse(yeuCau));
                if (yeuCau.amount().signum() > 0) {
                    var metadata = kiemTraMetadataCong(em, payment);
                    yeuCau = capNhatYeuCau(yeuCau, "CHO_XU_LY", null, null, "GUI_CONG", null, null);
                    lenh = taoLenhHoan(yeuCau, payment, metadata, ip);
                }
                repository.update(yeuCau);
                idem.repository().save(idem.scope(), idem.hash(), 202, json(accepted.body()), clock.instant());
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }

        guiHoanTien(yeuCau, lenh);
        return accepted;
    }

    // Hoàn do nhà xe hủy chuyến: giữ riêng luồng này để dễ tra cứu.
    public Response processTripCancellation(String orderId, String tripId, String reason, String ip) {
        Entry yeuCau;
        VNPayClient.RefundCommand lenh = null;
        boolean yeuCauMoi = false;
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                NguoiDung actor = xacThuc(em);
                AccessControl.role(actor, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
                ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, tripId);
                if (trip.trangThai != TrangThaiChuyen.DA_HUY) {
                    throw new AccountException(409, "TRIP_NOT_CANCELLED", "Chỉ xử lý hoàn sau khi chuyến đã hủy.");
                }
                AccessControl.operator(actor, trip.tuyenXe.nhaXe);
                DonDatVe order = AccessControl.resource(em, DonDatVe.class, orderId);
                HoanVeRepository repository = new HoanVeRepository(em);
                if (!repository.hasCancellationWork(orderId, tripId)) {
                    throw new AccountException(409, "CANCELLATION_WORK_NOT_FOUND",
                            "Không tìm thấy công việc xử lý của đơn bị ảnh hưởng.");
                }
                List<VeXe> tickets = new ArrayList<>();
                for (VeXe ticket : new VeXeRepository(em).forOrder(orderId)) {
                    if (ticket.chuyenXe.maChuyen.equals(tripId)
                            && Set.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE).contains(ticket.trangThai)) {
                        tickets.add(ticket);
                    }
                }
                boolean mixedOrder = repository.hasOtherTripData(orderId, tripId);
                if (mixedOrder) {
                    throw new AccountException(409, "CANCELLATION_PAYMENT_RECONCILIATION_REQUIRED",
                            "Đơn gồm vé của nhiều chuyến; cần đối soát riêng trước khi hoàn.");
                }
                long anyTickets = repository.countTicketsForTrip(orderId, tripId);
                boolean capturedWithoutTickets = tickets.isEmpty() && anyTickets == 0
                        && repository.countHoldsForTrip(orderId, tripId) > 0;
                if (tickets.isEmpty() && !capturedWithoutTickets) {
                    throw new AccountException(409, "CANCELLATION_REFUND_NOT_REQUIRED",
                            "Đơn không còn vé đã phát hành hoặc khoản giữ chỗ cần hoàn.");
                }
                List<ThanhToan> captured = new ArrayList<>();
                for (ThanhToan value : new ThanhToanRepository(em).forOrder(orderId)) {
                    if (CAPTURED.contains(value.trangThai)) {
                        captured.add(value);
                    }
                }
                if (captured.size() != 1) {
                    throw new AccountException(409, "CANCELLATION_PAYMENT_RECONCILIATION_REQUIRED",
                            "Chưa xác định được duy nhất khoản đã thu; cần đối soát.");
                }
                ThanhToan payment = khoaThanhToan(em, captured.getFirst().maGiaoDich);
                List<Entry> previous = repository.forOrder(orderId);
                Entry existing = null;
                Entry completed = null;
                for (Entry value : previous) {
                    if (value.paymentId().equals(payment.maGiaoDich) && Objects.toString(value.reason(),
                            "").startsWith("NHA_XE_HUY_CHUYEN:")) {
                        if (existing == null && HoanVeRepository.BLOCKING.contains(value.state())) {
                            existing = value;
                        }
                        if (completed == null && value.state().equals("THANH_CONG")) {
                            completed = value;
                        }
                    }
                }
                if (existing != null) {
                    yeuCau = existing;
                } else if (capturedWithoutTickets && completed != null) {
                    yeuCau = completed;
                } else {
                    for (Entry value : previous) {
                        if (value.paymentId().equals(payment.maGiaoDich)
                                && HoanVeRepository.BLOCKING.contains(value.state())) {
                            throw new AccountException(409, "REFUND_IN_PROGRESS",
                                    "Đơn đang có yêu cầu hoàn khác; cần xử lý trước để tránh hoàn trùng.");
                        }
                    }
                    if (!Boolean.parseBoolean(System.getProperty("trip.cancellation.refund.approved",
                            System.getenv().getOrDefault("TRIP_CANCELLATION_REFUND_APPROVED", "false")))) {
                        throw new AccountException(409, "CANCELLATION_REFUND_POLICY_NOT_APPROVED",
                                "Chính sách hoàn 100% khi nhà xe hủy chuyến chưa được phê duyệt; công việc được giữ để đối soát.");
                    }
                    if (order.chinhSachHoanVe == null) {
                        throw new AccountException(409, "REFUND_POLICY_MISSING",
                                "Đơn thiếu chính sách hoàn đã chốt; cần đối soát thủ công.");
                    }
                    var metadata = kiemTraMetadataCong(em, payment);
                    List<Line> lines = new ArrayList<>();
                    BigDecimal amount = BigDecimal.ZERO;
                    for (VeXe ticket : tickets) {
                        BigDecimal net = ticket.giaVe.setScale(0, RoundingMode.UNNECESSARY);
                        lines.add(new Line(ticket.maVe, dinhDangTien(net), "100", "0", dinhDangTien(net)));
                        amount = amount.add(net);
                    }
                    if (tickets.isEmpty()) {
                        amount = payment.soTien.setScale(0, RoundingMode.UNNECESSARY);
                    }
                    kiemTraHanMucHoan(previous, payment, amount);
                    String id = "HT-" + UUID.randomUUID();
                    String refundReason = "NHA_XE_HUY_CHUYEN: " + reason;
                    if (refundReason.length() > 500) {
                        refundReason = refundReason.substring(0, 500);
                    }
                    Entry entry = new Entry(id, orderId, payment.maGiaoDich, actor.maNguoiDung,
                            order.chinhSachHoanVe.maChinhSach,
                        refundReason, amount, "CHO_XU_LY", List.copyOf(lines), List.of(new History("TAO_YEU_CAU",
                                null, null, null, thoiDiemHienTai())),
                        UUID.randomUUID().toString().replace("-", ""), null,
                        amount.compareTo(payment.soTien) == 0, null, clock.instant(), null);
                    if (amount.signum() > 0) {
                        entry = capNhatYeuCau(entry, "CHO_XU_LY", null, null, "GUI_CONG", null, null);
                        lenh = taoLenhHoan(entry, payment, metadata, ip);
                    }
                    repository.insert(entry);
                    yeuCau = entry;
                    yeuCauMoi = true;
                }
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
        Response response;
        if (yeuCauMoi) {
            response = guiHoanTien(yeuCau, lenh);
        } else if (HoanVeRepository.BLOCKING.contains(yeuCau.state())) {
            response = verify(yeuCau.id(), ip);
        } else {
            response = toResponse(yeuCau);
        }
        dongBoHuyChuyen(orderId);
        return response;
    }

    // Yêu cầu và dấu gửi đã commit; không giữ transaction khi gọi cổng.
    private Response guiHoanTien(Entry yeuCau, VNPayClient.RefundCommand lenh) {
        try {
            VNPayClient.RefundResult ketQua;
            if (yeuCau.amount().signum() == 0) {
                ketQua = new VNPayClient.RefundResult(
                        yeuCau.requestId(), yeuCau.paymentId(), BigDecimal.ZERO,
                        "00", "00", "LOCAL", clock.instant(), true);
            } else {
                ketQua = gateway.refund(lenh);
            }
            luuBangChungCong(yeuCau.id(), ketQua);
            return hoanTatYeuCau(yeuCau.id(), ketQua);
        } catch (RuntimeException e) {
            return danhDauCanDoiSoat(yeuCau.id(), yeuCau.requestId(), e);
        }
    }

    private VNPayClient.RefundCommand taoLenhHoan(
            Entry yeuCau, ThanhToan payment,
            PaymentGatewayMetadataRepository.Metadata metadata, String ip) {

        return new VNPayClient.RefundCommand(
                yeuCau.requestId(), yeuCau.paymentId(), yeuCau.amount(), payment.soTien,
                metadata.createdAt(), metadata.transactionNo(), yeuCau.fullRefund(), yeuCau.userId(), ip);
    }

    // CHẶNG NHẬN KẾT QUẢ: lưu bằng chứng trước, cập nhật vé sau.
    private void luuBangChungCong(String id, VNPayClient.RefundResult result) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                var repo = new HoanVeRepository(em);
                Entry raw = repo.find(id);
                if (raw == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
                }
                khoaDon(em, raw.orderId(), null, false);
                khoaThanhToan(em, raw.paymentId());
                Entry entry = repo.lock(id);
                if (entry == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
                }
                if (HoanVeRepository.BLOCKING.contains(entry.state()) && result != null && result.correlated()
                        && entry.requestId().equals(result.requestId())
                        && entry.paymentId().equals(result.originalReference())
                        && entry.amount().compareTo(result.amountVnd()) == 0) {
                    String maKetQua = result.responseCode() + "/" + result.transactionStatus();
                    repo.update(capNhatYeuCau(entry, entry.state(), result.transactionNo(),
                            entry.error(), "DA_XAC_MINH_CONG", maKetQua, null));
                }
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    private Response hoanTatYeuCau(String id, VNPayClient.RefundResult result) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                var repo = new HoanVeRepository(em);
                Entry raw = repo.find(id);
                if (raw == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
                }
                DonDatVe order = khoaDon(em, raw.orderId(), null, false);
                ThanhToan payment = khoaThanhToan(em, raw.paymentId());
                Entry entry = repo.lock(id);
                if (entry == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
                }
                if (!HoanVeRepository.BLOCKING.contains(entry.state())) {
                    var response = toResponse(entry);
                    tx.commit();
                    return response;
                }
                if (result != null && !entry.requestId().equals(result.requestId())) {
                    var response = toResponse(entry);
                    tx.commit();
                    return response;
                }
                if (result == null || !entry.paymentId().equals(result.originalReference())
                        || entry.amount().compareTo(result.amountVnd()) != 0 || !result.correlated()) {
                    Entry pending = capNhatYeuCau(entry, "CAN_DOI_SOAT", entry.providerId(), "REFUND_NOT_CORRELATED",
                            "KET_QUA", null, null);
                    repo.update(pending);
                    var response = toResponse(pending);
                    tx.commit();
                    return response;
                }
                String state;
                if ("00".equals(result.responseCode()) && Set.of("00", "05",
                        "06").contains(Objects.toString(result.transactionStatus(), ""))) {
                    state = "THANH_CONG";
                } else if (Set.of("02", "09").contains(Objects.toString(result.transactionStatus(), ""))
                        || Set.of("02", "03", "91", "95", "97").contains(Objects.toString(result.responseCode(), ""))) {
                    state = "THAT_BAI";
                } else {
                    state = "CAN_DOI_SOAT";
                }
                boolean doHuyChuyen = entry.reason() != null
                        && entry.reason().startsWith("NHA_XE_HUY_CHUYEN:");
                if (state.equals("THANH_CONG")) {
                    for (Line line : entry.lines()) {
                        VeXe ticket = AccessControl.resource(em, VeXe.class, line.maVe());
                        boolean veHopLe = ticket.trangThai == TrangThaiVe.DA_XUAT_VE
                                || (doHuyChuyen && ticket.trangThai == TrangThaiVe.DA_LEN_XE);
                        if (!ticket.donDatVe.maDon.equals(order.maDon) || !veHopLe) {
                            throw new AccountException(409, "REFUND_TICKET_CONFLICT",
                                    "Vé đã thay đổi trong thời gian hoàn; cần đối soát.");
                        }
                        ticket.trangThai = TrangThaiVe.DA_HOAN;
                    }
                    BigDecimal refunded = entry.amount();
                    for (Entry previous : repo.forOrder(order.maDon)) {
                        if (previous.paymentId().equals(payment.maGiaoDich)
                                && previous.state().equals("THANH_CONG")) {
                            refunded = refunded.add(previous.amount());
                        }
                    }
                    if (refunded.signum() > 0) {
                        if (refunded.compareTo(payment.soTien) == 0) {
                            payment.trangThai = TrangThaiThanhToan.DA_HOAN_TIEN;
                        } else {
                            payment.trangThai = TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN;
                        }
                    }
                }
                String maLoi = null;
                Instant hoanTatLuc = null;
                if (state.equals("THANH_CONG")) {
                    hoanTatLuc = clock.instant();
                } else {
                    maLoi = "VNP_" + result.responseCode() + "_" + result.transactionStatus();
                }
                String maKetQua = result.responseCode() + "/" + result.transactionStatus();
                Entry updated = capNhatYeuCau(entry, state, result.transactionNo(),
                        maLoi, "KET_QUA", maKetQua, hoanTatLuc);
                repo.update(updated);
                if (doHuyChuyen) {
                    dongBoHuyChuyen(em, order.maDon);
                }
                var response = toResponse(updated);
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    private Response danhDauCanDoiSoat(String maHoanTien, String maLanGui, RuntimeException failure) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                HoanVeRepository repository = new HoanVeRepository(em);
                Entry raw = repository.find(maHoanTien);
                if (raw == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy yêu cầu hoàn.");
                }
                khoaDon(em, raw.orderId(), null, false);
                khoaThanhToan(em, raw.paymentId());
                Entry yeuCau = repository.lock(maHoanTien);
                if (yeuCau == null) {
                    throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy yêu cầu hoàn.");
                }
                if (!HoanVeRepository.BLOCKING.contains(yeuCau.state())
                        || !maLanGui.equals(yeuCau.requestId())) {
                    Response response = toResponse(yeuCau);
                    tx.commit();
                    return response;
                }
                String maLoi = "REFUND_RESULT_UNKNOWN";
                if (failure instanceof AccountException error) {
                    maLoi = error.code();
                }
                Entry updated = capNhatYeuCau(yeuCau, "CAN_DOI_SOAT", yeuCau.providerId(),
                        maLoi, "LOI_CONG", maLoi, null);
                repository.update(updated);
                Response response = toResponse(updated);
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    private Preview tinhDuKienHoan(EntityManager em, DonDatVe order, List<String> ids) {
        if (order.trangThai != TrangThaiDon.DA_THANH_TOAN) {
            throw new AccountException(409, "REFUND_NOT_ALLOWED", "Đơn chưa thanh toán thành công.");
        }
        var policy = order.chinhSachHoanVe;
        if (policy == null) {
            throw new AccountException(409, "REFUND_POLICY_MISSING",
                    "Đơn chưa có chính sách đã cam kết; không tự gán hồi tố.");
        }
        if (!policy.choPhepHoanVeKhuyenMai && order.maKhuyenMaiDaApDung != null) {
            throw new AccountException(409, "REFUND_NOT_ALLOWED", "Chính sách không cho hoàn vé có khuyến mãi.");
        }
        List<Entry> requests = new HoanVeRepository(em).forOrder(order.maDon);
        ThanhToan payment = null;
        for (ThanhToan candidate : new ThanhToanRepository(em).forOrder(order.maDon)) {
            if (CAPTURED.contains(candidate.trangThai)) {
                if (payment != null) {
                    throw new AccountException(409, "REFUND_PAYMENT_AMBIGUOUS",
                            "Không xác định được duy nhất giao dịch thu tiền.");
                }
                payment = candidate;
            }
        }
        if (payment == null) {
            throw new AccountException(409, "REFUND_PAYMENT_AMBIGUOUS",
                    "Không xác định được duy nhất giao dịch thu tiền.");
        }
        if (!Set.of("VNPAY", "MIEN_PHI").contains(payment.phuongThucThanhToan.maPhuongThuc)) {
            throw new AccountException(409, "PAYMENT_METHOD_UNSUPPORTED", "Phương thức chưa hỗ trợ hoàn.");
        }
        for (Entry entry : requests) {
            if (entry.paymentId().equals(payment.maGiaoDich) && HoanVeRepository.BLOCKING.contains(entry.state())) {
                throw new AccountException(409, "REFUND_IN_PROGRESS", "Giao dịch đang có yêu cầu hoàn cần xử lý.");
            }
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String id : ids) {
            VeXe ticket = layVe(em, id);
            if (!ticket.donDatVe.maDon.equals(order.maDon)) {
                throw AccountException.invalid("danhSachMaVe", "Có vé thuộc đơn khác.");
            }
            if (ticket.trangThai != TrangThaiVe.DA_XUAT_VE) {
                throw new AccountException(409, "REFUND_NOT_ALLOWED", "Vé đã lên xe, hoàn hoặc hủy.");
            }
            if (Set.of(TrangThaiChuyen.DA_HUY, TrangThaiChuyen.DANG_CHAY,
                TrangThaiChuyen.HOAN_TAT).contains(ticket.chuyenXe.trangThai)) {
                throw new AccountException(409, "REFUND_NOT_ALLOWED", "Chuyến không thuộc luồng khách tự hoàn vé.");
            }
            Instant departure = LocalDateTime.of(ticket.chuyenXe.ngayKhoiHanh,
                ticket.chuyenXe.gioKhoiHanh).atZone(TripSaleRules.ZONE).toInstant();
            if (!clock.instant().isBefore(departure)
                    || clock.instant().isAfter(departure.minus(Duration.ofHours(policy.soGioToiThieuTruocKhoiHanh)))) {
                throw new AccountException(409, "REFUND_NOT_ALLOWED", "Đã quá thời hạn hoàn vé theo chính sách.");
            }
            BigDecimal amount = PriceCalculator.calculateRefund(ticket.giaVe, policy.tyLeHoan, policy.phiHoan);
            lines.add(new Line(id, dinhDangTien(ticket.giaVe), policy.tyLeHoan.stripTrailingZeros().toPlainString(),
                dinhDangTien(policy.phiHoan), dinhDangTien(amount)));
            total = total.add(amount);
        }
        kiemTraHanMucHoan(requests, payment, total);
        return new Preview(order.maDon, payment.maGiaoDich, policy.maChinhSach,
            dinhDangTien(total), "VND", List.copyOf(lines), thoiDiemHienTai());
    }

    private PaymentGatewayMetadataRepository.Metadata kiemTraMetadataCong(EntityManager em, ThanhToan payment) {
        var value = new PaymentGatewayMetadataRepository(em).find(payment.maGiaoDich);
        if (value == null || value.createdAt() == null) {
            throw new AccountException(409, "REFUND_CONTEXT_MISSING",
                    "Giao dịch thiếu thời điểm tạo VNPay; cần đối soát, không suy đoán.");
        }
        if (gateway == null || gateway.merchantCode() == null || gateway.merchantCode().isBlank()) {
            throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Chưa cấu hình cổng hoàn tiền.");
        }
        if (!value.merchantCode().equals(gateway.merchantCode())) {
            throw new AccountException(409, "REFUND_CONTEXT_MISMATCH", "Merchant hiện tại khác giao dịch gốc.");
        }
        return value;
    }

    private static void kiemTraHanMucHoan(List<Entry> entries, ThanhToan payment, BigDecimal requested) {
        BigDecimal used = BigDecimal.ZERO;
        for (Entry entry : entries) {
            if (entry.paymentId().equals(payment.maGiaoDich)
                    && (entry.state().equals("THANH_CONG") || HoanVeRepository.BLOCKING.contains(entry.state()))) {
                used = used.add(entry.amount());
            }
        }
        if (used.add(requested).compareTo(payment.soTien) > 0) {
            throw new AccountException(409, "REFUND_AMOUNT_EXCEEDED", "Tiền hoàn vượt số tiền đã thu.");
        }
    }

    private DonDatVe khoaDon(EntityManager em, String id, NguoiDung user, boolean ownerOnly) {
        HoanVeRepository repository = new HoanVeRepository(em);
        DonDatVe initial = repository.findOrder(kiemTraMa(id));
        if (initial == null) {
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
        }
        List<VeXe> tickets = new VeXeRepository(em).forOrder(id);
        ChuyenXe trip;
        if (tickets.isEmpty()) {
            List<?> cancellation = repository.cancellationTripsForRefund(id);
            if (cancellation.isEmpty()) {
                throw new AccountException(409, "REFUND_NOT_ALLOWED",
                        "Đơn chưa phát hành vé; thuộc luồng đối soát riêng.");
            }
            trip = AccessControl.resource(em, ChuyenXe.class, cancellation.getFirst().toString());
        } else {
            trip = (ChuyenXe) org.hibernate.Hibernate.unproxy(tickets.getFirst().chuyenXe);
            if (trip.tuyenXe.nhaXe != null) {
                AccessControl.resource(em, NhaXe.class, trip.tuyenXe.nhaXe.maNhaXe);
            }
            AccessControl.resource(em, ChuyenXe.class, trip.maChuyen);
        }
        DonDatVe order = AccessControl.resource(em, DonDatVe.class, id);
        if (user != null) {
            if (ownerOnly || user.loaiTaiKhoan == LoaiTaiKhoan.KHACH_HANG) {
                AccessControl.role(user, LoaiTaiKhoan.KHACH_HANG);
                if (!order.nguoiDat.maNguoiDung.equals(user.maNguoiDung)) {
                    throw AccessControl.denied();
                }
            } else {
                AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
                AccessControl.operator(user, trip.tuyenXe.nhaXe);
            }
        }
        return order;
    }

    private ThanhToan khoaThanhToan(EntityManager em, String id) {
        ThanhToan payment = new ThanhToanRepository(em).lockById(id);
        if (payment == null) {
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
        }
        return payment;
    }

    private NguoiDung xacThuc(EntityManager em) {
        var context = RequestIdentity.get();
        return AccessControl.authenticated(em, context == null ? null : context.user(), clock.instant());
    }

    private static VeXe layVe(EntityManager em, String id) {
        VeXe value = new HoanVeRepository(em).findTicket(kiemTraMa(id));
        if (value == null) {
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
        }
        return value;
    }

    private Entry capNhatYeuCau(Entry entry, String state, String provider, String error, String action, String result,
            Instant confirmed) {
        List<History> history = new ArrayList<>(entry.history());
        history.add(new History(action, entry.requestId(), result, provider, thoiDiemHienTai()));
        return new Entry(entry.id(), entry.orderId(), entry.paymentId(), entry.userId(), entry.policyId(),
                entry.reason(), entry.amount(), state, entry.lines(),
            List.copyOf(history), entry.requestId(), provider, entry.fullRefund(), error, entry.created(), confirmed);
    }

    private Idem taoIdempotency(EntityManager em, NguoiDung user, String key, String hashInput) {
        var context = RequestIdentity.get();
        String path = context == null ? "/api/hoan-tien" : context.path();
        var scope = new IdempotencyRepository.Scope(user.maNguoiDung, "POST", path, key);
        var repo = new IdempotencyRepository(em);
        repo.lock(scope);
        return new Idem(repo, scope, IdempotencyRepository.hash(hashInput));
    }

    private BookingDto.Result docKetQuaDaLuu(IdempotencyRepository.Saved saved, String hash) {
        if (!saved.hash().equals(hash)) {
            throw new AccountException(409, "IDEMPOTENCY_KEY_REUSED", "Khóa đã dùng cho nội dung khác.");
        }
        try {
            return new BookingDto.Result(saved.status(), JSON.readTree(saved.body()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid idempotency response", e);
        }
    }

    private static Request kiemTraRequest(Request input) {
        if (input == null || input.danhSachMaVe() == null || input.danhSachMaVe().isEmpty()
                || input.danhSachMaVe().size() > 100) {
            throw AccountException.invalid("danhSachMaVe", "Cần chọn từ 1 đến 100 vé.");
        }
        Set<String> ids = new TreeSet<>();
        for (String id : input.danhSachMaVe()) {
            if (!ids.add(kiemTraMa(id))) {
                throw AccountException.invalid("danhSachMaVe", "Không gửi vé trùng.");
            }
        }
        if (input.lyDo() != null && input.lyDo().length() > 500) {
            throw AccountException.invalid("lyDo", "Lý do tối đa 500 ký tự.");
        }
        return new Request(List.copyOf(ids), input.lyDo());
    }

    private static void kiemTraKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,128}")) {
            throw AccountException.invalid("Idempotency-Key", "Cần khóa idempotency hợp lệ.");
        }
    }

    private static String kiemTraMa(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}")) {
            throw AccountException.invalid("id", "Mã không hợp lệ.");
        }
        return value;
    }

    private static String dinhDangTien(BigDecimal value) {
        return value.setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private OffsetDateTime thoiDiemHienTai() {
        return clock.instant().atZone(TripSaleRules.ZONE).toOffsetDateTime();
    }

    private Response toResponse(Entry value) {
        OffsetDateTime created = value.created() == null ? null
                : value.created().atZone(TripSaleRules.ZONE).toOffsetDateTime();
        OffsetDateTime confirmed = value.confirmed() == null ? null
                : value.confirmed().atZone(TripSaleRules.ZONE).toOffsetDateTime();
        return new Response(value.id(), value.orderId(), value.paymentId(), value.policyId(),
            value.amount().setScale(0).toPlainString(), "VND", value.state(), value.lines(),
            created, confirmed, value.providerId(), value.error(), value.history());
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot serialize refund data", e);
        }
    }

    private void dongBoHuyChuyen(String orderId) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                dongBoHuyChuyen(em, orderId);
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    private void dongBoHuyChuyen(EntityManager em, String orderId) {
        HoanVeRepository repository = new HoanVeRepository(em);
        long blocking = repository.countBlockingForOrder(orderId);
        for (Object trip : repository.cancellationTripIds(orderId)) {
            String tripId = trip.toString();
            long tickets = repository.countIssuedTicketsForTrip(orderId, tripId);
            String state = "CHO_XU_LY";
            if (blocking > 0) {
                state = "CAN_DOI_SOAT";
            } else if (tickets == 0) {
                state = "HOAN_TAT";
            }
            repository.updateCancellationWork(orderId, tripId, state, clock.instant());
        }
    }

    private record Idem(
            IdempotencyRepository repository,
            IdempotencyRepository.Scope scope,
            String hash) {}

}
