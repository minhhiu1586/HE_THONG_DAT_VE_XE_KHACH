package com.datvexe.service;

import jakarta.persistence.EntityManager;

/** A unique, compact public reference, not an authorization or QR token. */
final class TicketCode {
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private TicketCode() {}
    static String next(EntityManager em) {
        long sequence = ((Number) em.createNativeQuery("select nextval('ticket_public_code_seq')").getSingleResult()).longValue();
        return encode(sequence);
    }
    static String encode(long sequence) {
        if (sequence < 1 || sequence > 33554431L) throw new IllegalArgumentException("Ticket code capacity exceeded");
        long value = (sequence * 2654435761L) & 33554431L;
        char[] code = new char[5];
        for (int i = 4; i >= 0; i--) { code[i] = ALPHABET.charAt((int)(value & 31)); value >>>= 5; }
        return new String(code);
    }
}
