package com.datvexe.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthPolicyTest {
    @Test void healthCheckIsPublicReadOnly() {
        assertTrue(AccessPolicy.publicEndpoint("GET", "/api/health"));
        assertFalse(AccessPolicy.publicEndpoint("POST", "/api/health"));
    }
}
