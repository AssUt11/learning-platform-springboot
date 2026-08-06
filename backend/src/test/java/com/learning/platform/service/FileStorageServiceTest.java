package com.learning.platform.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {
    @TempDir
    Path uploadDir;

    @Test
    void storesAllowedFileWithGeneratedName() throws Exception {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));

        String url = service.storeResourceFile(file);

        assertNotNull(url);
        assertTrue(url.startsWith("/uploads/"));
        assertTrue(url.endsWith(".pdf"));
        Path stored = uploadDir.resolve(url.substring("/uploads/".length()));
        assertTrue(Files.isRegularFile(stored));
        assertEquals("content", Files.readString(stored));
    }

    @Test
    void rejectsUnsupportedExtension() {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", new byte[]{1, 2, 3});

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.storeResourceFile(file));

        assertEquals("Unsupported file type.", exception.getMessage());
    }

    @Test
    void ignoresEmptyFile() throws Exception {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertNull(service.storeResourceFile(file));
    }

    @Test
    void deletesOnlyFilesInsideUploadDirectory() throws Exception {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        Path stored = uploadDir.resolve("123-abcdef.pdf");
        Files.writeString(stored, "content");

        assertTrue(service.deleteStoredFile("/uploads/123-abcdef.pdf"));
        assertFalse(Files.exists(stored));
        assertFalse(service.deleteStoredFile("https://example.com/external.pdf"));
    }

    @Test
    void rejectsTraversalWhenDeletingStoredFile() throws Exception {
        Path nestedUploadDir = Files.createDirectory(uploadDir.resolve("uploads"));
        FileStorageService service = new FileStorageService(nestedUploadDir.toString());
        Path outside = uploadDir.resolve("outside.pdf");
        Files.writeString(outside, "keep");

        assertThrows(RuntimeException.class,
                () -> service.deleteStoredFile("/uploads/../outside.pdf"));
        assertTrue(Files.exists(outside));
    }
}
