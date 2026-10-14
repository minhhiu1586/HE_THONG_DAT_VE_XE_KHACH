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
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** T19: durable intent -> provider call without DB transaction -> atomic confirmed result. */
public final class HoanVeService {
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
        this.factory = factory; this.clock = clock; this.gateway = gateway;
    }
    private static Gateway defaultGateway() {
        var config = VNPayConfig.fromEnvironment(); var client = new VNPayClient(config);
        return new Gateway() {
            public String merchantCode() { return config.tmnCode(); }
            public VNPayClient.RefundResult refund(VNPayClient.RefundCommand c) { return client.refund(c); }
            public VNPayClient.RefundResult query(VNPayClient.RefundCommand c, String id) { return client.queryRefund(c,id); }
        };
    }
    public Preview preview(String orderId, Request input) {
        Request request = validate(input);
        return tx(em -> {
            NguoiDung user = authenticated(em);
            DonDatVe order = lockOrder(em, orderId, user, true);
            return calculate(em, order, request.danhSachMaVe());
        });
    }
    public PolicyResponse policy(String ticketId) {
        return tx(em -> {
            NguoiDung user = authenticated(em);
            VeXe initial = ticket(em,ticketId);
            DonDatVe order = lockOrder(em,initial.donDatVe.maDon,user,true);
            var policy = order.chinhSachHoanVe;
            try {
                Preview preview = calculate(em,order,List.of(ticketId));
                return new PolicyResponse(ticketId,com.datvexe.dto.ChinhSachHoanVeDto.view(policy),true,preview.soTienHoan(),"VND",null,now());
            } catch (AccountException error) {
                if (error.status() != 409) throw error;
                return new PolicyResponse(ticketId,policy == null ? null : com.datvexe.dto.ChinhSachHoanVeDto.view(policy),false,"0","VND",error.getMessage(),now());
            }
        });
    }
    public BookingDto.Result createTicket(String ticketId, SingleRequest input, String key, String ip) {
        if (input == null) throw invalid("body","Body JSON là bắt buộc.");
        String orderId = tx(em -> {
            NguoiDung user = authenticated(em); VeXe ticket = ticket(em,ticketId);
            return lockOrder(em,ticket.donDatVe.maDon,user,true).maDon;
        });
        return create(orderId,new Request(List.of(ticketId),input.lyDo()),key,ip);
    }
    public BookingDto.Result create(String orderId, Request input, String key, String ip) {
        Request request = validate(input); validateKey(key);
        Prepared prepared = tx(em -> {
            NguoiDung user = authenticated(em);
            AccessControl.role(user,LoaiTaiKhoan.KHACH_HANG);
            Idem idem = idem(em,user,key,HoanVeRepository.json(request));
            var saved = idem.repository().find(idem.scope(),clock.instant());
            if (saved != null) return new Prepared(null,replay(saved,idem.hash()),null,null,false);
            DonDatVe order = lockOrder(em,orderId,user,true);
            Preview preview = calculate(em,order,request.danhSachMaVe());
            ThanhToan payment = lockPayment(em,preview.maGiaoDichGoc());
            var repository = new HoanVeRepository(em);
            List<Entry> previous = repository.forOrder(order.maDon);
            assertBudget(previous,payment,new BigDecimal(preview.soTienHoan()),null);
            BigDecimal amount = new BigDecimal(preview.soTienHoan());
            var metadata = amount.signum() > 0 ? requireMetadata(em,payment) : null;
            boolean full = amount.compareTo(payment.soTien) == 0 && completed(previous,payment.maGiaoDich).signum() == 0;
            String id = "HT-" + UUID.randomUUID();
            Entry entry = new Entry(id,order.maDon,payment.maGiaoDich,user.maNguoiDung,preview.maChinhSach(),request.lyDo(),amount,
                    "CHO_XU_LY",preview.chiTiet(),List.of(new History("TAO_YEU_CAU",null,null,null,now())),requestId(),null,full,null,clock.instant(),null);
            repository.insert(entry);
            BookingDto.Result accepted = new BookingDto.Result(202,HoanVeRepository.response(entry));
            idem.repository().save(idem.scope(),idem.hash(),202,HoanVeRepository.json(accepted.body()),clock.instant());
            return new Prepared(entry,accepted,payment.soTien,metadata,true);
        });
        if (!prepared.fresh()) return prepared.result();
        dispatch(prepared,ip);
        // Return the same durable acceptance snapshot on the first call and replay.
        // GET /api/hoan-tien/{id} supplies the current processing result.
        return prepared.result();
    }
    public Response get(String id) {
        return tx(em -> {
            NguoiDung user = authenticated(em); Entry entry = found(new HoanVeRepository(em).find(code(id)));
            lockOrder(em,entry.orderId(),user,false);
            return HoanVeRepository.response(entry);
        });
    }
    public Page history(String ticketId, int page, int size, String sort) {
        if (page < 1 || size < 1 || size > 100 || ((long)page-1)*size > Integer.MAX_VALUE)
            throw invalid("page","Phân trang không hợp lệ.");
        if (sort != null && !Set.of("thoiDiemTao,asc","thoiDiemTao,desc").contains(sort)) throw invalid("sort","Sort không hợp lệ.");
        return tx(em -> {
            NguoiDung user = authenticated(em); VeXe ticket = ticket(em,ticketId);
            DonDatVe order = lockOrder(em,ticket.donDatVe.maDon,user,false);
            var values = new ArrayList<>(new HoanVeRepository(em).forOrder(order.maDon).stream()
                    .filter(e -> e.lines().stream().anyMatch(l -> l.maVe().equals(ticketId))).toList());
            if ("thoiDiemTao,desc".equals(sort)) Collections.reverse(values);
            long total = values.size();
            return new Page(values.stream().skip((long)(page-1)*size).limit(size).map(HoanVeRepository::response).toList(),page,size,total,(total+size-1)/size);
        });
    }
    public Response verify(String id, String ip) {
        Prepared prepared = tx(em -> {
            NguoiDung user = authenticated(em); var repo = new HoanVeRepository(em); Entry raw = found(repo.find(code(id)));
            lockOrder(em,raw.orderId(),user,false); ThanhToan payment = lockPayment(em,raw.paymentId()); Entry entry = found(repo.lock(id));
            if (!HoanVeRepository.BLOCKING.contains(entry.state())) return new Prepared(entry,null,null,null,false);
            if (entry.amount().signum() == 0) return new Prepared(entry,null,payment.soTien,null,true);
            // A live sender owns the attempt for at most its configured network deadline.
            History lastSend = entry.history().stream().filter(h -> "GUI_CONG".equals(h.thaoTac())).reduce((a,b)->b).orElse(null);
            if (lastSend != null && clock.instant().isBefore(lastSend.thoiDiem().toInstant().plusSeconds(30)))
                throw error("REQUEST_IN_PROGRESS","Yêu cầu hoàn đang được gửi tới cổng.");
            var metadata = requireMetadata(em,payment);
            Entry checking = changed(entry,"CAN_DOI_SOAT",entry.providerId(),"AWAITING_VERIFICATION","XAC_MINH",null,null);
            repo.update(checking);
            return new Prepared(checking,null,payment.soTien,metadata,true);
        });
        if (!prepared.fresh()) return HoanVeRepository.response(prepared.entry());
        if (prepared.entry().amount().signum() == 0) return dispatch(prepared,ip);
        History evidence = prepared.entry().history().stream().filter(h -> "DA_XAC_MINH_CONG".equals(h.thaoTac())
                && prepared.entry().requestId().equals(h.maRequestCong()) && "00/00".equals(h.maKetQua())).reduce((a,b)->b).orElse(null);
        if (evidence != null) return applyVerified(prepared.entry().id(),new VNPayClient.RefundResult(prepared.entry().requestId(),
                prepared.entry().paymentId(),prepared.entry().amount(),"00","00",evidence.maGiaoDichCong(),null,true));
        try { return applyVerified(prepared.entry().id(),gateway.query(command(prepared,ip),prepared.entry().providerId())); }
        catch (RuntimeException failure) { return uncertain(prepared.entry().id(),failure); }
    }
    public BookingDto.Result retry(String id, String key, String ip) {
        validateKey(key);
        Prepared prepared = tx(em -> {
            NguoiDung user = authenticated(em); AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE);
            Idem idem = idem(em,user,key,"retry:"+code(id)); var saved = idem.repository().find(idem.scope(),clock.instant());
            if (saved != null) return new Prepared(null,replay(saved,idem.hash()),null,null,false);
            var repo = new HoanVeRepository(em); Entry raw = found(repo.find(id));
            DonDatVe order = lockOrder(em,raw.orderId(),user,false); ThanhToan payment = lockPayment(em,raw.paymentId()); Entry entry = found(repo.lock(id));
            if (!entry.state().equals("THAT_BAI")) throw error("REFUND_RETRY_NOT_ALLOWED","Chỉ thử lại khoản hoàn đã xác định thất bại.");
            Preview preview = calculate(em,order,entry.lines().stream().map(Line::maVe).toList());
            if (!preview.maGiaoDichGoc().equals(entry.paymentId()) || !preview.chiTiet().equals(entry.lines()))
                throw error("REFUND_NOT_ALLOWED","Dữ liệu hoàn đã thay đổi.");
            assertBudget(repo.forOrder(order.maDon),payment,entry.amount(),entry.id());
            var metadata = requireMetadata(em,payment);
            Entry next = new Entry(entry.id(),entry.orderId(),entry.paymentId(),entry.userId(),entry.policyId(),entry.reason(),entry.amount(),
                    "CHO_XU_LY",entry.lines(),entry.history(),requestId(),null,entry.fullRefund(),null,entry.created(),null);
            next = changed(next,"CHO_XU_LY",null,null,"THU_LAI",null,null); repo.update(next);
            BookingDto.Result accepted = new BookingDto.Result(202,HoanVeRepository.response(next));
            idem.repository().save(idem.scope(),idem.hash(),202,HoanVeRepository.json(accepted.body()),clock.instant());
            return new Prepared(next,accepted,payment.soTien,metadata,true);
        });
        if (!prepared.fresh()) return prepared.result();
        dispatch(prepared,ip);
        return prepared.result();
    }
    private Response dispatch(Prepared prepared, String ip) {
        if (prepared.entry().amount().signum() == 0) return applyVerified(prepared.entry().id(),new VNPayClient.RefundResult(
                prepared.entry().requestId(),prepared.entry().paymentId(),BigDecimal.ZERO,"00","00","LOCAL",clock.instant(),true));
        Prepared sending = tx(em -> {
            var repo = new HoanVeRepository(em); Entry raw = found(repo.find(prepared.entry().id()));
            lockOrder(em,raw.orderId(),null,false); lockPayment(em,raw.paymentId()); Entry entry = found(repo.lock(raw.id()));
            if (!entry.state().equals("CHO_XU_LY") || !entry.requestId().equals(prepared.entry().requestId()))
                throw error("REQUEST_IN_PROGRESS","Yêu cầu đã được xử lý.");
            Entry next = changed(entry,"CHO_XU_LY",null,null,"GUI_CONG",null,null); repo.update(next);
            return new Prepared(next,null,prepared.originalAmount(),prepared.metadata(),true);
        });
        try { return applyVerified(sending.entry().id(),gateway.refund(command(sending,ip))); }
        catch (RuntimeException failure) { return uncertain(sending.entry().id(),failure); }
    }
    private VNPayClient.RefundCommand command(Prepared prepared, String ip) {
        Entry entry = prepared.entry();
        return new VNPayClient.RefundCommand(entry.requestId(),entry.paymentId(),entry.amount(),prepared.originalAmount(),
                prepared.metadata().createdAt(),prepared.metadata().transactionNo(),entry.fullRefund(),entry.userId(),ip);
    }
    /** Persist verified provider evidence before finalizing tickets, allowing recovery without paying twice. */
    private Response applyVerified(String id, VNPayClient.RefundResult result) {
        tx(em -> {
            var repo = new HoanVeRepository(em); Entry raw = found(repo.find(id));
            lockOrder(em,raw.orderId(),null,false); lockPayment(em,raw.paymentId()); Entry entry = found(repo.lock(id));
            if (HoanVeRepository.BLOCKING.contains(entry.state()) && result != null && result.correlated()
                    && entry.requestId().equals(result.requestId()) && entry.paymentId().equals(result.originalReference())
                    && entry.amount().compareTo(result.amountVnd()) == 0) {
                repo.update(changed(entry,entry.state(),result.transactionNo(),entry.error(),"DA_XAC_MINH_CONG",
                        result.responseCode()+"/"+result.transactionStatus(),null));
            }
            return null;
        });
        return finish(id,result);
    }
    private Response finish(String id, VNPayClient.RefundResult result) {
        return tx(em -> {
            var repo = new HoanVeRepository(em); Entry raw = found(repo.find(id));
            DonDatVe order = lockOrder(em,raw.orderId(),null,false); ThanhToan payment = lockPayment(em,raw.paymentId()); Entry entry = found(repo.lock(id));
            if (!HoanVeRepository.BLOCKING.contains(entry.state())) return HoanVeRepository.response(entry);
            if (result != null && !entry.requestId().equals(result.requestId())) return HoanVeRepository.response(entry);
            if (result == null || !entry.paymentId().equals(result.originalReference())
                    || entry.amount().compareTo(result.amountVnd()) != 0 || !result.correlated()) {
                Entry pending = changed(entry,"CAN_DOI_SOAT",entry.providerId(),"REFUND_NOT_CORRELATED","KET_QUA",null,null);
                repo.update(pending); return HoanVeRepository.response(pending);
            }
            String state;
            if ("00".equals(result.responseCode()) && "00".equals(result.transactionStatus())) state = "THANH_CONG";
            else if (Set.of("02","09").contains(Objects.toString(result.transactionStatus(),""))
                    || Set.of("02","03","91","95","97").contains(Objects.toString(result.responseCode(),""))) state = "THAT_BAI";
            else state = "CAN_DOI_SOAT";
            if (state.equals("THANH_CONG")) {
                List<Entry> previous = repo.forOrder(order.maDon); assertBudget(previous,payment,entry.amount(),entry.id());
                for (Line line : entry.lines()) {
                    VeXe ticket = AccessControl.resource(em,VeXe.class,line.maVe());
                    if (!ticket.donDatVe.maDon.equals(order.maDon) || ticket.trangThai != TrangThaiVe.DA_XUAT_VE)
                        throw error("REFUND_TICKET_CONFLICT","Vé đã thay đổi trong thời gian hoàn; cần đối soát.");
                    ticket.trangThai = TrangThaiVe.DA_HOAN;
                }
                BigDecimal refunded = completed(previous,payment.maGiaoDich).add(entry.amount());
                if (refunded.signum() > 0) payment.trangThai = refunded.compareTo(payment.soTien) == 0
                        ? TrangThaiThanhToan.DA_HOAN_TIEN : TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN;
            }
            Entry updated = changed(entry,state,result.transactionNo(),state.equals("THANH_CONG")?null:
                    "VNP_"+result.responseCode()+"_"+result.transactionStatus(),"KET_QUA",result.responseCode()+"/"+result.transactionStatus(),
                    state.equals("THANH_CONG")?clock.instant():null);
            repo.update(updated); return HoanVeRepository.response(updated);
        });
    }
    private Response uncertain(String id, RuntimeException failure) {
        return tx(em -> {
            var repo = new HoanVeRepository(em); Entry raw = found(repo.find(id));
            lockOrder(em,raw.orderId(),null,false); lockPayment(em,raw.paymentId()); Entry entry = found(repo.lock(id));
            if (!HoanVeRepository.BLOCKING.contains(entry.state())) return HoanVeRepository.response(entry);
            String code = failure instanceof AccountException error ? error.code() : "REFUND_RESULT_UNKNOWN";
            Entry pending = changed(entry,"CAN_DOI_SOAT",entry.providerId(),code,"LOI_CONG",code,null);
            repo.update(pending); return HoanVeRepository.response(pending);
        });
    }
    private Preview calculate(EntityManager em, DonDatVe order, List<String> ids) {
        if (order.trangThai != TrangThaiDon.DA_THANH_TOAN) throw error("REFUND_NOT_ALLOWED","Đơn chưa thanh toán thành công.");
        var policy = order.chinhSachHoanVe;
        if (policy == null) throw error("REFUND_POLICY_MISSING","Đơn chưa có chính sách đã cam kết; không tự gán hồi tố.");
        if (!policy.choPhepHoanVeKhuyenMai && order.maKhuyenMaiDaApDung != null)
            throw error("REFUND_NOT_ALLOWED","Chính sách không cho hoàn vé có khuyến mãi.");
        List<Entry> requests = new HoanVeRepository(em).forOrder(order.maDon);
        List<ThanhToan> captures = new ThanhToanRepository(em).forOrder(order.maDon).stream().filter(p -> CAPTURED.contains(p.trangThai)).toList();
        if (captures.size() != 1) throw error("REFUND_PAYMENT_AMBIGUOUS","Không xác định được duy nhất giao dịch thu tiền.");
        ThanhToan payment = captures.getFirst();
        if (!Set.of("VNPAY","MIEN_PHI").contains(payment.phuongThucThanhToan.maPhuongThuc))
            throw error("PAYMENT_METHOD_UNSUPPORTED","Phương thức chưa hỗ trợ hoàn.");
        if (requests.stream().anyMatch(e -> e.paymentId().equals(payment.maGiaoDich) && HoanVeRepository.BLOCKING.contains(e.state())))
            throw error("REFUND_IN_PROGRESS","Giao dịch đang có yêu cầu hoàn cần xử lý.");
        List<Line> lines = new ArrayList<>(); BigDecimal total = BigDecimal.ZERO;
        for (String id : ids) {
            VeXe ticket = ticket(em,id);
            if (!ticket.donDatVe.maDon.equals(order.maDon)) throw invalid("danhSachMaVe","Có vé thuộc đơn khác.");
            if (ticket.trangThai != TrangThaiVe.DA_XUAT_VE) throw error("REFUND_NOT_ALLOWED","Vé đã lên xe, hoàn hoặc hủy.");
            if (Set.of(TrangThaiChuyen.DA_HUY,TrangThaiChuyen.DANG_CHAY,TrangThaiChuyen.HOAN_TAT).contains(ticket.chuyenXe.trangThai))
                throw error("REFUND_NOT_ALLOWED","Chuyến không thuộc luồng khách tự hoàn vé.");
            Instant departure = LocalDateTime.of(ticket.chuyenXe.ngayKhoiHanh,ticket.chuyenXe.gioKhoiHanh).atZone(TripSaleRules.ZONE).toInstant();
            if (!clock.instant().isBefore(departure) || clock.instant().isAfter(departure.minus(Duration.ofHours(policy.soGioToiThieuTruocKhoiHanh))))
                throw error("REFUND_NOT_ALLOWED","Đã quá thời hạn hoàn vé theo chính sách.");
            if (requests.stream().anyMatch(e -> e.state().equals("THANH_CONG") && e.lines().stream().anyMatch(l -> l.maVe().equals(id))))
                throw error("REFUND_ALREADY_COMPLETED","Vé đã được hoàn.");
            BigDecimal amount = PriceCalculator.calculateRefund(ticket.giaVe,policy.tyLeHoan,policy.phiHoan);
            lines.add(new Line(id,money(ticket.giaVe),policy.tyLeHoan.stripTrailingZeros().toPlainString(),money(policy.phiHoan),money(amount))); total = total.add(amount);
        }
        assertBudget(requests,payment,total,null);
        return new Preview(order.maDon,payment.maGiaoDich,policy.maChinhSach,money(total),"VND",List.copyOf(lines),now());
    }
    private PaymentGatewayMetadataRepository.Metadata requireMetadata(EntityManager em, ThanhToan payment) {
        var value = new PaymentGatewayMetadataRepository(em).find(payment.maGiaoDich);
        if (value == null || value.createdAt() == null) throw error("REFUND_CONTEXT_MISSING","Giao dịch thiếu thời điểm tạo VNPay; cần đối soát, không suy đoán.");
        if (gateway == null || gateway.merchantCode() == null || gateway.merchantCode().isBlank())
            throw new AccountException(503,"PAYMENT_NOT_CONFIGURED","Chưa cấu hình cổng hoàn tiền.");
        if (!value.merchantCode().equals(gateway.merchantCode())) throw error("REFUND_CONTEXT_MISMATCH","Merchant hiện tại khác giao dịch gốc.");
        return value;
    }
    private static void assertBudget(List<Entry> entries, ThanhToan payment, BigDecimal requested, String exclude) {
        BigDecimal used = entries.stream().filter(e -> e.paymentId().equals(payment.maGiaoDich) && !e.id().equals(exclude)
                && (e.state().equals("THANH_CONG") || HoanVeRepository.BLOCKING.contains(e.state()))).map(Entry::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if (used.add(requested).compareTo(payment.soTien) > 0) throw error("REFUND_AMOUNT_EXCEEDED","Tiền hoàn vượt số tiền đã thu.");
    }
    private static BigDecimal completed(List<Entry> entries, String payment) {
        return entries.stream().filter(e -> e.paymentId().equals(payment) && e.state().equals("THANH_CONG")).map(Entry::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private DonDatVe lockOrder(EntityManager em, String id, NguoiDung user, boolean ownerOnly) {
        DonDatVe initial = em.find(DonDatVe.class,code(id));
        if (initial == null) throw missing();
        List<VeXe> tickets = new VeXeRepository(em).forOrder(id);
        if (tickets.isEmpty()) throw error("REFUND_NOT_ALLOWED","Đơn chưa phát hành vé; thuộc luồng đối soát riêng.");
        ChuyenXe trip = (ChuyenXe)org.hibernate.Hibernate.unproxy(tickets.getFirst().chuyenXe);
        if (trip.tuyenXe.nhaXe != null) AccessControl.resource(em,NhaXe.class,trip.tuyenXe.nhaXe.maNhaXe);
        AccessControl.resource(em,ChuyenXe.class,trip.maChuyen);
        DonDatVe order = AccessControl.resource(em,DonDatVe.class,id);
        if (user != null) {
            if (ownerOnly || user.loaiTaiKhoan == LoaiTaiKhoan.KHACH_HANG) {
                AccessControl.role(user,LoaiTaiKhoan.KHACH_HANG);
                if (!order.nguoiDat.maNguoiDung.equals(user.maNguoiDung)) throw AccessControl.denied();
            } else { AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE); AccessControl.operator(user,trip.tuyenXe.nhaXe); }
        }
        return order;
    }
    private ThanhToan lockPayment(EntityManager em,String id) {
        ThanhToan payment = new ThanhToanRepository(em).lockById(id);
        if (payment == null) throw missing(); return payment;
    }
    private NguoiDung authenticated(EntityManager em) {
        var context = RequestIdentity.get();
        return AccessControl.authenticated(em,context == null ? null : context.user(),clock.instant());
    }
    private static VeXe ticket(EntityManager em,String id) {
        VeXe value = em.find(VeXe.class,code(id));
        if (value == null) throw missing();
        em.refresh(value);
        // Public entity fields must be read from the initialized entity, not its lazy proxy.
        value.donDatVe = (DonDatVe)org.hibernate.Hibernate.unproxy(value.donDatVe);
        value.chuyenXe = (ChuyenXe)org.hibernate.Hibernate.unproxy(value.chuyenXe);
        return value;
    }
    private Entry changed(Entry entry,String state,String provider,String error,String action,String result,Instant confirmed) {
        List<History> history = new ArrayList<>(entry.history()); history.add(new History(action,entry.requestId(),result,provider,now()));
        return new Entry(entry.id(),entry.orderId(),entry.paymentId(),entry.userId(),entry.policyId(),entry.reason(),entry.amount(),state,entry.lines(),
                List.copyOf(history),entry.requestId(),provider,entry.fullRefund(),error,entry.created(),confirmed);
    }
    private Idem idem(EntityManager em,NguoiDung user,String key,String hashInput) {
        var context = RequestIdentity.get(); String path = context == null ? "/api/hoan-tien" : context.path();
        var scope = new IdempotencyRepository.Scope(user.maNguoiDung,"POST",path,key);
        var repo = new IdempotencyRepository(em); repo.lock(scope);
        return new Idem(repo,scope,IdempotencyRepository.hash(hashInput));
    }
    private BookingDto.Result replay(IdempotencyRepository.Saved saved,String hash) {
        if (!saved.hash().equals(hash)) throw error("IDEMPOTENCY_KEY_REUSED","Khóa đã dùng cho nội dung khác.");
        try { return new BookingDto.Result(saved.status(),new ObjectMapper().readTree(saved.body())); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid idempotency response",e); }
    }
    private static Request validate(Request input) {
        if (input == null || input.danhSachMaVe() == null || input.danhSachMaVe().isEmpty() || input.danhSachMaVe().size() > 100)
            throw invalid("danhSachMaVe","Cần chọn từ 1 đến 100 vé.");
        Set<String> ids = new TreeSet<>();
        for (String id : input.danhSachMaVe()) if (!ids.add(code(id))) throw invalid("danhSachMaVe","Không gửi vé trùng.");
        if (input.lyDo() != null && input.lyDo().length() > 500) throw invalid("lyDo","Lý do tối đa 500 ký tự.");
        return new Request(List.copyOf(ids),input.lyDo());
    }
    private static void validateKey(String key) { if (key == null || !key.matches("[A-Za-z0-9_-]{1,128}")) throw invalid("Idempotency-Key","Cần khóa idempotency hợp lệ."); }
    private static String code(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}")) throw invalid("id","Mã không hợp lệ."); return value; }
    private static String requestId() { return UUID.randomUUID().toString().replace("-",""); }
    private static String money(BigDecimal value) { return value.setScale(0,java.math.RoundingMode.UNNECESSARY).toPlainString(); }
    private OffsetDateTime now() { return clock.instant().atZone(TripSaleRules.ZONE).toOffsetDateTime(); }
    private static Entry found(Entry entry) { if (entry == null) throw missing(); return entry; }
    private static AccountException missing() { return new AccountException(404,"RESOURCE_NOT_FOUND","Không tìm thấy tài nguyên."); }
    private static AccountException error(String code,String message) { return new AccountException(409,code,message); }
    private static AccountException invalid(String field,String message) { return AccountException.invalid(field,message); }
    private <T> T tx(Function<EntityManager,T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            var transaction = em.getTransaction(); transaction.begin();
            try { T value = action.apply(em); em.flush(); transaction.commit(); return value; }
            catch (RuntimeException e) { if (transaction.isActive()) transaction.rollback(); throw e; }
        }
    }
    private record Idem(IdempotencyRepository repository,IdempotencyRepository.Scope scope,String hash) {}
    private record Prepared(Entry entry,BookingDto.Result result,BigDecimal originalAmount,PaymentGatewayMetadataRepository.Metadata metadata,boolean fresh) {}
}
