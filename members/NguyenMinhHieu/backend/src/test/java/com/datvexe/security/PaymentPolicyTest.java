package com.datvexe.security;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.domain.States.LoaiTaiKhoan;

import org.junit.jupiter.api.Test;

class PaymentPolicyTest {
    @Test
    void onlyExactProviderGetPathsArePublic() {
        for (String path :
                new String[] {"/api/thanh-toan/return/vnpay", "/api/thanh-toan/callback/vnpay"}) {
            assertTrue(AccessPolicy.publicEndpoint("GET", path));
            assertFalse(AccessPolicy.publicEndpoint("POST", path));
            assertFalse(AccessPolicy.publicEndpoint("GET", path + "/"));
        }
        assertFalse(AccessPolicy.publicEndpoint("GET", "/api/thanh-toan/PAY01"));
        assertFalse(AccessPolicy.publicEndpoint("POST", "/api/thanh-toan/PAY01/xac-minh"));
        assertFalse(AccessPolicy.publicEndpoint("GET", "/api/ve-xe/TKT01"));
    }

    @Test
    void manualVerificationRequiresCustomerWhileReadAllowsOperatorAndAdmin() {
        for (LoaiTaiKhoan role : new LoaiTaiKhoan[] {LoaiTaiKhoan.NHA_XE, LoaiTaiKhoan.ADMIN}) {
            CurrentUser user = new CurrentUser("U", role, "NX", null, "S");
            assertDoesNotThrow(() -> AccessPolicy.role(user, "/api/thanh-toan/PAY01", "GET"));
            assertThrows(
                    AccountException.class,
                    () -> AccessPolicy.role(user, "/api/thanh-toan/PAY01/xac-minh", "POST"));
        }
        CurrentUser customer = new CurrentUser("U", LoaiTaiKhoan.KHACH_HANG, null, null, "S");
        assertDoesNotThrow(
                () -> AccessPolicy.role(customer, "/api/thanh-toan/PAY01/xac-minh", "POST"));
        CurrentUser employee = new CurrentUser("U", LoaiTaiKhoan.NHAN_VIEN, "NX", "NV", "S");
        assertThrows(
                AccountException.class,
                () -> AccessPolicy.role(employee, "/api/thanh-toan/PAY01", "GET"));
    }
}
