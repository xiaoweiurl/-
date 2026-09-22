package com.imagemanager.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePasswordRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsCurrentPassword() throws Exception {
        ChangePasswordRequest req = mapper.readValue(
                "{\"currentPassword\":\"123456\",\"newPassword\":\"Abcdef12\",\"confirmPassword\":\"Abcdef12\"}",
                ChangePasswordRequest.class);
        assertEquals("123456", req.getCurrentPassword());
    }

    @Test
    void readsLegacyOldPasswordAlias() throws Exception {
        ChangePasswordRequest req = mapper.readValue(
                "{\"oldPassword\":\"123456\",\"newPassword\":\"Abcdef12\",\"confirmPassword\":\"Abcdef12\"}",
                ChangePasswordRequest.class);
        assertEquals("123456", req.getCurrentPassword());
    }
}
