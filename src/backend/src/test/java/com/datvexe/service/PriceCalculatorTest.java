package com.datvexe.service;

import com.datvexe.dto.PricingDto.PriceLine;
import com.datvexe.dto.PricingDto.PriceSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceCalculatorTest {
    @Test
    void allocatesUnequalPricesProportionally() {
        PriceSummary summary = PriceCalculator.calculate(List.of(
                seat("A", "100000"), seat("B", "200000"), seat("C", "300000")),
                new BigDecimal("60000"));

        assertEquals("600000", summary.tongTienGoc());
        assertEquals("60000", summary.tongTienGiam());
        assertEquals(List.of("10000", "20000", "30000"), discounts(summary));
    }

    @Test
    void allocatesRemainderByLargestFractionThenSeatId() {
        PriceSummary summary = PriceCalculator.calculate(List.of(
                seat("C", "100000"), seat("A", "100000"), seat("B", "100000")),
                new BigDecimal("10000"));

        assertEquals(List.of("3334", "3333", "3333"), discounts(summary));
        assertEquals(List.of("A", "B", "C"), summary.chiTietGia().stream().map(PriceLine::maGhe).toList());
    }

    @Test
    void roundsRawDiscountHalfUpBeforeAllocation() {
        PriceSummary summary = PriceCalculator.calculate(List.of(seat("A", "100")), new BigDecimal("10.5"));

        assertEquals("11", summary.tongTienGiam());
        assertEquals("89", summary.tongThanhToan());
    }

    @Test
    void capsDiscountAtTotalAndSupportsZeroTotal() {
        PriceSummary capped = PriceCalculator.calculate(List.of(seat("A", "100"), seat("B", "50")), new BigDecimal("999"));
        PriceSummary zero = PriceCalculator.calculate(List.of(seat("A", "0"), seat("B", "0")), new BigDecimal("999"));

        assertEquals("150", capped.tongTienGiam());
        assertEquals("0", capped.tongThanhToan());
        assertEquals(List.of("0", "0"), discounts(zero));
    }

    @Test
    void inputOrderDoesNotChangeResult() {
        PriceSummary first = PriceCalculator.calculate(List.of(seat("A", "100"), seat("B", "200")), new BigDecimal("10"));
        PriceSummary second = PriceCalculator.calculate(List.of(seat("B", "200"), seat("A", "100")), new BigDecimal("10"));

        assertEquals(first, second);
    }

    @Test
    void validatesBoundariesAndDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate(List.of(), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate(List.of(seat("A", "1"), seat("A", "2")), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate(List.of(seat("A", "-1")), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate(List.of(seat("A", "1.5")), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate(List.of(seat("A", "999999999999"), seat("B", "1")), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate(List.of(seat("A", "1")), new BigDecimal("-0.01")));
    }

    private static PriceCalculator.SeatPrice seat(String id, String price) {
        return new PriceCalculator.SeatPrice(id, new BigDecimal(price));
    }

    private static List<String> discounts(PriceSummary summary) {
        return summary.chiTietGia().stream().map(PriceLine::tienGiam).toList();
    }
}
