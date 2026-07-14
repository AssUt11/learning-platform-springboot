package com.learning.platform.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private final Map<String, Map<String, Object>> tokens = new ConcurrentHashMap<>();

    public String create(Map<String, Object> user) {
        String raw = value(user.get("id")) + "|" + value(user.get("name")) + "|" + value(user.get("email")) + "|" + value(user.get("role"));
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        tokens.put(token, user);
        return token;
    }

    public Map<String, Object> getUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        Map<String, Object> user = tokens.get(token);
        if (user != null) {
            return user;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4) {
                return null;
            }
            Map<String, Object> decoded = new HashMap<String, Object>();
            decoded.put("id", Long.parseLong(parts[0]));
            decoded.put("name", parts[1]);
            decoded.put("email", parts[2]);
            decoded.put("role", parts[3]);
            tokens.put(token, decoded);
            return decoded;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).replace("|", "");
    }
}
