package com.datvexe.payment;

import com.datvexe.security.AccountException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Creates VNPay payment URLs and verifies VNPay return/query responses. */
public final class VNPayClient {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigInteger MAX_PROVIDER_AMOUNT = new BigInteger("999999999999");
    private static final List<String> QUERY_RESPONSE_CHECK_FIELDS = List.of(
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
            "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode",
            "vnp_PromotionAmount");

    private final VNPayConfig config;
    private final HttpClient httpClient;
    private final Clock clock;

    public VNPayClient(VNPayConfig config) {
        this(config, newHttpClient(config), Clock.system(VN_ZONE));
    }

    public VNPayClient(VNPayConfig config, HttpClient httpClient) {
        this(config, httpClient, Clock.system(VN_ZONE));
    }

    /** Injection point for deterministic tests and embedders with a configured client. */
    public VNPayClient(VNPayConfig config, HttpClient httpClient, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Builds the signed redirect URL. Amounts are whole VND and must fit the 12-digit
     * provider amount after multiplication by 100.
     */
    public String payUrl(String reference, BigDecimal amountVnd, Instant createdAt,
            Instant expiresAt, String ip) {
        requireConfigured();
        requireValidReturnUrl();
        requireReference(reference);
        requireIp(ip);
        long amount = toWholeVnd(amountVnd);
        String providerAmount = providerAmount(amount);
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw invalid("expiresAt", "Thời hạn thanh toán VNPay không hợp lệ.");
        }

        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", config.tmnCode());
        fields.put("vnp_Amount", providerAmount);
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", reference);
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + reference);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", config.returnUrl());
        fields.put("vnp_IpAddr", ip);
        fields.put("vnp_CreateDate", formatDate(createdAt));
        fields.put("vnp_ExpireDate", formatDate(expiresAt));

