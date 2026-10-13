package com.datvexe.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class BrevoClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Config config;
    private final HttpClient client;
    private final URI endpoint;
    public record Config(String apiKey, String senderEmail, String senderName, boolean enabled) {
        public static Config fromEnvironment() {
            return new Config(System.getenv().getOrDefault("BREVO_API_KEY", ""),
                System.getenv().getOrDefault("BREVO_SENDER_EMAIL", ""),
                System.getenv().getOrDefault("BREVO_SENDER_NAME", "Vexexin - Hỗ trợ khách hàng"),
                Boolean.parseBoolean(System.getenv().getOrDefault("EMAIL_NOTIFICATIONS_ENABLED", "true")));
        }
        public boolean ready() { return enabled && !apiKey.isBlank() && !senderEmail.isBlank() && !senderName.isBlank(); }
        @Override public String toString() { return "BrevoConfig[apiKey=[REDACTED], enabled=" + enabled + "]"; }
    }
    public record Attachment(String name, String content) {}
    public record Message(String orderId, String recipient, String name, String subject, String html, String text, List<Attachment> attachments) {
        public Message { attachments = List.copyOf(attachments == null ? List.of() : attachments); }
        public Message(String orderId, String recipient, String name, String subject, String html, String text) {
            this(orderId, recipient, name, subject, html, text, List.of());
        }
    }
    public static final class SendFailure extends Exception {
        public final String code;
        public final boolean retry;
        public final boolean uncertain;
        public SendFailure(String code, boolean retry, boolean uncertain) { super(code); this.code = code; this.retry = retry; this.uncertain = uncertain; }
    }
    public BrevoClient(Config config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), URI.create("https://api.brevo.com/v3/smtp/email"));
    }
    public BrevoClient(Config config, HttpClient client, URI endpoint) { this.config = config; this.client = client; this.endpoint = endpoint; }
    public boolean ready() { return config.ready(); }
    public String send(Message message) throws SendFailure {
        if (!ready()) throw new SendFailure("EMAIL_NOT_CONFIGURED", true, false);
        try {
            String body = JSON.writeValueAsString(Map.of(
                "sender", Map.of("name", config.senderName(), "email", config.senderEmail()),
                "to", List.of(Map.of("email", message.recipient(), "name", message.name())),
                "subject", message.subject(), "htmlContent", message.html(), "textContent", message.text(),
                "headers", Map.of("idempotencyKey", UUID.nameUUIDFromBytes(("vexexin-receipt:" + message.orderId()).getBytes(StandardCharsets.UTF_8)).toString()), "tags", List.of("booking-receipt"), "attachment", message.attachments()));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("api-key", config.apiKey()).header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String id = JSON.readTree(response.body()).path("messageId").asText("");
                if (id.isBlank()) throw new SendFailure("BREVO_RESPONSE_UNKNOWN", false, true);
                return id;
            }
            throw new SendFailure("BREVO_HTTP_" + response.statusCode(), response.statusCode() == 429, response.statusCode() >= 500);
        } catch (HttpTimeoutException e) { throw new SendFailure("BREVO_TIMEOUT", false, true); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SendFailure("BREVO_INTERRUPTED", false, true); }
        catch (IOException e) { throw new SendFailure("BREVO_NETWORK_UNKNOWN", false, true); }
    }
}
