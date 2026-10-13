package com.datvexe.service;

import com.datvexe.dto.PricingDto.PriceLine;
import com.datvexe.dto.PricingDto.PriceSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure T17 price allocation; it has no HTTP, persistence or promotion concerns. */
public final class PriceCalculator {
    private static final BigDecimal MAX_VND = new BigDecimal("999999999999");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private PriceCalculator() {}

    public record SeatPrice(String maGhe, BigDecimal giaGoc) {}

    public static PriceSummary calculate(List<SeatPrice> seats, BigDecimal rawDiscount) {
        validateInputs(seats, rawDiscount);

        List<SeatPrice> sortedSeats = seats.stream()
                .sorted(Comparator.comparing(SeatPrice::maGhe))
                .toList();
        BigDecimal total = sortedSeats.stream()
                .map(SeatPrice::giaGoc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = rawDiscount.min(total).setScale(0, RoundingMode.HALF_UP);

        List<BigDecimal> discounts = allocate(sortedSeats, total, discount);
        List<PriceLine> lines = new ArrayList<>(sortedSeats.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < sortedSeats.size(); i++) {
            BigDecimal price = sortedSeats.get(i).giaGoc();
            BigDecimal lineDiscount = discounts.get(i);
            allocated = allocated.add(lineDiscount);
            lines.add(new PriceLine(
                    sortedSeats.get(i).maGhe(),
                    amount(price),
                    amount(lineDiscount),
                    amount(price.subtract(lineDiscount))));
        }
        if (allocated.compareTo(discount) != 0) {
            throw new IllegalStateException("Phân bổ giảm giá không khớp tổng giảm.");
        }

        return new PriceSummary(
                "VND",
                amount(total),
                amount(discount),
                amount(total.subtract(discount)),
                lines);
    }

    private static List<BigDecimal> allocate(
            List<SeatPrice> seats,
            BigDecimal total,
            BigDecimal discount) {
        List<BigDecimal> result = new ArrayList<>(seats.size());
        if (total.signum() == 0 || discount.signum() == 0) {
            for (int i = 0; i < seats.size(); i++) result.add(BigDecimal.ZERO);
            return result;
        }

        List<Share> shares = new ArrayList<>(seats.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < seats.size(); i++) {
            BigDecimal numerator = discount.multiply(seats.get(i).giaGoc());
            BigDecimal[] quotientAndRemainder = numerator.divideAndRemainder(total);
            BigDecimal base = quotientAndRemainder[0];
            result.add(base);
            allocated = allocated.add(base);
            shares.add(new Share(i, quotientAndRemainder[1]));
        }

        int remainder = discount.subtract(allocated).intValueExact();
        shares.sort(Comparator
                .comparing(Share::remainder, Comparator.reverseOrder())
                .thenComparing(share -> seats.get(share.index()).maGhe()));
        for (int i = 0; i < remainder; i++) {
            result.set(shares.get(i % shares.size()).index(),
                    result.get(shares.get(i % shares.size()).index()).add(ONE));
        }
        return result;
    }

    private static void validateInputs(List<SeatPrice> seats, BigDecimal rawDiscount) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ghế không được rỗng.");
        }
        if (rawDiscount == null || rawDiscount.signum() < 0) {
            throw new IllegalArgumentException("Tiền giảm không được âm hoặc null.");
        }

        Set<String> ids = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SeatPrice seat : seats) {
            if (seat == null || seat.maGhe() == null || seat.maGhe().isBlank()) {
                throw new IllegalArgumentException("Mã ghế không được rỗng.");
            }
            if (!ids.add(seat.maGhe())) {
                throw new IllegalArgumentException("Mã ghế bị lặp.");
            }
            validateVnd(seat.giaGoc(), "Giá gốc");
            total = total.add(seat.giaGoc());
        }
        if (total.compareTo(MAX_VND) > 0) {
            throw new IllegalArgumentException("Tổng tiền vượt giới hạn VND.");
        }
    }

    private static void validateVnd(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_VND) > 0
                || value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(field + " phải là số nguyên VND không âm.");
        }
    }

    private static String amount(BigDecimal value) {
        return value.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private record Share(int index, BigDecimal remainder) {}
}
