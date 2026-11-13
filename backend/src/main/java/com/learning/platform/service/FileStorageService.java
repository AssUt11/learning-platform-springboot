package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;
    private static final long AVATAR_MAX_PIXELS = 16_000_000L;
    private static final int AVATAR_SIDE = 512;

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<String>(
        Arrays.asList("mp4", "pdf", "doc", "docx", "ppt", "pptx", "txt")
    );

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
        String safeName =
            System.currentTimeMillis() +
            "-" +
            UUID.randomUUID().toString().replace("-", "") +
            "." +
            extension;
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

    /** Decode and re-encode uploaded photos so stored avatars contain pixels only. */
    public String storeAvatarFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请先选择一张照片。");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new BusinessException(400, "照片不能超过 5 MB。");
        }
        String mime = String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
        if (!Set.of("image/png", "image/jpeg").contains(mime)) {
            throw new BusinessException(400, "请上传已转换为 PNG 或 JPEG 的照片。");
        }
        BufferedImage source;
        try (
            InputStream input = file.getInputStream();
            ImageInputStream imageInput = ImageIO.createImageInputStream(input)
        ) {
            if (imageInput == null) throw invalidAvatar();
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw invalidAvatar();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw invalidAvatar();
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > AVATAR_MAX_PIXELS) {
                    throw new BusinessException(400, "照片尺寸过大，请选择不超过 1600 万像素的图片。");
                }
                source = reader.read(0);
                if (source == null) throw invalidAvatar();
            } finally {
                reader.dispose();
            }
        } catch (BusinessException error) {
            throw error;
        } catch (IOException | IllegalArgumentException error) {
            throw invalidAvatar();
        }
        BufferedImage avatar = new BufferedImage(AVATAR_SIDE, AVATAR_SIDE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = avatar.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int side = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - side) / 2;
            int y = (source.getHeight() - side) / 2;
            graphics.drawImage(source, 0, 0, AVATAR_SIDE, AVATAR_SIDE, x, y, x + side, y + side, null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        Path directory = new File(uploadDir).getCanonicalFile().toPath().resolve("avatars");
        Files.createDirectories(directory);
        String filename = UUID.randomUUID().toString().replace("-", "") + ".png";
        Path destination = directory.resolve(filename);
        try {
            if (!ImageIO.write(avatar, "png", destination.toFile())) {
                throw new IOException("Avatar image encoder is unavailable.");
            }
        } catch (IOException error) {
            Files.deleteIfExists(destination);
            throw error;
        } finally {
            avatar.flush();
        }
        return "/uploads/avatars/" + filename;
    }

    public boolean deleteStoredAvatar(String url) throws IOException {
        if (url == null || !url.matches("/uploads/avatars/[a-f0-9]{32}\\.png")) return false;
        Path directory = new File(uploadDir).getCanonicalFile().toPath().resolve("avatars");
        Path target = directory.resolve(url.substring("/uploads/avatars/".length())).normalize();
        if (!target.getParent().equals(directory)) return false;
        return Files.deleteIfExists(target);
    }

    private BusinessException invalidAvatar() {
        return new BusinessException(400, "这张照片无法读取，请重新选择 JPEG、PNG 或 WebP 图片。");
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
