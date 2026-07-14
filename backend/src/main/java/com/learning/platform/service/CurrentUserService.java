package com.learning.platform.service;

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
            throw new RuntimeException("Please log in first.");
        }
        return user;
    }

    public Map<String, Object> requireRole(String authorization, String role) {
        Map<String, Object> user = requireLogin(authorization);
        if (!role.equals(user.get("role"))) {
            throw new RuntimeException("Current account does not have permission.");
        }
        return user;
    }

    public Map<String, Object> requireStudent(String authorization) {
        return requireRole(authorization, "student");
    }

    public Map<String, Object> requireTeacher(String authorization) {
        return requireRole(authorization, "teacher");
    }
}
