package com.datvexe.email;

import com.datvexe.domain.EmailOutbox;
import jakarta.persistence.*;
import java.time.*;
import java.util.List;

public final class EmailDispatcher {
    public interface Sender { String send(BrevoClient.Message message) throws BrevoClient.SendFailure; }
    private final EntityManagerFactory factory;
    private final Sender sender;
    private final Clock clock;
    public EmailDispatcher(EntityManagerFactory factory, Sender sender, Clock clock) { this.factory = factory; this.sender = sender; this.clock = clock; }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    public void runOnce() {
        List<String> ids;
        try (EntityManager em = factory.createEntityManager()) {
            ids = em.createQuery("select e.maDon from EmailOutbox e where (e.status='PENDING' and e.nextAttemptAt<=:now) or (e.status='SENDING' and e.startedAt<:stale) order by e.createdAt", String.class)
                .setParameter("now", now()).setParameter("stale", now().minusMinutes(2)).setMaxResults(5).getResultList();
        }
        for (String id : ids) dispatch(id);
    }
    private static List<BrevoClient.Attachment> attachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
            new com.fasterxml.jackson.core.type.TypeReference<List<BrevoClient.Attachment>>() {}); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored receipt attachments", e); }
    }
    private void dispatch(String id) {
        BrevoClient.Message message;
        int attempt;
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try {
                EmailOutbox email = em.find(EmailOutbox.class, id, LockModeType.PESSIMISTIC_WRITE);
                if (email == null) { tx.commit(); return; }
                if (email.status.equals("SENDING") && email.startedAt != null && email.startedAt.isBefore(now().minusMinutes(2))) {
                    email.status = "UNKNOWN"; email.lastError = "WORKER_INTERRUPTED"; tx.commit(); return;
                }
                if (!email.status.equals("PENDING") || email.nextAttemptAt.isAfter(now())) { tx.commit(); return; }
                email.status = "SENDING"; email.startedAt = now(); email.attempts++; attempt = email.attempts;
                message = new BrevoClient.Message(email.maDon, email.recipient, email.recipientName, email.subject, email.htmlContent, email.textContent, attachments(email.attachmentsJson));
                tx.commit();
            } catch (RuntimeException e) { if (tx.isActive()) tx.rollback(); throw e; }
        }
        String messageId = null, error = null, status = "SENT";
        try { messageId = sender.send(message); }
        catch (BrevoClient.SendFailure e) { error = e.code; status = e.uncertain ? "UNKNOWN" : e.retry && attempt < 5 ? "PENDING" : "FAILED"; }
        catch (RuntimeException e) { error = "EMAIL_DELIVERY_UNKNOWN"; status = "UNKNOWN"; }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try {
                EmailOutbox email = em.find(EmailOutbox.class, id, LockModeType.PESSIMISTIC_WRITE);
                if (email != null && email.status.equals("SENDING") && email.attempts == attempt) {
                    email.status = status; email.lastError = error; email.providerMessageId = messageId;
                    if (status.equals("SENT")) email.sentAt = now();
                    if (status.equals("PENDING")) email.nextAttemptAt = now().plusSeconds(20L * (1L << Math.min(attempt, 4)));
                }
                tx.commit();
            } catch (RuntimeException e) { if (tx.isActive()) tx.rollback(); throw e; }
        }
    }
}
