package com.datvexe.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class LoginRateLimiter {
    private record Window(long since, int count) {}

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public LoginRateLimiter() {
        this(System::currentTimeMillis);
    }

    public LoginRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    public void check(String address) {
        long now = clock.getAsLong();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().since() >= 60_000);
        AtomicBoolean denied = new AtomicBoolean();
        attempts.compute(
                address,
                (key, old) -> {
                    Window window = old == null ? new Window(now, 0) : old;
                    if (window.count() >= 20) denied.set(true);
                    return new Window(window.since(), window.count() + 1);
                });
        if (denied.get() || attempts.size() > 10_000)
            throw new AccountException(
                    429, "TOO_MANY_REQUESTS", "Bạn đã thử quá nhiều lần. Vui lòng chờ một phút.");
    }
}
