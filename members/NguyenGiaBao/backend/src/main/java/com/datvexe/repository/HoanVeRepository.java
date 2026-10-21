package com.datvexe.repository;

import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.Entities.VeXe;
import com.datvexe.domain.States.TrangThaiVe;
import com.datvexe.dto.HoanVeDto.History;
import com.datvexe.dto.HoanVeDto.Line;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lưu dữ liệu kỹ thuật hoàn tiền trong transaction của service. */
public final class HoanVeRepository {

    public static final Set<String> BLOCKING = Set.of("CHO_XU_LY", "CAN_DOI_SOAT");
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String COLUMNS = """
            ma_hoan_tien, ma_don, ma_giao_dich_goc, ma_nguoi_yeu_cau, ma_chinh_sach,
            ly_do, so_tien_hoan, trang_thai, cast(chi_tiet as text), cast(lich_su as text),
            ma_request_cong, ma_giao_dich_cong, loai_hoan_toan_phan, ly_do_loi, created_at, confirmed_at
            """;

    public record Entry(
            String id,
            String orderId,
            String paymentId,
            String userId,
            String policyId,
            String reason,
            BigDecimal amount,
            String state,
            List<Line> lines,
            List<History> history,
            String requestId,
            String providerId,
            boolean fullRefund,
            String error,
            Instant created,
            Instant confirmed) {}

    private final EntityManager em;

    public HoanVeRepository(EntityManager em) {
        this.em = em;
    }

    public Entry find(String id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select " + COLUMNS + " from refund_request where ma_hoan_tien = :id")
                .setParameter("id", id)
                .getResultList();
        return rows.isEmpty() ? null : map(rows.getFirst());
    }

    public Entry lock(String id) {
        em.createNativeQuery("""
                        select ma_hoan_tien from refund_request
                        where ma_hoan_tien = :id for update
                        """)
                .setParameter("id", id)
                .getResultList();
        return find(id);
    }

