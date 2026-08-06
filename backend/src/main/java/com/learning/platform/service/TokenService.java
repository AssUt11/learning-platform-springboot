package com.learning.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_VERSION = "v1";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final int MAXIMUM_TOKEN_LENGTH = 2048;
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final JdbcTemplate jdbcTemplate;
    private final byte[] secret;
    private final long tokenTtlSeconds;

    public TokenService(JdbcTemplate jdbcTemplate,
                        @Value("${app.auth.token-secret}") String tokenSecret,
                        @Value("${app.auth.token-ttl-seconds:86400}") long tokenTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.secret = tokenSecret == null ? new byte[0] : tokenSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("app.auth.token-secret must contain at least 32 bytes.");
        }
        if (tokenTtlSeconds <= 0 || tokenTtlSeconds > 31_536_000L) {
            throw new IllegalStateException("app.auth.token-ttl-seconds must be between 1 and 31536000.");
        }
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public String create(Map<String, Object> user) {
        long userId = positiveLong(user == null ? null : user.get("id"));
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = Math.addExact(issuedAt, tokenTtlSeconds);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = String.join("|", TOKEN_VERSION, String.valueOf(userId),
                String.valueOf(issuedAt), String.valueOf(expiresAt), nonce);
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + encode(sign(encodedPayload));
    }

    public Map<String, Object> getUser(String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            return null;
        }

        String[] tokenParts = token.split("\\.", -1);
        if (tokenParts.length != 2 || tokenParts[0].isEmpty() || tokenParts[1].isEmpty()) {
            return null;
        }

        try {
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            byte[] expectedSignature = sign(tokenParts[0]);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|", -1);
            if (payloadParts.length != 5 || !TOKEN_VERSION.equals(payloadParts[0]) || payloadParts[4].length() != 32) {
                return null;
            }

            long userId = positiveLong(payloadParts[1]);
            long issuedAt = Long.parseLong(payloadParts[2]);
            long expiresAt = Long.parseLong(payloadParts[3]);
            long now = Instant.now().getEpochSecond();
            if (issuedAt > now + CLOCK_SKEW_SECONDS || expiresAt <= now || expiresAt <= issuedAt) {
                return null;
            }

            return loadCurrentUser(userId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<String, Object> loadCurrentUser(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,name,email,role from users where id=?", userId);
        if (rows.size() != 1) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", ((Number) row.get("id")).longValue());
        user.put("name", String.valueOf(row.get("name")));
        user.put("email", String.valueOf(row.get("email")));
        user.put("role", String.valueOf(row.get("role")));
        return user;
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        int separator = value.indexOf(' ');
        if (separator <= 0 || !"Bearer".equalsIgnoreCase(value.substring(0, separator))) {
            return null;
        }
        String token = value.substring(separator + 1).trim();
        if (token.isEmpty() || token.length() > MAXIMUM_TOKEN_LENGTH || token.indexOf(' ') >= 0) {
            return null;
        }
        return token;
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create authentication token.", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private long positiveLong(Object value) {
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            if (parsed <= 0) {
                throw new IllegalArgumentException("Value must be positive.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Value must be a positive integer.", exception);
        }
    }
}
