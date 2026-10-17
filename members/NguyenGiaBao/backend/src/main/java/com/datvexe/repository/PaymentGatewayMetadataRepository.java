package com.datvexe.repository;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;

/** Durable provider identifiers. Uses the caller's payment/refund transaction. */
public final class PaymentGatewayMetadataRepository {
    public record Metadata(String merchantCode, Instant createdAt, String transactionNo) {}
    private final EntityManager em;
    public PaymentGatewayMetadataRepository(EntityManager em) { this.em = em; }
    public void save(String id, String merchant, Instant created) {
        em.createNativeQuery("insert into payment_gateway_metadata(ma_giao_dich,merchant_code,request_created_at) values (:id,:merchant,:created)")
                .setParameter("id",id).setParameter("merchant",merchant).setParameter("created",created).executeUpdate();
    }
    public Metadata find(String id) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("select merchant_code,request_created_at,provider_transaction_no from payment_gateway_metadata where ma_giao_dich=:id")
                .setParameter("id",id).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.getFirst();
        return new Metadata((String)row[0], instant(row[1]), (String)row[2]);
    }
    public void confirmed(String id, String providerId) {
        em.createNativeQuery("update payment_gateway_metadata set provider_transaction_no=:provider where ma_giao_dich=:id")
                .setParameter("id",id).setParameter("provider",providerId).executeUpdate();
    }
    static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant time) return time;
        if (value instanceof OffsetDateTime time) return time.toInstant();
        if (value instanceof java.sql.Timestamp time) return time.toInstant();
        return OffsetDateTime.parse(value.toString()).toInstant();
    }
}
