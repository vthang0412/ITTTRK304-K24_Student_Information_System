package com.example.student_management.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {
    private static final String VALID_TEST_SECRET = "test-only-signing-key-with-32-bytes-minimum";

    @Test
    void signsAndValidatesTokensWithConfiguredSecret() {
        JwtUtil jwtUtil = new JwtUtil(VALID_TEST_SECRET, 60_000);
        var user = User.withUsername("test-user")
                .password("not-used")
                .authorities("ROLE_STUDENT")
                .build();

        String token = jwtUtil.generateToken(user);

        assertEquals("test-user", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.validateToken(token, user));
    }

    @Test
    void rejectsShortAndExampleSecretsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short", 60_000));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("replace-with-a-random-secret-at-least-32-characters-long", 60_000));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("change-this-secret-key-for-dev-only-please-change-in-prod", 60_000));
    }
}
