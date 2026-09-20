package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerSessionGuardTest {

    @Test
    void fullSessionIsUnrestrictedHere() {
        LoginResponse.UserInfo user = LoginResponse.UserInfo.builder()
                .role("user").scope("full").build();
        assertTrue(SamplerSessionGuard.allows("/erp-sync/status", "GET", user));
        assertTrue(SamplerSessionGuard.allows("/admin/users", "GET", user));
        assertTrue(SamplerSessionGuard.allows("/org/sync", "POST", user));
        assertTrue(SamplerSessionGuard.allows("/goods-library", "GET", user));
        assertTrue(SamplerSessionGuard.allows("/user/settings", "GET", user));
        assertTrue(SamplerSessionGuard.allows("/user/password", "PUT", user));
    }

    @Test
    void samplerMayReadAndUpdateBoundGoodsOnly() {
        LoginResponse.UserInfo sampler = LoginResponse.UserInfo.builder()
                .role("sampler").scope("sampler").samplerGoodsId("9").build();
        assertTrue(SamplerSessionGuard.allows("/goods-library/9", "GET", sampler));
        assertTrue(SamplerSessionGuard.allows("/goods-library/9", "PUT", sampler));
        assertTrue(SamplerSessionGuard.allows("/goods-library/9/images", "POST", sampler));
        assertTrue(SamplerSessionGuard.allows("/goods-library/9/images", "DELETE", sampler));
        assertFalse(SamplerSessionGuard.allows("/goods-library/9", "DELETE", sampler));
        assertFalse(SamplerSessionGuard.allows("/goods-library/9/sampler-notice/resend", "POST", sampler));
        assertFalse(SamplerSessionGuard.allows("/goods-library/8", "GET", sampler));
        assertFalse(SamplerSessionGuard.allows("/goods-library", "GET", sampler));
        assertFalse(SamplerSessionGuard.allows("/goods-library", "POST", sampler));
        assertFalse(SamplerSessionGuard.allows("/erp-sync/status", "GET", sampler));
        assertFalse(SamplerSessionGuard.allows("/org/sync", "POST", sampler));
        assertFalse(SamplerSessionGuard.allows("/admin/users", "GET", sampler));
        assertFalse(SamplerSessionGuard.allows("/images", "GET", sampler));
        assertFalse(SamplerSessionGuard.allows("/user/settings", "GET", sampler));
        assertFalse(SamplerSessionGuard.allows("/user/password", "PUT", sampler));
        assertFalse(SamplerSessionGuard.allows("/admin/users/u2/reset-password", "POST", sampler));
    }

    @Test
    void nullUserIsNotSampler() {
        assertTrue(SamplerSessionGuard.allows("/goods-library", "GET", null));
        assertFalse(SamplerSessionGuard.isSamplerScope(null));
    }
}
