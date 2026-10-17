package com.datvexe.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datvexe.payment.VNPayClient.RefundCommand;
import com.datvexe.payment.VNPayClient.RefundResult;
import com.datvexe.security.AccountException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class VNPayRefundClientTest {
    private static final String SECRET = "unit-test-secret";
    private static final String REFUND_REQUEST_ID = "A".repeat(32);
    private static final VNPayConfig CONFIG = new VNPayConfig("TESTCODE", SECRET,
            "http://localhost:8080/api/thanh-toan/return/vnpay",
            VNPayConfig.SANDBOX_PAY_URL, "http://localhost/query", Duration.ofSeconds(3));
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:00Z");
    private static final Instant NOW = Instant.parse("2026-01-02T03:05:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void refundUsesCanonicalSignatureAndReturnsProviderTransactionNumber() throws Exception {
        Map<String, String> response = signedRefundResponse("00", "00", "7654321", "02", "1250000");
        StubHttpClient transport = new StubHttpClient(toJson(response));
        VNPayClient client = client(transport);

        RefundResult result = client.refund(command("12500", "12500", true));

        assertEquals(REFUND_REQUEST_ID, result.requestId());
        assertEquals("ORD-1", result.originalReference());
        assertEquals(new BigDecimal("12500"), result.amountVnd());
        assertEquals("00", result.responseCode());
        assertEquals("00", result.transactionStatus());
        assertEquals("7654321", result.transactionNo());
        assertEquals(Instant.parse("2026-01-02T03:05:00Z"), result.paidAt());
        assertTrue(result.correlated());

        assertEquals("POST", transport.lastRequest.method());
        assertEquals(Duration.ofSeconds(3), transport.lastRequest.timeout().orElseThrow());
        assertEquals("application/json", transport.lastRequest.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("http://localhost/query", transport.lastRequest.uri().toString());
        Map<String, String> sent = readBody(transport.lastRequest);
        assertEquals(REFUND_REQUEST_ID, sent.get("vnp_RequestId"));
        assertEquals("2.1.0", sent.get("vnp_Version"));
        assertEquals("refund", sent.get("vnp_Command"));
        assertEquals("02", sent.get("vnp_TransactionType"));
        assertEquals("ORD-1", sent.get("vnp_TxnRef"));
        assertEquals("1250000", sent.get("vnp_Amount"));
        assertEquals("1234567", sent.get("vnp_TransactionNo"));
        assertEquals("20260102100400", sent.get("vnp_TransactionDate"));
        assertEquals("20260102100500", sent.get("vnp_CreateDate"));
        assertEquals(refundRequestHash(sent), sent.get("vnp_SecureHash"));
    }

    @Test
    void signedSuccessfulRefundRequiresNumericProviderTransactionNumber() {
        Map<String, String> missingNumber = signedRefundResponse("00", "00", "", "02", "1250000");
        assertInvalidRefundResponse(missingNumber);

        Map<String, String> nonNumericNumber = signedRefundResponse("00", "00", "refund-123", "02", "1250000");
        assertInvalidRefundResponse(nonNumericNumber);
    }

    @Test
    void refundPreservesProviderPendingStatuses() {
        for (String status : List.of("05", "06")) {
            StubHttpClient transport = new StubHttpClient(toJson(
                    signedRefundResponse("00", status, "7654321", "02", "1250000")));
            RefundResult result = client(transport).refund(command("12500", "12500", true));
            assertEquals("00", result.responseCode());
            assertEquals(status, result.transactionStatus());
            assertEquals("7654321", result.transactionNo());
            assertTrue(result.correlated());
        }
    }

    @Test
    void refundRejectsBadSignatureReferenceAmountAndType() {
        Map<String, String> badSignature = signedRefundResponse("00", "00", "7654321", "02", "1250000");
        badSignature.put("vnp_ResponseCode", "95");
        assertInvalidRefundResponse(badSignature);

        Map<String, String> wrongReference = signedRefundResponse("00", "00", "7654321", "02", "1250000");
        wrongReference.put("vnp_TxnRef", "ORD-OTHER");
        resignRefundResponse(wrongReference);
        assertInvalidRefundResponse(wrongReference);

        Map<String, String> wrongAmount = signedRefundResponse("00", "00", "7654321", "02", "1300000");
        assertInvalidRefundResponse(wrongAmount);

        Map<String, String> wrongType = signedRefundResponse("00", "00", "7654321", "03", "1250000");
        assertInvalidRefundResponse(wrongType);
    }

    @Test
    void timeoutLeavesRefundUnresolved() {
        StubHttpClient transport = new StubHttpClient(toJson(
                signedRefundResponse("00", "00", "7654321", "02", "1250000")));
        transport.sendFailure = new HttpTimeoutException("timeout");
        AccountException error = assertThrows(AccountException.class,
                () -> client(transport).refund(command("12500", "12500", true)));
        assertEquals(502, error.status());
        assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", error.code());
    }

    @Test
    void queryRefundDoesNotTreatOriginalPaymentAsRefundEvenWhenAmountMatches() throws Exception {
        Map<String, String> response = signedQueryResponse("01", "00", "7654321", "1250000");
        StubHttpClient transport = new StubHttpClient(toJson(response));
        RefundCommand command = command("12500", "12500", false);

        RefundResult result = client(transport).queryRefund(command, "7654321");

        assertEquals("01", response.get("vnp_TransactionType"));
        assertEquals("7654321", result.transactionNo());
        assertFalse(result.correlated());
        Map<String, String> sent = readBody(transport.lastRequest);
        assertEquals("querydr", sent.get("vnp_Command"));
        assertEquals("20260102100400", sent.get("vnp_TransactionDate"));
        assertEquals("20260102100500", sent.get("vnp_CreateDate"));
        assertEquals(queryRequestHash(sent), sent.get("vnp_SecureHash"));
        assertNotNull(result.paidAt());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), result.paidAt());
    }

    @Test
    void queryRefundCorrelatesOnlyExactRefundNumberTypeAndAmount() {
        RefundCommand command = command("12000", "12500", false);
        Map<String, String> response = signedQueryResponse("03", "05", "7654321", "1200000");
        StubHttpClient transport = new StubHttpClient(toJson(response));

        RefundResult matching = client(transport).queryRefund(command, "7654321");
        assertTrue(matching.correlated());
        assertEquals("05", matching.transactionStatus());

        assertFalse(client(new StubHttpClient(toJson(response)))
                .queryRefund(command, null).correlated());
        assertFalse(client(new StubHttpClient(toJson(response)))
                .queryRefund(command, "7654322").correlated());

        Map<String, String> wrongAmount = signedQueryResponse("03", "00", "7654321", "1250000");
        assertFalse(client(new StubHttpClient(toJson(wrongAmount)))
                .queryRefund(command, "7654321").correlated());
    }

    @Test
    void queryRefundRejectsInvalidQuerySignatureAndDoesNotInventOriginalDate() throws Exception {
        Map<String, String> response = signedQueryResponse("03", "00", "7654321", "1200000");
        response.put("vnp_TransactionStatus", "06");
        StubHttpClient transport = new StubHttpClient(toJson(response));
        AccountException invalid = assertThrows(AccountException.class,
                () -> client(transport).queryRefund(command("12000", "12500", false), "7654321"));
        assertEquals(502, invalid.status());

        Map<String, String> valid = signedQueryResponse("03", "00", "7654321", "1200000");
        StubHttpClient validTransport = new StubHttpClient(toJson(valid));
        RefundResult result = client(validTransport).queryRefund(command("12000", "12500", false), "7654321");
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), result.paidAt());
        assertEquals("20260102100400", readBody(validTransport.lastRequest).get("vnp_TransactionDate"));
    }

    @Test
    void localValidationRequiresWholeBoundedAmountsAndPersistedRequestId() {
        VNPayClient client = client(new StubHttpClient("{}"));
        assertEquals(400, assertThrows(AccountException.class,
                () -> client.refund(command("0", "12500", false))).status());
        assertEquals(400, assertThrows(AccountException.class,
                () -> client.refund(command("12501", "12500", false))).status());
        RefundCommand invalidId = new RefundCommand("new-id", "ORD-1", BigDecimal.ONE,
                new BigDecimal("12500"), CREATED, "1234567", false, "operator", "127.0.0.1");
        assertEquals(400, assertThrows(AccountException.class, () -> client.refund(invalidId)).status());
    }

    private static void assertInvalidRefundResponse(Map<String, String> response) {
        AccountException error = assertThrows(AccountException.class,
                () -> client(new StubHttpClient(toJson(response))).refund(command("12500", "12500", true)));
        assertEquals(502, error.status());
        assertEquals("PAYMENT_PROVIDER_INVALID_RESPONSE", error.code());
    }

    private static VNPayClient client(StubHttpClient transport) {
        return new VNPayClient(CONFIG, transport, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private static RefundCommand command(String amount, String originalAmount, boolean full) {
        return new RefundCommand(REFUND_REQUEST_ID, "ORD-1",
                new BigDecimal(amount), new BigDecimal(originalAmount), CREATED, "1234567", full,
                "operator-1", "127.0.0.1");
    }

    private static Map<String, String> signedRefundResponse(String responseCode, String status,
            String transactionNo, String transactionType, String amount) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "RESP1");
        fields.put("vnp_Command", "refund");
        fields.put("vnp_ResponseCode", responseCode);
        fields.put("vnp_Message", "Refund processed");
        fields.put("vnp_TmnCode", "TESTCODE");
        fields.put("vnp_TxnRef", "ORD-1");
        fields.put("vnp_Amount", amount);
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20260102100500");
        fields.put("vnp_TransactionNo", transactionNo);
        fields.put("vnp_TransactionType", transactionType);
        fields.put("vnp_TransactionStatus", status);
        fields.put("vnp_OrderInfo", "Hoan tien don hang ORD-1");
        resignRefundResponse(fields);
        return fields;
    }

    private static void resignRefundResponse(Map<String, String> fields) {
        fields.put("vnp_SecureHash", hash(String.join("|", List.of("vnp_ResponseId", "vnp_Command",
                "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount",
                "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
                "vnp_TransactionStatus", "vnp_OrderInfo").stream()
                .map(key -> fields.getOrDefault(key, "")).toList())));
    }

    private static Map<String, String> signedQueryResponse(String type, String status,
            String transactionNo, String amount) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "QUERY-1");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_Message", "Confirm Success");
        fields.put("vnp_TmnCode", "TESTCODE");
        fields.put("vnp_TxnRef", "ORD-1");
        fields.put("vnp_Amount", amount);
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20260102100405");
        fields.put("vnp_TransactionNo", transactionNo);
        fields.put("vnp_TransactionType", type);
        fields.put("vnp_TransactionStatus", status);
        fields.put("vnp_OrderInfo", "Transaction details");
        fields.put("vnp_PromotionCode", "");
        fields.put("vnp_PromotionAmount", "0");
        fields.put("vnp_SecureHash", queryResponseHash(fields));
        return fields;
    }

    private static String refundRequestHash(Map<String, String> fields) {
        return hash(String.join("|", List.of("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
                "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount", "vnp_TransactionNo",
                "vnp_TransactionDate", "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo")
                .stream().map(key -> fields.getOrDefault(key, "")).toList()));
    }

    private static String queryRequestHash(Map<String, String> fields) {
        return hash(String.join("|", List.of("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
                "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo")
                .stream().map(key -> fields.getOrDefault(key, "")).toList()));
    }

    private static String queryResponseHash(Map<String, String> fields) {
        return hash(String.join("|", List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
                "vnp_PromotionCode", "vnp_PromotionAmount").stream()
                .map(key -> fields.getOrDefault(key, "")).toList()));
    }

    private static String hash(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte value : digest) output.append(String.format("%02x", value & 0xff));
            return output.toString();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static String toJson(Map<String, String> fields) {
        try {
            return JSON.writeValueAsString(fields);
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    private static Map<String, String> readBody(HttpRequest request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
            @Override public void onError(Throwable error) { throw new AssertionError(error); }
            @Override public void onComplete() {}
        });
        return JSON.readValue(bytes.toByteArray(), JSON.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, String.class));
    }

    private static class StubHttpClient extends HttpClient {
        private final String responseJson;
        private HttpRequest lastRequest;
        private IOException sendFailure;

        private StubHttpClient(String responseJson) { this.responseJson = responseJson; }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            lastRequest = request;
            if (sendFailure != null) throw sendFailure;
            HttpResponse.ResponseInfo responseInfo = new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return 200; }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
                @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            };
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(responseInfo);
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean delivered;
                @Override public void request(long count) {
                    if (delivered || count <= 0) return;
                    delivered = true;
                    subscriber.onNext(List.of(ByteBuffer.wrap(body)));
                    subscriber.onComplete();
                }
                @Override public void cancel() { delivered = true; }
            });
            T decoded = subscriber.getBody().toCompletableFuture().join();
            return new StubResponse<>(request, decoded);
        }

        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private record StubResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {
        @Override public int statusCode() { return 200; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
