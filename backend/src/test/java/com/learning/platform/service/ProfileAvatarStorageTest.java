package com.learning.platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.learning.platform.exception.BusinessException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ProfileAvatarStorageTest {

    @TempDir
    Path uploads;

    @Test
    void reencodesPhotoIntoGeneratedSquarePngInsideAvatarDirectory() throws Exception {
        FileStorageService storage = new FileStorageService(uploads.toString());
        BufferedImage source = new BufferedImage(12, 6, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 12, 6);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", bytes);

        String url = storage.storeAvatarFile(new MockMultipartFile("file", "../../photo.jpg", "image/jpeg", bytes.toByteArray()));

        assertTrue(url.matches("/uploads/avatars/[a-f0-9]{32}\\.png"));
        Path stored = uploads.resolve(url.substring("/uploads/".length()));
        BufferedImage avatar = ImageIO.read(stored.toFile());
        assertEquals(512, avatar.getWidth());
        assertEquals(512, avatar.getHeight());
        assertEquals(Color.BLUE.getBlue(), new Color(avatar.getRGB(256, 256)).getBlue(), 2);
        assertTrue(storage.deleteStoredAvatar(url));
        assertFalse(Files.exists(stored));
    }

    @Test
    void rejectsSvgDisguisedAsPhotoWithoutWritingFile() throws Exception {
        FileStorageService storage = new FileStorageService(uploads.toString());
        MockMultipartFile forged = new MockMultipartFile("file", "image.png", "image/png",
            "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class, () -> storage.storeAvatarFile(forged));
        try (var files = Files.list(uploads)) { assertEquals(0, files.count()); }
    }

    @Test
    void rejectsOversizedPhotosBeforeReadingBytes() throws Exception {
        FileStorageService storage = new FileStorageService(uploads.toString());
        MultipartFile large = mock(MultipartFile.class);
        when(large.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        assertThrows(BusinessException.class, () -> storage.storeAvatarFile(large));
        verify(large, never()).getInputStream();
    }

    @Test
    void cleanupCannotDeleteCourseResourcesOrTraverseOutsideAvatarDirectory() throws Exception {
        FileStorageService storage = new FileStorageService(uploads.toString());
        Path lesson = uploads.resolve("lesson.pdf");
        Files.writeString(lesson, "keep this course file");

        assertFalse(storage.deleteStoredAvatar("/uploads/lesson.pdf"));
        assertFalse(storage.deleteStoredAvatar("/uploads/avatars/../../lesson.pdf"));
        assertFalse(storage.deleteStoredAvatar("https://example.com/avatar.png"));
        assertTrue(Files.exists(lesson));
    }
}
