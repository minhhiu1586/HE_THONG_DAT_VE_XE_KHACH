package com.datvexe.ticket;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
class TicketCodeTest {
    @Test void referencesAreFiveCharactersAndUnique() {
        var codes = new HashSet<String>();
        for (long i = 1; i <= 100000; i++) {
            String code = TicketCode.encode(i);
            assertTrue(code.matches("[0-9A-HJKMNP-TV-Z]{5}"));
            assertTrue(codes.add(code));
        }
        assertTrue(codes.add(TicketCode.encode(33554431L)));
        assertThrows(IllegalArgumentException.class, () -> TicketCode.encode(33554432L));
    }
}
