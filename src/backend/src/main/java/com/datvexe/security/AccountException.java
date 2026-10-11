package com.datvexe.security;

import com.datvexe.service.DomainException;
import java.util.Map;

public final class AccountException extends DomainException {
    private final String code;
    private final Map<String, Object> details;
    public AccountException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public AccountException(int status, String code, String message, Map<String, Object> details) {
        super(status, message); this.code = code; this.details = details;
    }
    public String code() { return code; }
    public Map<String, Object> details() { return details; }
    public static AccountException invalid(String field, String message) {
        return new AccountException(400, "VALIDATION_ERROR", message, Map.of("fieldErrors",
            java.util.List.of(Map.of("field", field, "code", "INVALID", "message", message))));
    }
}
