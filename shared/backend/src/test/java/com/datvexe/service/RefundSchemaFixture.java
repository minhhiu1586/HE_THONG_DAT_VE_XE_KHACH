package com.datvexe.service;

import jakarta.persistence.EntityManager;

/** Empty technical tables for existing H2 booking/payment regression tests. */
public final class RefundSchemaFixture {
    private RefundSchemaFixture() {}
    public static void create(EntityManager em) {
        em.createNativeQuery("CREATE TABLE payment_gateway_metadata(ma_giao_dich varchar(80) primary key,merchant_code varchar(32) not null,request_created_at timestamp with time zone not null,provider_transaction_no varchar(80))").executeUpdate();
        em.createNativeQuery("CREATE TABLE refund_request(ma_hoan_tien varchar(40) primary key,ma_don varchar(40),ma_giao_dich_goc varchar(80),ma_nguoi_yeu_cau varchar(40),ma_chinh_sach varchar(40),ly_do varchar(500),so_tien_hoan numeric(14,0),trang_thai varchar(24),chi_tiet text,lich_su text,ma_request_cong varchar(32),ma_giao_dich_cong varchar(80),loai_hoan_toan_phan boolean,ly_do_loi varchar(120),created_at timestamp with time zone,confirmed_at timestamp with time zone)").executeUpdate();
        em.createNativeQuery("CREATE ALIAS IF NOT EXISTS PG_ADVISORY_XACT_LOCK FOR 'com.datvexe.service.RefundSchemaFixture.advisoryLock'").executeUpdate();
    }
    public static long advisoryLock(long ignored) { return 0; }
}
