package com.learning.platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class ProfileAvatarServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ProfileAvatarService service = new ProfileAvatarService(jdbc, storage);
    private final String previous = "/uploads/avatars/11111111111111111111111111111111.png";
    private final String next = "/uploads/avatars/22222222222222222222222222222222.png";

    private void stubProfile() {
        when(jdbc.queryForList("select id,name,email,role,avatar_url from users where id=?", 8L))
            .thenReturn(List.of(Map.of("id", 8L, "name", "学习者", "email", "student@example.com", "role", "student", "avatar_url", previous)));
    }

    @Test
    void savesAvatarOnlyForRequestedAuthenticatedAccount() throws Exception {
        stubProfile();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});
        when(storage.storeAvatarFile(file)).thenReturn(next);
        when(jdbc.update("update users set avatar_url=? where id=?", next, 8L)).thenReturn(1);

        Map<String, Object> profile = service.updateAvatar(8L, file);

        assertEquals(next, profile.get("avatarUrl"));
        assertEquals(8L, profile.get("id"));
        assertFalse(profile.containsKey("password_hash"));
        verify(jdbc).update("update users set avatar_url=? where id=?", next, 8L);
        verify(storage).deleteStoredAvatar(previous);
    }

    @Test
    void removesNewFileAndKeepsExistingAvatarWhenDatabaseUpdateFails() throws Exception {
        stubProfile();
        MockMultipartFile file = new MockMultipartFile("file", new byte[] {1});
        when(storage.storeAvatarFile(file)).thenReturn(next);
        when(jdbc.update(anyString(), eq(next), eq(8L))).thenThrow(new IllegalStateException("db unavailable"));

        assertThrows(IllegalStateException.class, () -> service.updateAvatar(8L, file));

        verify(storage).deleteStoredAvatar(next);
        verify(storage, never()).deleteStoredAvatar(previous);
    }

    @Test
    void cleanupFailureDoesNotUndoSuccessfulProfileSave() throws Exception {
        stubProfile();
        MockMultipartFile file = new MockMultipartFile("file", new byte[] {1});
        when(storage.storeAvatarFile(file)).thenReturn(next);
        when(jdbc.update("update users set avatar_url=? where id=?", next, 8L)).thenReturn(1);
        doThrow(new IOException("locked")).when(storage).deleteStoredAvatar(previous);

        assertEquals(next, service.updateAvatar(8L, file).get("avatarUrl"));
    }
}
