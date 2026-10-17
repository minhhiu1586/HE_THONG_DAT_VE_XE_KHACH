package com.datvexe.service;

import java.math.BigInteger;
import java.security.SecureRandom;

final class OrderCode {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private OrderCode() {}

    static String next() {
        byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);
        BigInteger value = new BigInteger(1, bytes);
        StringBuilder code = new StringBuilder("VX");
        for (int i = 15; i >= 0; i--) {
            if (i % 4 == 3) code.append('-');
            code.append(ALPHABET.charAt(value.shiftRight(i * 5).intValue() & 31));
        }
        return code.toString();
    }
}
