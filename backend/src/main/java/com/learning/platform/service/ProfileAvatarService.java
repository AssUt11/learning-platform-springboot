package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileAvatarService {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileAvatarService.class);
    private final JdbcTemplate jdbc;
    private final FileStorageService storage;

    public ProfileAvatarService(JdbcTemplate jdbc, FileStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public Map<String, Object> profile(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id,name,email,role,avatar_url from users where id=?",
            userId
        );
        if (rows.size() != 1) {
            throw new BusinessException(401, "账号已失效，请重新登录。");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ((Number) row.get("id")).longValue());
        result.put("name", row.get("name"));
        result.put("email", row.get("email"));
        result.put("role", row.get("role"));
        result.put("avatarUrl", row.get("avatar_url") == null ? "" : row.get("avatar_url"));
        return result;
    }

    public Map<String, Object> updateAvatar(long userId, MultipartFile file) throws IOException {
        Map<String, Object> previous = profile(userId);
        String storedUrl = storage.storeAvatarFile(file);
        try {
            int changed = jdbc.update("update users set avatar_url=? where id=?", storedUrl, userId);
            if (changed != 1) {
                throw new BusinessException(401, "账号已失效，请重新登录。");
            }
        } catch (RuntimeException error) {
            removeUnusedAvatar(storedUrl);
            throw error;
        }
        // Only generated avatar files are eligible for cleanup; course resources are untouched.
        removeUnusedAvatar(String.valueOf(previous.get("avatarUrl")));
        previous.put("avatarUrl", storedUrl);
        return previous;
    }

    private void removeUnusedAvatar(String url) {
        try {
            storage.deleteStoredAvatar(url);
        } catch (IOException error) {
            LOG.warn("An unused avatar file could not be removed.");
        }
    }
}
