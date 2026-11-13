package com.learning.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** Explicitly enabled local demo identities. Never reuses the site's real administrator account. */
@Service
public class DemoLoginService {
    private static final Map<String, String> EMAILS = Map.of(
        "student", "feature-demo-weak@zhidu.local",
        "teacher", "feature-demo-teacher@zhidu.local",
        "admin", "feature-demo-admin@zhidu.local"
    );
    private static final Map<String, String> LABELS = Map.of(
        "student", "演示学生", "teacher", "演示老师", "admin", "演示管理"
    );
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String accountsFile;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DemoLoginService(JdbcTemplate jdbc, ObjectMapper mapper,
        @Value("${app.demo-login.enabled:false}") boolean enabled,
        @Value("${app.demo-login.accounts-file:}") String accountsFile) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.enabled = enabled;
        this.accountsFile = accountsFile;
    }

    public Map<String, Object> available(HttpServletRequest request) {
        if (!allowed(request)) return Map.of("enabled", false, "accounts", List.of());
        Map<String, Map<String, String>> saved = readAccounts();
        List<Map<String, String>> accounts = new ArrayList<>();
        for (String role : List.of("student", "teacher", "admin")) {
            Map<String, String> account = saved.get(role);
            if (valid(role, account) && !user(role, account.get("email")).isEmpty())
                accounts.add(Map.of("role", role, "label", LABELS.get(role), "email", account.get("email")));
        }
        return Map.of("enabled", !accounts.isEmpty(), "accounts", accounts);
    }

    public Map<String, String> credentials(HttpServletRequest request, String role) {
        if (!allowed(request)) throw new BusinessException(404, "演示入口未开启。");
        if (!EMAILS.containsKey(role)) throw new BusinessException(404, "演示身份不存在。");
        Map<String, String> account = readAccounts().get(role);
        if (!valid(role, account)) throw new BusinessException(503, "该演示账号暂不可用，请稍后重试。");
        var rows = user(role, account.get("email"));
        if (rows.size() != 1 || !encoder.matches(account.get("password"), String.valueOf(rows.get(0).get("password_hash"))))
            throw new BusinessException(503, "该演示账号暂不可用，请稍后重试。");
        return Map.of("email", account.get("email"), "password", account.get("password"));
    }

    private List<Map<String, Object>> user(String role, String email) {
        return jdbc.queryForList("select id,password_hash from users where email=? and role=?", email, role);
    }

    private boolean valid(String role, Map<String, String> account) {
        return account != null && EMAILS.get(role).equals(account.get("email"))
            && account.get("password") != null && !account.get("password").isBlank();
    }

    private Map<String, Map<String, String>> readAccounts() {
        try {
            if (accountsFile == null || accountsFile.isBlank()) return Map.of();
            Path file = Path.of(accountsFile);
            if (!Files.isRegularFile(file) || Files.size(file) > 16384) return Map.of();
            Map<String, Map<String, String>> saved = mapper.readValue(Files.readString(file), new TypeReference<Map<String, Map<String, String>>>() {});
            return saved == null ? Map.of() : saved;
        } catch (Exception unavailable) {
            return Map.of();
        }
    }

    private boolean allowed(HttpServletRequest request) {
        if (!enabled || !loopback(request.getRemoteAddr()) || !loopback(request.getServerName())) return false;
        // Dev proxies must forward the original address. Every hop must be local, including
        // an appended real address after a forged X-Forwarded-For supplied by a remote caller.
        for (String header : List.of("X-Forwarded-For", "X-Real-IP")) {
            String value = request.getHeader(header);
            if (value != null && Arrays.stream(value.split(",", -1)).anyMatch(item -> !loopback(item.trim()))) return false;
        }
        if (request.getHeader("Forwarded") != null) return false;
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && Arrays.stream(forwardedHost.split(",", -1)).anyMatch(item -> !localUrl("http://" + item.trim()))) return false;
        String origin = request.getHeader("Origin");
        return origin == null || localUrl(origin);
    }

    private static boolean localUrl(String value) {
        try {
            URI uri = URI.create(value);
            return Set.of("http", "https").contains(uri.getScheme()) && uri.getUserInfo() == null && loopback(uri.getHost())
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean loopback(String value) {
        if (value == null) return false;
        String host = value.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        return Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1").contains(host);
    }
}
