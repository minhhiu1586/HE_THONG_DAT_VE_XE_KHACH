package com.datvexe.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Versioned PBKDF2-HMAC-SHA256 hashes; never stores or logs the password. */
public final class Passwords {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private Passwords() {}
    public static void validate(String value, String field) {
        if (value == null || value.length() < 10 || value.length() > 128
                || value.codePoints().noneMatch(Character::isLetter) || value.codePoints().noneMatch(Character::isDigit))
            throw AccountException.invalid(field, "Mật khẩu cần 10–128 ký tự, có chữ và số.");
    }
    public static String hash(String password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
            + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean matches(String password, String encoded) {
        if (password == null || password.length() > 128 || encoded == null) return false;
        try {
            String[] p = encoded.split("\\$", -1);
            if (p.length != 4 || !p[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(p[1]);
            if (iterations < ITERATIONS || iterations > 1_200_000) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]), expected = Base64.getDecoder().decode(p[3]);
            if (salt.length != 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException e) { return false; }
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Password hashing unavailable", e); }
        finally { spec.clearPassword(); }
    }
}