        String hashData = sortedEncodedFields(fields);
        return config.payUrl() + "?" + hashData + "&vnp_SecureHash=" + hmac(hashData);
    }

    /**
     * Verifies checksum, configured merchant and callback fields before the caller
     * reads or mutates domain data. The caller must still compare reference and amount
     * with its authoritative order record and enforce idempotency.
     */
    public VerifiedPayment verifyCallback(Map<String, String> params) {
        requireConfigured();
        if (params == null || params.isEmpty()) throw invalid("callback", "Kết quả VNPay không hợp lệ.");
        for (String key : params.keySet()) {
            if (key == null || !key.startsWith("vnp_")) {
                throw invalid("callback", "Kết quả VNPay chứa tham số không hợp lệ.");
            }
        }

        String suppliedHash = params.get("vnp_SecureHash");
        if (suppliedHash == null || !suppliedHash.matches("(?i)[0-9a-f]{128}")) {
            throw invalid("callback", "Chữ ký VNPay không hợp lệ.");
        }
        Map<String, String> signedFields = new TreeMap<>(params);
        signedFields.remove("vnp_SecureHash");
        signedFields.remove("vnp_SecureHashType");
        String expectedHash = hmac(sortedEncodedFields(signedFields));
        if (!constantTimeHexEquals(expectedHash, suppliedHash)) {
            throw invalid("callback", "Chữ ký VNPay không hợp lệ.");
        }

        if (!config.tmnCode().equals(params.get("vnp_TmnCode"))) {
            throw invalid("callback", "Mã merchant VNPay không khớp.");
        }
        if (params.containsKey("vnp_CurrCode") && !"VND".equals(params.get("vnp_CurrCode"))) {
            throw invalid("vnp_CurrCode", "Đơn vị tiền tệ VNPay không hợp lệ.");
        }
        if (params.containsKey("vnp_Command") && !"pay".equals(params.get("vnp_Command"))) {
            throw invalid("vnp_Command", "Loại giao dịch VNPay không hợp lệ.");
        }
        String reference = params.get("vnp_TxnRef");
        requireReference(reference);
        long amountVnd = parseProviderAmount(params.get("vnp_Amount"));
        String responseCode = requiredField(params, "vnp_ResponseCode");
        String transactionStatus = requiredField(params, "vnp_TransactionStatus");
        Instant paidAt = parseOptionalProviderDate(params.get("vnp_PayDate"));
        return new VerifiedPayment(reference, amountVnd, responseCode, transactionStatus,
                paidAt, blankToNull(params.get("vnp_TransactionNo")));
    }

    /** Verifies the callback and also checks the order values supplied by the caller. */
    public VerifiedPayment verifyCallback(Map<String, String> params, String expectedReference,
            BigDecimal expectedAmountVnd) {
        VerifiedPayment payment = verifyCallback(params);
        long expectedAmount = toWholeVnd(expectedAmountVnd);
        if (!payment.reference().equals(expectedReference) || payment.amountVnd() != expectedAmount) {
            throw invalid("callback", "Thông tin giao dịch VNPay không khớp đơn hàng.");
        }
        return payment;
    }

    /**
     * Queries the sandbox result API. originalCreateDate must be the exact payment
     * request creation time sent as vnp_CreateDate; it is required by QueryDR.
     */
    public VerifiedPayment queryDr(String reference, BigDecimal expectedAmountVnd,
            Instant originalCreateDate, String ip) {
        requireConfigured();
        requireReference(reference);
        requireIp(ip);
        long expectedAmount = toWholeVnd(expectedAmountVnd);
        if (originalCreateDate == null) {
            throw invalid("originalCreateDate", "Thiếu thời điểm tạo giao dịch VNPay.");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String orderInfo = "Truy van giao dich " + reference;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", requestId);
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TmnCode", config.tmnCode());
        fields.put("vnp_TxnRef", reference);
        fields.put("vnp_OrderInfo", orderInfo);
        fields.put("vnp_TransactionDate", formatDate(originalCreateDate));
        fields.put("vnp_CreateDate", formatDate(clock.instant()));
        fields.put("vnp_IpAddr", ip);
        fields.put("vnp_SecureHash", hmac(String.join("|",
                requestId, "2.1.0", "querydr", config.tmnCode(), reference,
                formatDate(originalCreateDate), fields.get("vnp_CreateDate"), ip, orderInfo)));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.queryUrl()))
                    .timeout(config.requestTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(fields), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException | JsonProcessingException error) {
            throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Cấu hình VNPay chưa hợp lệ.");
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw providerUnavailable();
        } catch (IOException | RuntimeException error) {
            throw providerUnavailable();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw providerUnavailable();

        Map<String, String> responseFields = readProviderObject(response.body());
        verifyQueryResponse(responseFields, reference, expectedAmount);
        // A failed QueryDR API call does not establish that the payment failed.
        if (!"00".equals(responseFields.get("vnp_ResponseCode"))) throw providerUnavailable();
        return paymentFromFields(responseFields);
    }

    private void verifyQueryResponse(Map<String, String> fields, String expectedReference, long expectedAmount) {
        String suppliedHash = fields.get("vnp_SecureHash");
        if (suppliedHash == null || !suppliedHash.matches("(?i)[0-9a-f]{128}")) {
            throw providerInvalidResponse();
        }
        String hashData = String.join("|", QUERY_RESPONSE_CHECK_FIELDS.stream()
                .map(field -> nullToEmpty(fields.get(field))).toList());
        if (!constantTimeHexEquals(hmac(hashData), suppliedHash)) throw providerInvalidResponse();
        String command = fields.get("vnp_Command");
        if (command != null && !command.isBlank() && !"querydr".equals(command)) throw providerInvalidResponse();
        if (!config.tmnCode().equals(fields.get("vnp_TmnCode"))) throw providerInvalidResponse();
        if (!expectedReference.equals(fields.get("vnp_TxnRef"))) throw providerInvalidResponse();
        if (fields.get("vnp_ResponseCode") == null || fields.get("vnp_ResponseCode").isBlank()) {
            throw providerInvalidResponse();
        }
        long amount;
        try {
            amount = parseProviderAmount(fields.get("vnp_Amount"));
        } catch (AccountException error) {
            throw providerInvalidResponse();
        }
        if (amount != expectedAmount) throw providerInvalidResponse();
    }

    private VerifiedPayment paymentFromFields(Map<String, String> fields) {
        final Instant paidAt;
        try {
            paidAt = parseOptionalProviderDate(fields.get("vnp_PayDate"));
        } catch (AccountException error) {
            throw providerInvalidResponse();
        }
        return new VerifiedPayment(fields.get("vnp_TxnRef"), parseProviderAmount(fields.get("vnp_Amount")),
                nullToEmpty(fields.get("vnp_ResponseCode")), nullToEmpty(fields.get("vnp_TransactionStatus")),
                paidAt, blankToNull(fields.get("vnp_TransactionNo")));
    }

    private Map<String, String> readProviderObject(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null || !root.isObject()) throw providerInvalidResponse();
            Map<String, String> fields = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && value.isValueNode() && !value.isNull()) {
                    fields.put(entry.getKey(), value.asText());
                }
            });
            return fields;
        } catch (JsonProcessingException error) {
            throw providerInvalidResponse();
        }
    }

    private String sortedEncodedFields(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        new TreeMap<>(fields).forEach((key, value) -> {
            if (value != null) {
                pairs.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return String.join("&", pairs);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (Exception error) {
            throw new AccountException(500, "PAYMENT_SIGNING_FAILED", "Không thể ký yêu cầu VNPay.");
        }
    }

    private static boolean constantTimeHexEquals(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private static long toWholeVnd(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw invalid("amount", "Số tiền thanh toán không hợp lệ.");
        final BigInteger whole;
        try {
            whole = amount.toBigIntegerExact();
        } catch (ArithmeticException error) {
            throw invalid("amount", "Số tiền phải là số nguyên VND.");
        }
        BigInteger providerAmount = whole.multiply(BigInteger.valueOf(100));
        if (whole.signum() <= 0 || providerAmount.compareTo(MAX_PROVIDER_AMOUNT) > 0) {
            throw invalid("amount", "Số tiền vượt giới hạn VNPay.");
        }
        return whole.longValueExact();
    }

    private static String providerAmount(long amountVnd) {
        return BigInteger.valueOf(amountVnd).multiply(BigInteger.valueOf(100)).toString();
    }

    private static long parseProviderAmount(String value) {
        if (value == null || !value.matches("[0-9]{1,12}")) {
            throw invalid("vnp_Amount", "Số tiền VNPay không hợp lệ.");
        }
        BigInteger amount = new BigInteger(value);
        BigInteger[] vndAndRemainder = amount.divideAndRemainder(BigInteger.valueOf(100));
        if (vndAndRemainder[1].signum() != 0 || vndAndRemainder[0].signum() <= 0) {
            throw invalid("vnp_Amount", "Số tiền VNPay không biểu diễn được số nguyên VND.");
        }
        return vndAndRemainder[0].longValueExact();
    }

    private static String formatDate(Instant instant) {
        return VN_DATE.format(LocalDateTime.ofInstant(instant, VN_ZONE));
    }

    private static Instant parseOptionalProviderDate(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[0-9]{14}")) throw invalid("vnp_PayDate", "Thời điểm thanh toán VNPay không hợp lệ.");
        try {
            return LocalDateTime.parse(value, VN_DATE).atZone(VN_ZONE).toInstant();
        } catch (DateTimeParseException error) {
            throw invalid("vnp_PayDate", "Thời điểm thanh toán VNPay không hợp lệ.");
        }
    }

    private static String requiredField(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) throw invalid(name, "Thiếu thông tin giao dịch VNPay.");
        return value;
    }

    private static void requireReference(String reference) {
        if (reference == null || !reference.matches("[A-Za-z0-9._-]{1,100}")) {
            throw invalid("reference", "Mã giao dịch VNPay không hợp lệ.");
        }
    }

    private static void requireIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            throw invalid("ip", "Địa chỉ IP giao dịch không hợp lệ.");
        }
    }

    private void requireConfigured() {
        if (config.tmnCode().isBlank() || config.hashSecret().isBlank()) {
            throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Thanh toán qua VNPay chưa được cấu hình.");
        }
    }

    private void requireValidReturnUrl() {
        try {
            URI uri = URI.create(config.returnUrl());
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Invalid VNPay return URL.");
            }
        } catch (IllegalArgumentException error) {
            throw new AccountException(503, "PAYMENT_NOT_CONFIGURED", "Cấu hình URL trả về từ VNPay chưa hợp lệ.");
        }
    }

    private static HttpClient newHttpClient(VNPayConfig config) {
        Objects.requireNonNull(config, "config");
        return HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
    }

    private static AccountException invalid(String field, String message) {
        return AccountException.invalid(field, message);
    }

    private static AccountException providerUnavailable() {
        return new AccountException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Không thể xác minh giao dịch với VNPay lúc này.");
    }

    private static AccountException providerInvalidResponse() {
        return new AccountException(502, "PAYMENT_PROVIDER_INVALID_RESPONSE", "Phản hồi xác minh từ VNPay không hợp lệ.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Result fields stay explicit so API response code is not confused with payment status. */
    public record VerifiedPayment(
            String reference,
            long amountVnd,
            String responseCode,
            String transactionStatus,
            Instant paidAt,
            String providerTransaction) {
        public String ref() { return reference; }
        public String txnStatus() { return transactionStatus; }
        public String providerTxn() { return providerTransaction; }
    }
}
