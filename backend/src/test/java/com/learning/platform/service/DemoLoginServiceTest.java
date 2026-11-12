package com.learning.platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoLoginServiceTest {
    @TempDir Path directory;
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final ObjectMapper mapper = new ObjectMapper();
    static final String PASSWORD = "Example-demo-2026";
    static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    Path accounts;
    DemoLoginService service;

    @BeforeEach void setup() throws Exception {
        accounts = directory.resolve("accounts.json");
        Map<String, Object> saved = new LinkedHashMap<>();
        for (String role : List.of("student", "teacher", "admin")) {
            String email = "feature-demo-" + (role.equals("student") ? "weak" : role) + "@zhidu.local";
            saved.put(role, Map.of("email", email, "password", PASSWORD));
            when(jdbc.queryForList(anyString(), eq(email), eq(role))).thenReturn(List.of(Map.of("id", 9L, "password_hash", HASH)));
        }
        Files.writeString(accounts, mapper.writeValueAsString(saved));
        service = new DemoLoginService(jdbc, mapper, true, accounts.toString());
    }

    MockHttpServletRequest local() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); request.setServerName("localhost");
        request.addHeader("Origin", "http://127.0.0.1:5173");
        return request;
    }

    @Test void defaultOffNeverReturnsOrReadsCredentials() {
        var disabled = new DemoLoginService(jdbc, mapper, false, accounts.toString());
        assertEquals(false, disabled.available(local()).get("enabled"));
        assertEquals(404, assertThrows(BusinessException.class, () -> disabled.credentials(local(), "admin")).getCode());
        verifyNoInteractions(jdbc);
    }

    @Test void publicListContainsOnlyRoleLabelsAndEmails() throws Exception {
        var state = service.available(local());
        assertEquals(true, state.get("enabled"));
        assertEquals(3, ((List<?>) state.get("accounts")).size());
        String json = mapper.writeValueAsString(state);
        assertTrue(json.contains("演示管理"));
        assertFalse(json.contains(PASSWORD));
        assertFalse(json.contains("password"));
    }

    @Test void credentialsAreOnlyForTheFixedDemoIdentityAndStillRequireAnExistingMatchingPassword() {
        assertEquals(Map.of("email", "feature-demo-admin@zhidu.local", "password", PASSWORD), service.credentials(local(), "admin"));
        when(jdbc.queryForList(anyString(), eq("feature-demo-admin@zhidu.local"), eq("admin"))).thenReturn(List.of(Map.of("id", 9L, "password_hash", new BCryptPasswordEncoder().encode("changed-password"))));
        assertEquals(503, assertThrows(BusinessException.class, () -> service.credentials(local(), "admin")).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.credentials(local(), "owner")).getCode());
    }

    @Test void cannotUseTheRealAdministratorOrOverrideAnotherIdentityThroughTheFile() throws Exception {
        Files.writeString(accounts, mapper.writeValueAsString(Map.of("admin", Map.of("email", "admin@zhidu.local", "password", PASSWORD))));
        assertEquals(false, service.available(local()).get("enabled"));
        assertEquals(503, assertThrows(BusinessException.class, () -> service.credentials(local(), "admin")).getCode());
        verifyNoInteractions(jdbc);
    }

    @Test void remoteAddressesHostsOriginsAndUntrustedProxyHopsAreRejected() {
        List<MockHttpServletRequest> requests = new ArrayList<>();
        var remote = local(); remote.setRemoteAddr("192.0.2.5"); requests.add(remote);
        var host = local(); host.setServerName("public.example"); requests.add(host);
        var origin = local(); origin.removeHeader("Origin"); origin.addHeader("Origin", "https://localhost.attacker.example"); requests.add(origin);
        var forwarded = local(); forwarded.addHeader("X-Forwarded-For", "127.0.0.1, 192.0.2.5"); requests.add(forwarded);
        var real = local(); real.addHeader("X-Real-IP", "192.0.2.5"); requests.add(real);
        var forwardedHost = local(); forwardedHost.addHeader("X-Forwarded-Host", "public.example"); requests.add(forwardedHost);
        var standard = local(); standard.addHeader("Forwarded", "for=192.0.2.5"); requests.add(standard);
        for (var request : requests) {
            assertEquals(false, service.available(request).get("enabled"));
            assertEquals(404, assertThrows(BusinessException.class, () -> service.credentials(request, "admin")).getCode());
        }
        verifyNoInteractions(jdbc);
    }

    @Test void normalLocalViteProxyAndIpv6AreAllowed() {
        var proxy = local(); proxy.addHeader("X-Forwarded-For", "::1, 127.0.0.1"); proxy.addHeader("X-Forwarded-Host", "localhost:5173");
        assertEquals(true, service.available(proxy).get("enabled"));
        var ipv6 = local(); ipv6.setRemoteAddr("0:0:0:0:0:0:0:1"); ipv6.setServerName("::1"); ipv6.removeHeader("Origin"); ipv6.addHeader("Origin", "http://[::1]:8081");
        assertEquals(true, service.available(ipv6).get("enabled"));
    }

    @Test void missingMalformedAndNullFilesKeepTheNormalLoginAvailable() throws Exception {
        for (String content : List.of("not-json", "null", "[]")) {
            Files.writeString(accounts, content);
            assertEquals(false, service.available(local()).get("enabled"));
        }
        Files.delete(accounts);
        assertEquals(false, service.available(local()).get("enabled"));
        verifyNoInteractions(jdbc);
    }
}
