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

/** Tính giá, phân bổ giảm giá và tiền hoàn; không truy cập HTTP hoặc database. */
public final class PriceCalculator {
    private static final BigDecimal MAX_VND = new BigDecimal("999999999999");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PriceCalculator() {}

    public record SeatPrice(String maGhe, BigDecimal giaGoc) {}

    public static PriceSummary calculate(List<SeatPrice> seats, BigDecimal rawDiscount) {
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

        List<SeatPrice> sortedSeats = new ArrayList<>(seats);
        sortedSeats.sort(Comparator.comparing(SeatPrice::maGhe));
        BigDecimal discount = rawDiscount.min(total).setScale(0, RoundingMode.HALF_UP);
        List<BigDecimal> discounts = new ArrayList<>();
        List<Share> shares = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < sortedSeats.size(); i++) {
            BigDecimal base = BigDecimal.ZERO;
            BigDecimal fraction = BigDecimal.ZERO;
            if (total.signum() > 0 && discount.signum() > 0) {
                BigDecimal numerator = discount.multiply(sortedSeats.get(i).giaGoc());
                BigDecimal[] parts = numerator.divideAndRemainder(total);
                base = parts[0];
                fraction = parts[1];
            }
            discounts.add(base);
            allocated = allocated.add(base);
            shares.add(new Share(i, fraction));
        }

        // Chia phần dư theo phần lẻ giảm dần; nếu bằng nhau thì theo mã ghế.
        int remainder = discount.subtract(allocated).intValueExact();
        shares.sort(Comparator.comparing(Share::remainder, Comparator.reverseOrder())
                .thenComparing(share -> sortedSeats.get(share.index()).maGhe()));
        for (int i = 0; i < remainder; i++) {
            int index = shares.get(i).index();
            discounts.set(index, discounts.get(index).add(BigDecimal.ONE));
        }

        List<PriceLine> lines = new ArrayList<>();
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (int i = 0; i < sortedSeats.size(); i++) {
            SeatPrice seat = sortedSeats.get(i);
            BigDecimal lineDiscount = discounts.get(i);
            allocatedTotal = allocatedTotal.add(lineDiscount);
            lines.add(new PriceLine(seat.maGhe(), amount(seat.giaGoc()),
                    amount(lineDiscount), amount(seat.giaGoc().subtract(lineDiscount))));
        }
        if (allocatedTotal.compareTo(discount) != 0) {
            throw new IllegalStateException("Phân bổ giảm giá không khớp tổng giảm.");
        }
        return new PriceSummary("VND", amount(total), amount(discount),
                amount(total.subtract(discount)), lines);
    }

    /** Dựng lại giá từng ghế từ tổng tiền đã chốt, không lấy giá tuyến hiện tại. */
    public static PriceSummary calculateFromFrozenTotals(
            List<String> seatIds, BigDecimal total, BigDecimal discount) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ghế không được rỗng.");
        }
        validateVnd(total, "Tổng tiền đã chốt");
        if (discount == null) {
            discount = BigDecimal.ZERO;
        }
        validateVnd(discount, "Tiền giảm đã chốt");
        BigDecimal fare = total.divide(BigDecimal.valueOf(seatIds.size()), 0, RoundingMode.UNNECESSARY);
        List<SeatPrice> prices = new ArrayList<>();
        for (String seatId : seatIds) {
            prices.add(new SeatPrice(seatId, fare));
        }
        return calculate(prices, discount);
    }

    /** Chỉ tính tiền hoàn; service kiểm tra chính sách, thời hạn và trạng thái vé. */
    public static BigDecimal calculateRefund(
            BigDecimal paidAmount,
            BigDecimal refundPercent,
            BigDecimal cancellationFee) {
        validateVnd(paidAmount, "Tiền đã thanh toán");
        validateVnd(cancellationFee, "Phí hủy");
        if (refundPercent == null || refundPercent.signum() < 0
                || refundPercent.compareTo(HUNDRED) > 0
                || refundPercent.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Tỷ lệ hoàn phải từ 0 đến 100 và có tối đa 2 chữ số thập phân.");
        }

        return paidAmount.multiply(refundPercent)
                .divide(HUNDRED)
                .subtract(cancellationFee)
                .setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(paidAmount);
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
