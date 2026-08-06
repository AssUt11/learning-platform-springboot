package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CurrentUserService {
    private final TokenService tokenService;

    public CurrentUserService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Map<String, Object> optional(String authorization) {
        return tokenService.getUser(authorization);
    }

    public Map<String, Object> requireLogin(String authorization) {
        Map<String, Object> user = optional(authorization);
        if (user == null) {
            throw new BusinessException(401, "Please log in first or sign in again.");
        }
        return user;
    }

    public Map<String, Object> requireRole(String authorization, String role) {
        Map<String, Object> user = requireLogin(authorization);
        if (!role.equals(user.get("role"))) {
            throw new BusinessException(403, "Current account does not have permission.");
        }
        return user;
    }

    public Map<String, Object> requireStudent(String authorization) {
        return requireRole(authorization, "student");
    }

    public Map<String, Object> requireLearner(String authorization) {
        Map<String, Object> user = requireLogin(authorization);
        Object role = user.get("role");
        if (!("student".equals(role) || "teacher".equals(role))) {
            throw new BusinessException(403, "Current account does not have learning permission.");
        }
        return user;
    }

    public Map<String, Object> requireTeacher(String authorization) {
        return requireRole(authorization, "teacher");
    }

    public Map<String, Object> requireAdmin(String authorization) {
        return requireRole(authorization, "admin");
    }
}
