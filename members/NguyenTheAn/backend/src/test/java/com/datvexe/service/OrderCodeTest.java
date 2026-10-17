package com.datvexe.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderCodeTest {
    @Test void codeIsReadableAndFitsExistingResourceRoutes() {
        for (int i = 0; i < 100; i++) {
            String code = OrderCode.next();
            assertTrue(code.matches("VX(-[0-9A-HJKMNP-TV-Z]{4}){4}"));
            assertEquals(22, code.length());
            assertTrue(code.matches("[A-Za-z0-9_-]{1,80}"));
        }
    }
}
