package com.datvexe.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class LoginRateLimiterTest {
    @Test
    void rateLimitIsPerAddressAndResetsAtOneMinute() {
        var now = new AtomicLong(1000);
        var limiter = new LoginRateLimiter(now::get);
        for (int i = 0; i < 20; i++) limiter.check("127.0.0.1");
        assertEquals(
                429,
                assertThrows(AccountException.class, () -> limiter.check("127.0.0.1")).status());
        assertDoesNotThrow(() -> limiter.check("127.0.0.2"));
        now.set(60_999);
        assertThrows(AccountException.class, () -> limiter.check("127.0.0.1"));
        now.set(61_000);
        assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
    }
}
