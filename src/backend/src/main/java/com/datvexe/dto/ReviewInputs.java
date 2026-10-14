package com.datvexe.dto;

/** Ownership and moderation state are always determined by the server. */
public final class ReviewInputs {
    private ReviewInputs() {}
    public record Create(String maChuyen,Integer soSao,String binhLuan) {}
    public record Edit(Integer soSao,String binhLuan) {}
    public record Moderation(String lyDo) {}
}
