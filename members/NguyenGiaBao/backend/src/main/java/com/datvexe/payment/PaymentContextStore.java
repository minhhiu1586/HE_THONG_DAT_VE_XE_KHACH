package com.datvexe.payment;

import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.PricingDto.PriceLine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Volatile payment-attempt context used by the limited local VNPay demo. */
public final class PaymentContextStore {
    public static final Duration RETENTION = Duration.ofHours(24);
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public PaymentContextStore() { this(Clock.systemUTC()); }
    public PaymentContextStore(Clock clock) { this.clock = java.util.Objects.requireNonNull(clock); }

    public void put(Attempt attempt) {
        if (attempt == null || attempt.maGiaoDich() == null) throw new IllegalArgumentException("Attempt is required.");
        cleanup(clock.instant());
        attempts.put(attempt.maGiaoDich(), attempt);
    }

    public Attempt get(String maGiaoDich) {
        Attempt attempt = attempts.get(maGiaoDich);
        if (attempt == null || !clock.instant().isBefore(attempt.storedUntil())) {
            if (attempt != null) attempts.remove(maGiaoDich, attempt);
            return null;
        }
        return attempt;
    }

    public void remove(String maGiaoDich) { attempts.remove(maGiaoDich); }

    public int size() { cleanup(clock.instant()); return attempts.size(); }

    private void cleanup(Instant now) {
        attempts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().storedUntil()));
    }

    public record Attempt(
            String maGiaoDich,
            String maDon,
            String userId,
            String maPhuongThuc,
            BigDecimal soTien,
            Instant createdAt,
            Instant expiresAt,
            Instant storedUntil,
            String clientIp,
            String urlThanhToan,
            List<String> maGhe,
            List<PassengerRequest> passengers,
            List<PriceLine> chiTietGia) {
        public Attempt {
            if (maGiaoDich == null || maDon == null || userId == null || maPhuongThuc == null
                    || soTien == null || createdAt == null || expiresAt == null) {
                throw new IllegalArgumentException("Payment attempt fields are required.");
            }
            storedUntil = storedUntil == null ? createdAt.plus(RETENTION) : storedUntil;
            maGhe = List.copyOf(maGhe == null ? List.of() : maGhe);
            passengers = List.copyOf(passengers == null ? List.of() : passengers);
            chiTietGia = List.copyOf(chiTietGia == null ? List.of() : chiTietGia);
        }
    }
}
