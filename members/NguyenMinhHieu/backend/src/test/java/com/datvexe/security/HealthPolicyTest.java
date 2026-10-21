package com.datvexe.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealthPolicyTest {
    @Test
    void healthCheckIsPublicReadOnly() {
        assertTrue(AccessPolicy.publicEndpoint("GET", "/api/health"));
        assertFalse(AccessPolicy.publicEndpoint("POST", "/api/health"));
        assertTrue(AccessPolicy.publicEndpoint("GET", "/api/khuyen-mai"));
        assertTrue(AccessPolicy.publicEndpoint("GET", "/api/tuyen-xe/cong-khai"));
        assertFalse(AccessPolicy.publicEndpoint("POST", "/api/tuyen-xe/cong-khai"));
        assertFalse(AccessPolicy.publicEndpoint("POST", "/api/khuyen-mai"));
    }
}
