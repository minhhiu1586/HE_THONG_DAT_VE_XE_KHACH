package com.datvexe.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** T12 request DTOs; these fields do not introduce database attributes. */
public final class TripInputs {
    private TripInputs() {}
    public record Draft(String maChuyen, String maTuyen, String maXe,
        LocalDate ngayKhoiHanh, LocalTime gioKhoiHanh, LocalDateTime gioDenDuKien,
        BigDecimal giaVeDuKien) {}
}
