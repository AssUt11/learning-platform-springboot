package com.learning.platform.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.exception.GlobalExceptionHandler;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.ProfileAvatarService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {

    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final ProfileAvatarService avatars = mock(ProfileAvatarService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProfileController(currentUser, avatars))
        .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void rejectsAnonymousProfileRequests() throws Exception {
        when(currentUser.requireLogin(null)).thenThrow(new BusinessException(401, "Please log in first."));

        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());

        verifyNoInteractions(avatars);
    }

    @Test
    void ignoresClientAccountIdAndUsesAuthenticatedUserForAvatarUpdate() throws Exception {
        when(currentUser.requireLogin("Bearer signed-user-8")).thenReturn(Map.of("id", 8L));
        when(avatars.updateAvatar(eq(8L), any())).thenReturn(Map.of("id", 8L, "avatarUrl", "/uploads/avatars/new.png"));
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        mvc.perform(multipart("/api/profile/avatar").file(file).param("userId", "999")
            .header("Authorization", "Bearer signed-user-8").with(request -> { request.setMethod("PUT"); return request; }))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(8));

        verify(avatars).updateAvatar(eq(8L), any());
        verify(avatars, never()).updateAvatar(eq(999L), any());
    }
}
