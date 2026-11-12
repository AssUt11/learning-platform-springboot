package com.learning.platform.courseware;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** The same database-row fingerprint is used by extraction and retrieval. */
public final class CoursewareAssetVersion {
    private CoursewareAssetVersion() {}

    public static String of(Map<String, Object> row) {
        String source = String.join("\n", value(row, "assetId"), value(row, "fileUrl"),
            value(row, "fileSize"), value(row, "assetType"), value(row, "assetUpdatedAt"), value(row, "contentText"));
        return sha256(source.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String value(Map<String, Object> row, String key) {
        return row.get(key) == null ? "" : String.valueOf(row.get(key));
    }
}
