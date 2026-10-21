package com.datvexe.service;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Tiện ích sinh mã đơn đặt vé ngẫu nhiên, an toàn mật mã học (cryptographically secure).
 * Định dạng: VX-XXXX-XXXX-XXXX (16 ký tự Base32 không gây nhầm lẫn).
 * Thiết kế tương tự các tiện ích sinh mã trong Cinema-main (JwtUtil / Token generators).
 */
final class OrderCode {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private OrderCode() {}

    /**
     * Sinh mã đơn đặt vé duy nhất tiếp theo.
     *
     * @return Chuỗi mã đơn có tiền tố "VX-" phân tách mỗi 4 ký tự
     */
    static String next() {
        byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);
        BigInteger value = new BigInteger(1, bytes);
        StringBuilder code = new StringBuilder("VX");
        for (int i = 15; i >= 0; i--) {
            if (i % 4 == 3) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(value.shiftRight(i * 5).intValue() & 31));
        }
        return code.toString();
    }
}