    public List<Entry> forOrder(String order) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select " + COLUMNS
                        + " from refund_request where ma_don = :id order by created_at, ma_hoan_tien")
                .setParameter("id", order)
                .getResultList();
        List<Entry> entries = new ArrayList<>();
        for (Object[] row : rows) {
            entries.add(map(row));
        }
        return entries;
    }

    public boolean ticketBlocked(String ticket) {
        List<?> orders = em.createNativeQuery("select ma_don from ve_xe where ma_ve = :ticket")
                .setParameter("ticket", ticket)
                .getResultList();
        if (orders.isEmpty()) {
            return false;
        }
        for (Entry entry : forOrder((String) orders.getFirst())) {
            if (BLOCKING.contains(entry.state())) {
                for (Line line : entry.lines()) {
                    if (line.maVe().equals(ticket)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void insert(Entry value) {
        em.createNativeQuery("""
                        insert into refund_request (
                            ma_hoan_tien, ma_don, ma_giao_dich_goc, ma_nguoi_yeu_cau, ma_chinh_sach,
                            ly_do, so_tien_hoan, trang_thai, chi_tiet, lich_su, ma_request_cong,
                            ma_giao_dich_cong, loai_hoan_toan_phan, ly_do_loi, created_at, confirmed_at)
                        values (:id, :order, :payment, :user, :policy, :reason, :amount, :state,
                            cast(:lines as jsonb), cast(:history as jsonb), :request, :provider,
                            :full, :error, :created, :confirmed)
                        """)
                .setParameter("id", value.id())
                .setParameter("order", value.orderId())
                .setParameter("payment", value.paymentId())
                .setParameter("user", value.userId())
                .setParameter("policy", value.policyId())
                .setParameter("reason", value.reason())
                .setParameter("amount", value.amount())
                .setParameter("state", value.state())
                .setParameter("lines", json(value.lines()))
                .setParameter("history", json(value.history()))
                .setParameter("request", value.requestId())
                .setParameter("provider", value.providerId())
                .setParameter("full", value.fullRefund())
                .setParameter("error", value.error())
                .setParameter("created", value.created())
                .setParameter("confirmed", value.confirmed())
                .executeUpdate();
    }

    public void update(Entry value) {
        em.createNativeQuery("""
                        update refund_request
                        set trang_thai = :state, lich_su = cast(:history as jsonb),
                            ma_request_cong = :request, ma_giao_dich_cong = :provider,
                            ly_do_loi = :error, confirmed_at = :confirmed
                        where ma_hoan_tien = :id
                        """)
                .setParameter("id", value.id())
                .setParameter("state", value.state())
                .setParameter("history", json(value.history()))
                .setParameter("request", value.requestId())
                .setParameter("provider", value.providerId())
                .setParameter("error", value.error())
                .setParameter("confirmed", value.confirmed())
                .executeUpdate();
    }

    public DonDatVe findOrder(String id) {
        return em.find(DonDatVe.class, id);
    }

    public VeXe findTicket(String id) {
        VeXe ticket = em.find(VeXe.class, id);
        if (ticket != null) {
            em.refresh(ticket);
            ticket.donDatVe = (DonDatVe) org.hibernate.Hibernate.unproxy(ticket.donDatVe);
            ticket.chuyenXe = (ChuyenXe) org.hibernate.Hibernate.unproxy(ticket.chuyenXe);
        }
        return ticket;
    }

    public boolean hasCancellationWork(String orderId, String tripId) {
        return !em.createNativeQuery("""
                        select ma_don from trip_cancellation_work
                        where ma_don = :order and ma_chuyen = :trip
                        """)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .getResultList().isEmpty();
    }

    public boolean hasOtherTripData(String orderId, String tripId) {
        long tickets = em.createQuery("""
                        select count(v) from VeXe v
                        where v.donDatVe.maDon = :order and v.chuyenXe.maChuyen <> :trip
                        """, Long.class)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .getSingleResult();
        long holds = em.createQuery("""
                        select count(h) from GiuCho h
                        where h.donDatVe.maDon = :order and h.chuyenXe.maChuyen <> :trip
                        """, Long.class)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .getSingleResult();
        return tickets > 0 || holds > 0;
    }

    public long countTicketsForTrip(String orderId, String tripId) {
        return em.createQuery("""
                        select count(v) from VeXe v
                        where v.donDatVe.maDon = :order and v.chuyenXe.maChuyen = :trip
                        """, Long.class)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .getSingleResult();
    }

    public long countHoldsForTrip(String orderId, String tripId) {
        return em.createQuery("""
                        select count(h) from GiuCho h
                        where h.donDatVe.maDon = :order and h.chuyenXe.maChuyen = :trip
                        """, Long.class)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .getSingleResult();
    }

    public long countBlockingForOrder(String orderId) {
        return ((Number) em.createNativeQuery("""
                        select count(*) from refund_request
                        where ma_don = :order and trang_thai in ('CHO_XU_LY', 'CAN_DOI_SOAT')
                        """)
                .setParameter("order", orderId)
                .getSingleResult()).longValue();
    }

    public List<?> cancellationTripIds(String orderId) {
        return em.createNativeQuery("select ma_chuyen from trip_cancellation_work where ma_don = :order")
                .setParameter("order", orderId)
                .getResultList();
    }

    public long countIssuedTicketsForTrip(String orderId, String tripId) {
        return em.createQuery("""
                        select count(v) from VeXe v
                        where v.donDatVe.maDon = :order and v.chuyenXe.maChuyen = :trip
                          and v.trangThai in :states
                        """, Long.class)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .setParameter("states", List.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE))
                .getSingleResult();
    }

    public void updateCancellationWork(String orderId, String tripId, String state, Instant now) {
        em.createNativeQuery("""
                        update trip_cancellation_work
                        set trang_thai = :state, last_error = null, updated_at = :at
                        where ma_don = :order and ma_chuyen = :trip
                        """)
                .setParameter("state", state)
                .setParameter("at", now)
                .setParameter("order", orderId)
                .setParameter("trip", tripId)
                .executeUpdate();
    }

    public List<?> cancellationTripsForRefund(String orderId) {
        return em.createNativeQuery("""
                        select w.ma_chuyen from trip_cancellation_work w
                        join refund_request r on r.ma_don = w.ma_don
                        where w.ma_don = :order and r.ly_do like 'NHA_XE_HUY_CHUYEN:%'
                        limit 1
                        """)
                .setParameter("order", orderId)
                .getResultList();
    }

    private static Entry map(Object[] row) {
        try {
            List<Line> lines = JSON.readValue((String) row[8], new TypeReference<List<Line>>() {});
            List<History> history = JSON.readValue((String) row[9], new TypeReference<List<History>>() {});
            return new Entry(
                    (String) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
                    (String) row[5], (BigDecimal) row[6], (String) row[7], lines, history,
                    (String) row[10], (String) row[11], (Boolean) row[12], (String) row[13],
                    PaymentGatewayMetadataRepository.instant(row[14]),
                    PaymentGatewayMetadataRepository.instant(row[15]));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid persisted refund data", e);
        }
    }

    public static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot serialize refund data", e);
        }
    }
}
