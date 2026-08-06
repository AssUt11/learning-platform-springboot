package com.learning.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void createsSignedTokenAndReloadsCurrentUserFromDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService service = new TokenService(jdbcTemplate, SECRET, 3600);
        Map<String, Object> databaseUser = user(7L, "Alice", "alice@example.com", "teacher");
        when(jdbcTemplate.queryForList(anyString(), eq(7L)))
                .thenReturn(Collections.singletonList(databaseUser));

        String token = service.create(user(7L, "Old name", "old@example.com", "student"));
        Map<String, Object> authenticated = service.getUser("Bearer " + token);

        assertNotNull(authenticated);
        assertEquals("Alice", authenticated.get("name"));
        assertEquals("teacher", authenticated.get("role"));
        verify(jdbcTemplate).queryForList(anyString(), eq(7L));
    }

    @Test
    void rejectsTamperedAndLegacyUnsignedTokens() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService service = new TokenService(jdbcTemplate, SECRET, 3600);
        String token = service.create(user(7L, "Alice", "alice@example.com", "student"));
        String tampered = token.substring(0, token.indexOf('.') + 1) + "invalid";
        String legacy = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("7|Alice|alice@example.com|teacher".getBytes(StandardCharsets.UTF_8));

        assertNull(service.getUser("Bearer " + tampered));
        assertNull(service.getUser("Bearer " + legacy));
    }

    @Test
    void rejectsShortSecretAtStartup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThrows(IllegalStateException.class, () -> new TokenService(jdbcTemplate, "too-short", 3600));
    }

    private Map<String, Object> user(long id, String name, String email, String role) {
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", id);
        user.put("name", name);
        user.put("email", email);
        user.put("role", role);
        return user;
    }
}
