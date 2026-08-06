package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    @Test
    void learnerPermissionIncludesStudentsAndTeachers() {
        TokenService tokenService = mock(TokenService.class);
        CurrentUserService service = new CurrentUserService(tokenService);
        when(tokenService.getUser("student-token")).thenReturn(user("student"));
        when(tokenService.getUser("teacher-token")).thenReturn(user("teacher"));

        assertEquals("student", service.requireLearner("student-token").get("role"));
        assertEquals("teacher", service.requireLearner("teacher-token").get("role"));
    }

    @Test
    void learnerPermissionStillRejectsAdministrator() {
        TokenService tokenService = mock(TokenService.class);
        CurrentUserService service = new CurrentUserService(tokenService);
        when(tokenService.getUser("admin-token")).thenReturn(user("admin"));

        assertThrows(BusinessException.class, () -> service.requireLearner("admin-token"));
    }

    private Map<String, Object> user(String role) {
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", 7L);
        user.put("name", "测试用户");
        user.put("role", role);
        return user;
    }
}
