package com.datvexe.validation;

import com.datvexe.security.AccountException;

public final class ReviewValidation {
    private ReviewValidation() {}

    private static AccountException error(int status, String code, String message) {
        return new AccountException(status, code, message);
    }

    public static String resourceId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}"))
            throw error(400, "INVALID_REVIEW_ID", "Mã tài nguyên không hợp lệ.");
        return id;
    }

    public static void text(String text) {
        if (text == null) return;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                    continue;
                }
                throw error(400, "INVALID_REVIEW_CONTENT", "Nội dung chứa ký tự không hợp lệ.");
            }
            if (Character.isLowSurrogate(c)
                    || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                throw error(400, "INVALID_REVIEW_CONTENT", "Nội dung chứa ký tự không hợp lệ.");
        }
    }

    public static String content(Integer stars, String text) {
        if (stars == null || stars < 1 || stars > 5)
            throw error(400, "INVALID_REVIEW_STARS", "Điểm phải là số nguyên từ 1 đến 5.");
        if (text != null && text.length() > 2000)
            throw error(400, "INVALID_REVIEW_CONTENT", "Nhận xét không được vượt quá 2000 ký tự.");
        text(text);
        return text == null ? "" : text.strip();
    }

    public static void pagination(int page, int size) {
        if (page < 1 || size < 1 || size > 100 || ((long) page - 1) * size > Integer.MAX_VALUE)
            throw error(400, "INVALID_PAGINATION", "Phân trang không hợp lệ.");
    }
}
