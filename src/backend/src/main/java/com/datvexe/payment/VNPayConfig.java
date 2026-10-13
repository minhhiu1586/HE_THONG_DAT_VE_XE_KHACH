package com.datvexe.payment;

import java.time.Duration;

/** VNPay merchant configuration. Credentials are read from the process environment. */
public record VNPayConfig(
        String tmnCode,
        String hashSecret,
        String returnUrl,
        String payUrl,
        String queryUrl,
        Duration requestTimeout) {

    public static final String SANDBOX_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final String SANDBOX_QUERY_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    public static final String DEFAULT_RETURN_URL = "http://localhost:8080/api/thanh-toan/return/vnpay";
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    public VNPayConfig {
        tmnCode = tmnCode == null ? "" : tmnCode.trim();
        hashSecret = hashSecret == null ? "" : hashSecret;
        returnUrl = defaultIfBlank(returnUrl, DEFAULT_RETURN_URL);
        payUrl = defaultIfBlank(payUrl, SANDBOX_PAY_URL);
        queryUrl = defaultIfBlank(queryUrl, SANDBOX_QUERY_URL);
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("VNPay request timeout must be positive.");
        }
    }

    /** Builds a configuration from VNP_TMN_CODE, VNP_HASH_SECRET and VNP_RETURN_URL. */
    public static VNPayConfig fromEnvironment() {
        return new VNPayConfig(
                System.getenv("VNP_TMN_CODE"),
                System.getenv("VNP_HASH_SECRET"),
                System.getenv("VNP_RETURN_URL"),
                SANDBOX_PAY_URL,
                SANDBOX_QUERY_URL,
                DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public String toString() {
        return "VNPayConfig[tmnCode=" + tmnCode + ", hashSecret=[REDACTED], returnUrl=" + returnUrl
                + ", payUrl=" + payUrl + ", queryUrl=" + queryUrl + ", requestTimeout=" + requestTimeout + "]";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
