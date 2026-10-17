package com.datvexe.repository;

import com.datvexe.dto.HoanVeDto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Native persistence for technical refund records, not a HoanTien business entity. */
public final class HoanVeRepository {
    public static final Set<String> BLOCKING = Set.of("CHO_XU_LY", "CAN_DOI_SOAT");
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public record Entry(String id, String orderId, String paymentId, String userId, String policyId,
            String reason, BigDecimal amount, String state, List<Line> lines, List<History> history,
            String requestId, String providerId, boolean fullRefund, String error, Instant created, Instant confirmed) {}
    private static final String COLUMNS = "ma_hoan_tien,ma_don,ma_giao_dich_goc,ma_nguoi_yeu_cau,ma_chinh_sach,ly_do,so_tien_hoan,trang_thai,cast(chi_tiet as text),cast(lich_su as text),ma_request_cong,ma_giao_dich_cong,loai_hoan_toan_phan,ly_do_loi,created_at,confirmed_at";
    private final EntityManager em;
    public HoanVeRepository(EntityManager em) { this.em = em; }
    public Entry find(String id) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("select " + COLUMNS + " from refund_request where ma_hoan_tien=:id")
                .setParameter("id",id).getResultList();
        return rows.isEmpty() ? null : map(rows.getFirst());
    }
    public Entry lock(String id) {
        em.createNativeQuery("select ma_hoan_tien from refund_request where ma_hoan_tien=:id for update").setParameter("id",id).getResultList();
        return find(id);
    }
    public List<Entry> forOrder(String order) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("select " + COLUMNS + " from refund_request where ma_don=:id order by created_at,ma_hoan_tien")
                .setParameter("id",order).getResultList();
        return rows.stream().map(HoanVeRepository::map).toList();
    }
    public boolean ticketBlocked(String ticket) {
        var orders = em.createNativeQuery("select ma_don from ve_xe where ma_ve=:ticket").setParameter("ticket",ticket).getResultList();
        return !orders.isEmpty() && forOrder((String)orders.getFirst()).stream().anyMatch(e -> BLOCKING.contains(e.state())
                && e.lines().stream().anyMatch(line -> line.maVe().equals(ticket)));
    }
    public void insert(Entry value) {
        em.createNativeQuery("insert into refund_request(ma_hoan_tien,ma_don,ma_giao_dich_goc,ma_nguoi_yeu_cau,ma_chinh_sach,ly_do,so_tien_hoan,trang_thai,chi_tiet,lich_su,ma_request_cong,ma_giao_dich_cong,loai_hoan_toan_phan,ly_do_loi,created_at,confirmed_at) values (:id,:order,:payment,:user,:policy,:reason,:amount,:state,cast(:lines as jsonb),cast(:history as jsonb),:request,:provider,:full,:error,:created,:confirmed)")
                .setParameter("id",value.id()).setParameter("order",value.orderId()).setParameter("payment",value.paymentId())
                .setParameter("user",value.userId()).setParameter("policy",value.policyId()).setParameter("reason",value.reason())
                .setParameter("amount",value.amount()).setParameter("state",value.state()).setParameter("lines",json(value.lines()))
                .setParameter("history",json(value.history())).setParameter("request",value.requestId()).setParameter("provider",value.providerId())
                .setParameter("full",value.fullRefund()).setParameter("error",value.error()).setParameter("created",value.created())
                .setParameter("confirmed",value.confirmed()).executeUpdate();
    }
    public void update(Entry value) {
        em.createNativeQuery("update refund_request set trang_thai=:state,lich_su=cast(:history as jsonb),ma_request_cong=:request,ma_giao_dich_cong=:provider,ly_do_loi=:error,confirmed_at=:confirmed where ma_hoan_tien=:id")
                .setParameter("id",value.id()).setParameter("state",value.state()).setParameter("history",json(value.history()))
                .setParameter("request",value.requestId()).setParameter("provider",value.providerId()).setParameter("error",value.error())
                .setParameter("confirmed",value.confirmed()).executeUpdate();
    }
    public static Response response(Entry value) {
        return new Response(value.id(),value.orderId(),value.paymentId(),value.policyId(),value.amount().setScale(0).toPlainString(),"VND",value.state(),
                value.lines(),offset(value.created()),offset(value.confirmed()),value.providerId(),value.error(),value.history());
    }
    private static OffsetDateTime offset(Instant time) { return time == null ? null : time.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(); }
    private static Entry map(Object[] row) {
        try {
            return new Entry((String)row[0],(String)row[1],(String)row[2],(String)row[3],(String)row[4],(String)row[5],
                    (BigDecimal)row[6],(String)row[7],JSON.readValue((String)row[8],new TypeReference<List<Line>>(){}),
                    JSON.readValue((String)row[9],new TypeReference<List<History>>(){}),(String)row[10],(String)row[11],
                    (Boolean)row[12],(String)row[13],PaymentGatewayMetadataRepository.instant(row[14]),PaymentGatewayMetadataRepository.instant(row[15]));
        } catch (java.io.IOException e) { throw new IllegalStateException("Invalid persisted refund data",e); }
    }
    public static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot serialize refund data",e); }
    }
}
