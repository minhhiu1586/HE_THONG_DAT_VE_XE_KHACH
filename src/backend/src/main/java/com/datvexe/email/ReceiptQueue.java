package com.datvexe.email;

import com.datvexe.domain.EmailOutbox;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import jakarta.persistence.EntityManager;
import java.util.List;

public final class ReceiptQueue {
    private ReceiptQueue() {}
    public static void enqueue(EntityManager em, DonDatVe order, ThanhToan payment) {
        if (order.trangThai != TrangThaiDon.DA_THANH_TOAN || payment.trangThai != TrangThaiThanhToan.THANH_CONG
            || order.khachDatEmail == null || order.khachDatEmail.isBlank() || em.find(EmailOutbox.class, order.maDon) != null) return;
        List<VeXe> tickets = em.createQuery("select v from VeXe v where v.donDatVe.maDon=:id order by v.ghe.viTri", VeXe.class)
            .setParameter("id", order.maDon).getResultList();
        if (tickets.isEmpty() || tickets.stream().anyMatch(t -> t.trangThai != TrangThaiVe.DA_XUAT_VE)) return;
        ReceiptTemplate.Content content = ReceiptTemplate.render(order, payment, tickets, false);
        EmailOutbox email = new EmailOutbox();
        email.maDon = order.maDon; email.recipient = order.khachDatEmail; email.recipientName = order.khachDatTen;
        email.subject = content.subject(); email.htmlContent = content.html(); email.textContent = content.text();
        email.createdAt = payment.thoiGianThanhToan; email.nextAttemptAt = payment.thoiGianThanhToan;
        try {
            email.attachmentsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tickets.stream()
                .map(t -> new BrevoClient.Attachment("Ve-" + t.maTraCuu + ".png", com.datvexe.ticket.TicketQr.base64(t.maQR))).toList());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Unable to prepare ticket attachments",e); }
        em.persist(email);
    }
}
