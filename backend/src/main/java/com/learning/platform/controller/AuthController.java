package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);
    private static final int MINIMUM_PASSWORD_CHARACTERS = 8;
    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final byte[] teacherRegistrationCode;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbcTemplate,
                          TokenService tokenService,
                          @Value("${app.auth.teacher-registration-code}") String teacherRegistrationCode) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.teacherRegistrationCode = text(teacherRegistrationCode).getBytes(StandardCharsets.UTF_8);
        if (this.teacherRegistrationCode.length < 8) {
            throw new IllegalStateException("app.auth.teacher-registration-code must contain at least 8 bytes.");
        }
    }

    @PostMapping("/register")
    @Transactional
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        requireBody(body);
        String name = normalizedName(body.get("name"));
        String email = normalizedEmail(body.get("email"));
        String password = body.get("password");
        String role = text(body.get("role")).toLowerCase(Locale.ROOT);

        validateName(name);
        validateEmail(email);
        validatePassword(password);
        if (!("teacher".equals(role) || "student".equals(role))) {
            throw new BusinessException(400, "Role must be either student or teacher.");
        }
        if ("teacher".equals(role)) {
            requireTeacherRegistrationCode(body.get("teacherRegistrationCode"));
        }

        Integer count = jdbcTemplate.queryForObject("select count(*) from users where email=?", Integer.class, email);
        if (count != null && count > 0) {
            throw new BusinessException(409, "Email already exists.");
        }

        try {
            jdbcTemplate.update("insert into users(name,email,password_hash,role) values(?,?,?,?)",
                    name, email, encoder.encode(password), role);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "Email already exists.");
        }

        Long id = jdbcTemplate.queryForObject("select id from users where email=?", Long.class, email);
        Map<String, Object> user = userMap(id, name, email, role);
        Map<String, Object> result = response(user);
        result.put("needsInterestSelection", "student".equals(role));
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        requireBody(body);
        String email = normalizedEmail(body.get("email"));
        String password = body.get("password");
        validateEmail(email);
        if (password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw new BusinessException(400, "Email and password are required.");
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "select id,name,email,password_hash,role from users where email=?", email);
        if (users.isEmpty() || !encoder.matches(password, String.valueOf(users.get(0).get("password_hash")))) {
            throw new BusinessException(401, "Email or password is incorrect.");
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

    private void validateName(String name) {
        if (name.isEmpty() || name.length() > 80) {
            throw new BusinessException(400, "Name is required and must not exceed 80 characters.");
        }
    }

    private void validateEmail(String email) {
        int separator = email.indexOf('@');
        String localPart = separator > 0 ? email.substring(0, separator) : "";
        if (email.isEmpty() || email.length() > 120
                || localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")
                || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(400, "Please provide a valid email address.");
        }
    }

    private void validatePassword(String password) {
        if (password == null
                || password.codePointCount(0, password.length()) < MINIMUM_PASSWORD_CHARACTERS
                || password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw new BusinessException(400, "Password must be at least 8 characters and at most 72 UTF-8 bytes.");
        }
    }

    private void requireTeacherRegistrationCode(String suppliedCode) {
        String normalizedCode = text(suppliedCode);
        if (normalizedCode.length() > 256) {
            throw new BusinessException(403, "Teacher registration code is incorrect.");
        }
        byte[] supplied = normalizedCode.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(teacherRegistrationCode, supplied)) {
            throw new BusinessException(403, "Teacher registration code is incorrect.");
        }
    }

    private void requireBody(Map<String, String> body) {
        if (body == null) {
            throw new BusinessException(400, "Request body is required.");
        }
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

    private String normalizedName(String value) {
        return text(value).replaceAll("\\s+", " ");
    }

    private String normalizedEmail(String value) {
        return text(value).toLowerCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
