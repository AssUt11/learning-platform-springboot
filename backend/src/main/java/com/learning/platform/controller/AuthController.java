package com.learning.platform.controller;

import com.learning.platform.service.TokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        String role = body.get("role");
        if (name == null || email == null || password == null || !("teacher".equals(role) || "student".equals(role))) {
            throw new RuntimeException("Registration information is incomplete.");
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from users where email=?", Integer.class, email);
        if (count != null && count > 0) {
            throw new RuntimeException("Email already exists.");
        }
        jdbcTemplate.update("insert into users(name,email,password_hash,role) values(?,?,?,?)",
                name, email, encoder.encode(password), role);
        Long id = jdbcTemplate.queryForObject("select id from users where email=?", Long.class, email);
        Map<String, Object> user = userMap(id, name, email, role);
        Map<String, Object> result = response(user);
        result.put("needsInterestSelection", "student".equals(role));
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "select * from users where email=?", body.get("email"));
        if (users.isEmpty() || !encoder.matches(body.get("password"), String.valueOf(users.get(0).get("password_hash")))) {
            throw new RuntimeException("Email or password is incorrect.");
        }
        Map<String, Object> row = users.get(0);
        Map<String, Object> user = userMap(((Number) row.get("id")).longValue(),
                String.valueOf(row.get("name")), String.valueOf(row.get("email")), String.valueOf(row.get("role")));
        Map<String, Object> result = response(user);
        if ("student".equals(user.get("role"))) {
            result.put("needsInterestSelection", !hasPreferences(user.get("id")));
        }
        return result;
    }

    private Map<String, Object> response(Map<String, Object> user) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("user", user);
        result.put("token", tokenService.create(user));
        return result;
    }

    private Map<String, Object> userMap(Long id, String name, String email, String role) {
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", id);
        user.put("name", name);
        user.put("email", email);
        user.put("role", role);
        return user;
    }

    private boolean hasPreferences(Object studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student_preferences where student_id=?", Integer.class, studentId);
        return count != null && count > 0;
    }
}
