package com.datvexe.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datvexe.security.AccountException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class VNPayClientTest {
    private static final String SECRET = "unit-test-secret";
    private static final VNPayConfig CONFIG = new VNPayConfig("TESTCODE", SECRET,
            "http://localhost:8080/api/thanh-toan/return/vnpay",
            VNPayConfig.SANDBOX_PAY_URL, "http://localhost/query", Duration.ofSeconds(3));

    @Test
    void payUrlUsesSortedEncodedParametersAndVietnameseTime() {
        VNPayClient client = new VNPayClient(CONFIG, new StubHttpClient("{}"));
        Instant createdAt = Instant.parse("2026-01-01T00:30:00Z");
        Instant expiresAt = Instant.parse("2026-01-01T00:45:00Z");

        String url = client.payUrl("ORD-1", new BigDecimal("123.00"), createdAt, expiresAt, "127.0.0.1");
        String query = url.substring(url.indexOf('?') + 1);
        String signedData = query.substring(0, query.lastIndexOf("&vnp_SecureHash="));
        Map<String, String> fields = decodeQuery(signedData);

        assertEquals("12300", fields.get("vnp_Amount"));
        assertEquals("20260101073000", fields.get("vnp_CreateDate"));
        assertEquals("20260101074500", fields.get("vnp_ExpireDate"));
        assertEquals("TESTCODE", fields.get("vnp_TmnCode"));
        assertEquals("http://localhost:8080/api/thanh-toan/return/vnpay", fields.get("vnp_ReturnUrl"));
        assertEquals(expectedHash(signedData), decodeQuery(query).get("vnp_SecureHash"));
        assertEquals(fields.keySet().stream().sorted().toList(), new ArrayList<>(fields.keySet()));
    }

    @Test
    void payUrlRejectsFractionalAndProviderOverflowAmounts() {
        VNPayClient client = new VNPayClient(CONFIG, new StubHttpClient("{}"));
        Instant now = Instant.parse("2026-01-01T00:30:00Z");

        assertThrows(AccountException.class,
                () -> client.payUrl("ORD-1", new BigDecimal("12.50"), now, now.plusSeconds(900), "127.0.0.1"));
        assertThrows(AccountException.class,
                () -> client.payUrl("ORD-1", new BigDecimal("10000000000"), now, now.plusSeconds(900), "127.0.0.1"));
    }

    @Test
    void payUrlEncodesNonAsciiReturnUrlAndRejectsInvalidReturnUrlScheme() {
        VNPayConfig unicode = new VNPayConfig("TESTCODE", SECRET,
                "https://example.test/return?note=Tiếng", VNPayConfig.SANDBOX_PAY_URL,
                "http://localhost/query", Duration.ofSeconds(3));
        VNPayClient client = new VNPayClient(unicode, new StubHttpClient("{}"));
        Instant now = Instant.parse("2026-01-01T00:30:00Z");
        assertTrue(client.payUrl("ORD-1", BigDecimal.TEN, now, now.plusSeconds(60), "127.0.0.1")
                .contains("Ti%E1%BA%BFng"));

        VNPayConfig invalidReturn = new VNPayConfig("TESTCODE", SECRET, "javascript:alert(1)",
                VNPayConfig.SANDBOX_PAY_URL, "http://localhost/query", Duration.ofSeconds(3));
        AccountException failure = assertThrows(AccountException.class, () -> new VNPayClient(invalidReturn,
                new StubHttpClient("{}" )).payUrl("ORD-1", BigDecimal.TEN, now, now.plusSeconds(60), "127.0.0.1"));
        assertEquals("PAYMENT_NOT_CONFIGURED", failure.code());
    }

    @Test
    void callbackRequiresValidSignatureMerchantAndWholeVndAmount() {
        VNPayClient client = new VNPayClient(CONFIG, new StubHttpClient("{}"));
        Map<String, String> callback = signedCallback();

        VNPayClient.VerifiedPayment verified = client.verifyCallback(callback);
        assertEquals("ORD-1", verified.reference());
        assertEquals(12500L, verified.amountVnd());
        assertEquals("00", verified.responseCode());
        assertEquals("00", verified.transactionStatus());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), verified.paidAt());
        assertEquals("TX-123", verified.providerTransaction());

        Map<String, String> tampered = new LinkedHashMap<>(callback);
        tampered.put("vnp_ResponseCode", "24");
        assertThrows(AccountException.class, () -> client.verifyCallback(tampered));

        Map<String, String> wrongMerchant = signedCallback();
        wrongMerchant.put("vnp_TmnCode", "OTHER001");
        wrongMerchant.put("vnp_SecureHash", callbackHash(wrongMerchant));
        assertThrows(AccountException.class, () -> client.verifyCallback(wrongMerchant));

        Map<String, String> fractionalAmount = signedCallback();
        fractionalAmount.put("vnp_Amount", "1250001");
        fractionalAmount.put("vnp_SecureHash", callbackHash(fractionalAmount));
        assertThrows(AccountException.class, () -> client.verifyCallback(fractionalAmount));

        Map<String, String> wrongCurrency = signedCallback();
        wrongCurrency.put("vnp_CurrCode", "USD");
        wrongCurrency.put("vnp_SecureHash", callbackHash(wrongCurrency));
        assertThrows(AccountException.class, () -> client.verifyCallback(wrongCurrency));

        Map<String, String> nonProviderField = signedCallback();
        nonProviderField.put("tracking", "outside-provider");
        assertThrows(AccountException.class, () -> client.verifyCallback(nonProviderField));
    }

    @Test
    void callbackCanCheckExpectedOrderValues() {
        VNPayClient client = new VNPayClient(CONFIG, new StubHttpClient("{}"));
        Map<String, String> callback = signedCallback();

        assertEquals("ORD-1", client.verifyCallback(callback, "ORD-1", new BigDecimal("12500")).reference());
        assertThrows(AccountException.class,
                () -> client.verifyCallback(callback, "ORD-2", new BigDecimal("12500")));
        assertThrows(AccountException.class,
                () -> client.verifyCallback(callback, "ORD-1", new BigDecimal("12501")));
    }

    @Test
    void missingCredentialsLeaveApplicationConstructibleAndFailClosedOnUse() {
        VNPayConfig unconfigured = new VNPayConfig(null, null, null, null, null, null);
        VNPayClient client = new VNPayClient(unconfigured, new StubHttpClient("{}"));
        assertEquals(VNPayConfig.DEFAULT_RETURN_URL, unconfigured.returnUrl());
        assertFalse(unconfigured.toString().contains(SECRET));
        assertTrue(CONFIG.toString().contains("[REDACTED]"));
        AccountException failure = assertThrows(AccountException.class,
                () -> client.payUrl("ORD-1", BigDecimal.TEN, Instant.now(), Instant.now().plusSeconds(60), "127.0.0.1"));
        assertEquals(503, failure.status());
        assertEquals("PAYMENT_NOT_CONFIGURED", failure.code());
    }

    @Test
    void queryDrUsesOriginalCreateDateTimeoutAndVerifiesSignedResponse() {
        Map<String, String> response = signedQueryResponse();
        StubHttpClient transport = new StubHttpClient(toJson(response));
        VNPayClient client = new VNPayClient(CONFIG, transport,
                Clock.fixed(Instant.parse("2026-01-02T03:05:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        VNPayClient.VerifiedPayment verified = client.queryDr("ORD-1", new BigDecimal("12500"),
                Instant.parse("2026-01-02T03:04:00Z"), "127.0.0.1");

        assertEquals("ORD-1", verified.reference());
        assertEquals(12500L, verified.amountVnd());
        assertEquals("00", verified.responseCode());
        assertEquals("00", verified.transactionStatus());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), verified.paidAt());
        assertNotNull(transport.lastRequest);
        assertEquals("POST", transport.lastRequest.method());
        assertEquals(Duration.ofSeconds(3), transport.lastRequest.timeout().orElseThrow());
        assertEquals("application/json", transport.lastRequest.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("http://localhost/query", transport.lastRequest.uri().toString());

        Map<String, String> changed = new LinkedHashMap<>(response);
        changed.put("vnp_TransactionStatus", "01");
        transport.responseJson = toJson(changed);
        AccountException invalid = assertThrows(AccountException.class,
                () -> client.queryDr("ORD-1", new BigDecimal("12500"),
                        Instant.parse("2026-01-02T03:04:00Z"), "127.0.0.1"));
        assertEquals("PAYMENT_PROVIDER_INVALID_RESPONSE", invalid.code());

        Map<String, String> wrongCommand = new LinkedHashMap<>(response);
        wrongCommand.put("vnp_Command", "pay");
        wrongCommand.put("vnp_SecureHash", queryResponseHash(wrongCommand));
        transport.responseJson = toJson(wrongCommand);
        AccountException wrongCommandFailure = assertThrows(AccountException.class,
                () -> client.queryDr("ORD-1", new BigDecimal("12500"),
                        Instant.parse("2026-01-02T03:04:00Z"), "127.0.0.1"));
        assertEquals("PAYMENT_PROVIDER_INVALID_RESPONSE", wrongCommandFailure.code());

        Map<String, String> queryError = new LinkedHashMap<>(response);
        queryError.put("vnp_ResponseCode", "91");
        queryError.put("vnp_SecureHash", queryResponseHash(queryError));
        transport.responseJson = toJson(queryError);
        AccountException unresolved = assertThrows(AccountException.class,
                () -> client.queryDr("ORD-1", new BigDecimal("12500"),
                        Instant.parse("2026-01-02T03:04:00Z"), "127.0.0.1"));
        assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", unresolved.code());

        transport.responseJson = toJson(response);
        transport.sendFailure = new HttpTimeoutException("timeout");
        AccountException timeout = assertThrows(AccountException.class,
                () -> client.queryDr("ORD-1", new BigDecimal("12500"),
                        Instant.parse("2026-01-02T03:04:00Z"), "127.0.0.1"));
        assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", timeout.code());
        transport.sendFailure = null;
        transport.statusCode = 503;
        AccountException httpFailure = assertThrows(AccountException.class,
                () -> client.queryDr("ORD-1", new BigDecimal("12500"),
                        Instant.parse("2026-01-02T03:04:00Z"), "127.0.0.1"));
        assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", httpFailure.code());
        transport.statusCode = 200;

        AccountException missingOriginalDate = assertThrows(AccountException.class,
                () -> client.queryDr("ORD-1", new BigDecimal("12500"), null, "127.0.0.1"));
        assertEquals("VALIDATION_ERROR", missingOriginalDate.code());
    }

    private static Map<String, String> signedCallback() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TmnCode", "TESTCODE");
        fields.put("vnp_TxnRef", "ORD-1");
        fields.put("vnp_Amount", "1250000");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_PayDate", "20260102100405");
        fields.put("vnp_TransactionNo", "TX-123");
        fields.put("vnp_SecureHashType", "HMACSHA512");
        fields.put("vnp_SecureHash", callbackHash(fields));
        return fields;
    }

    private static String callbackHash(Map<String, String> fields) {
        Map<String, String> signed = new LinkedHashMap<>(fields);
        signed.remove("vnp_SecureHash");
        signed.remove("vnp_SecureHashType");
        String data = signed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElse("");
        return expectedHash(data);
    }

    private static Map<String, String> signedQueryResponse() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "RESP-1");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_Message", "Confirm Success");
        fields.put("vnp_TmnCode", "TESTCODE");
        fields.put("vnp_TxnRef", "ORD-1");
        fields.put("vnp_Amount", "1250000");
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20260102100405");
        fields.put("vnp_TransactionNo", "12345");
        fields.put("vnp_TransactionType", "01");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_OrderInfo", "Thanh toan don hang ORD-1");
        fields.put("vnp_PromotionCode", "");
        fields.put("vnp_PromotionAmount", "0");
        fields.put("vnp_SecureHash", queryResponseHash(fields));
        return fields;
    }

    private static String queryResponseHash(Map<String, String> fields) {
        String data = List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
                "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
                "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode",
                "vnp_PromotionAmount").stream().map(fields::get).reduce((left, right) -> left + "|" + right).orElse("");
        return expectedHash(data);
    }

    private static String expectedHash(String data) {
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

    private static Map<String, String> decodeQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(parts.length == 1 ? "" : parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String toJson(Map<String, String> fields) {
        String pairs = fields.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce((left, right) -> left + "," + right).orElse("");
        return "{" + pairs + "}";
    }

    private static final class StubHttpClient extends HttpClient {
        private String responseJson;
        private HttpRequest lastRequest;
        private int statusCode = 200;
        private IOException sendFailure;

        private StubHttpClient(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            lastRequest = request;
            if (sendFailure != null) throw sendFailure;
            HttpResponse.ResponseInfo responseInfo = new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return statusCode; }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
                @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            };
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(responseInfo);
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean delivered;
                @Override public void request(long count) {
                    if (delivered || count <= 0) return;
                    delivered = true;
                    subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
                    subscriber.onComplete();
                }
                @Override public void cancel() { delivered = true; }
            });
            T body = subscriber.getBody().toCompletableFuture().join();
            return new StubResponse<>(request, body, statusCode);
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
                HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubResponse<T>(HttpRequest request, T body, int statusCode) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
