package com.datvexe.dto;

public final class ReviewInputs {
    private ReviewInputs() {}

    public record Create(String maChuyen, Integer soSao, String binhLuan) {}

    public record Edit(Integer soSao, String binhLuan) {}

    public record Moderation(String lyDo) {}
}
