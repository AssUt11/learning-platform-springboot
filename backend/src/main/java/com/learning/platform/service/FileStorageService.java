package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<String>(Arrays.asList(
            "mp4", "pdf", "doc", "docx", "ppt", "pptx", "txt"
    ));

    private final String uploadDir;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String storeResourceFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String originalName = StringUtils.cleanPath(String.valueOf(file.getOriginalFilename()));
        if (originalName.contains("..")) {
            throw new BusinessException(400, "Invalid file name.");
        }
        String extension = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "Unsupported file type.");
        }
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(500, "Upload directory cannot be created.");
        }
        String safeName = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "")
                + "." + extension;
        file.transferTo(new File(dir, safeName));
        return "/uploads/" + safeName;
    }

    public boolean deleteStoredFile(String fileUrl) throws IOException {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return false;
        }
        if (!fileUrl.startsWith("/uploads/")) {
            return false;
        }
        String fileName = fileUrl.substring("/uploads/".length());
        if (fileName.isEmpty() || !new File(fileName).getName().equals(fileName)) {
            throw new BusinessException(400, "Invalid stored file path.");
        }
        File root = new File(uploadDir).getCanonicalFile();
        File target = new File(root, fileName).getCanonicalFile();
        if (!root.equals(target.getParentFile())) {
            throw new BusinessException(400, "Invalid stored file path.");
        }
        return Files.deleteIfExists(target.toPath());
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
