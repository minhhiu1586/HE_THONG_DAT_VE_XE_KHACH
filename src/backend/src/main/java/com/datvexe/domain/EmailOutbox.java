package com.datvexe.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "EmailOutbox")
@Table(name = "email_outbox")
public class EmailOutbox {
    @Id @Column(name = "ma_don", length = 40) public String maDon;
    @Column(name = "recipient", nullable = false, length = 254) public String recipient;
    @Column(name = "recipient_name", nullable = false, length = 160) public String recipientName;
    @Column(name = "subject", nullable = false, length = 255) public String subject;
    @Column(name = "html_content", nullable = false, columnDefinition = "text") public String htmlContent;
    @Column(name = "text_content", nullable = false, columnDefinition = "text") public String textContent;
    @Column(name = "status", nullable = false, length = 20) public String status = "PENDING";
    @Column(name = "attempts", nullable = false) public int attempts;
    @Column(name = "next_attempt_at", nullable = false) public LocalDateTime nextAttemptAt;
    @Column(name = "created_at", nullable = false) public LocalDateTime createdAt;
    @Column(name = "started_at") public LocalDateTime startedAt;
    @Column(name = "sent_at") public LocalDateTime sentAt;
    @Column(name = "provider_message_id", length = 255) public String providerMessageId;
    @Column(name = "last_error", length = 120) public String lastError;
    @Column(name = "attachments_json", columnDefinition = "text") public String attachmentsJson = "[]";
    public EmailOutbox() {}
}
