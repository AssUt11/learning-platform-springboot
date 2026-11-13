package com.learning.platform.ai.context;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, server-selected reference to a teacher-confirmed document fragment. */
public final class CoursewareSnippet {

    private final long chunkId;
    private final String documentId;
    private final long assetId;
    private final long courseId;
    private final long lessonId;
    private final String versionHash;
    private final String fileName;
    private final String locationType;
    private final int locationIndex;
    private final String content;

    public CoursewareSnippet(long chunkId, String documentId, long assetId, long courseId,
        long lessonId, String versionHash, String fileName, String locationType,
        int locationIndex, String content) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.assetId = assetId;
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.versionHash = versionHash;
        this.fileName = fileName;
        this.locationType = locationType;
        this.locationIndex = locationIndex;
        this.content = content;
    }

    public long getChunkId() { return chunkId; }
    public long getCourseId() { return courseId; }
    public long getLessonId() { return lessonId; }
    public String getContent() { return content; }

    public String title() {
        String position = "PAGE".equals(locationType) ? "第 " + locationIndex + " 页"
            : "SLIDE".equals(locationType) ? "幻灯片 " + locationIndex
            : "文本段 " + locationIndex;
        return fileName + " · " + position;
    }

    public String promptText(int referenceNumber) {
        return "[资料" + referenceNumber + "] " + title() + "\n" + content + "\n";
    }

    public Map<String, Object> citation() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", "COURSEWARE_CHUNK");
        result.put("sourceId", chunkId);
        result.put("title", title());
        result.put("courseId", courseId);
        result.put("lessonId", lessonId);
        result.put("assetId", assetId);
        result.put("documentId", documentId);
        result.put("chunkId", chunkId);
        result.put("versionHash", versionHash);
        result.put("fileName", fileName);
        result.put("locationType", locationType);
        result.put("locationIndex", locationIndex);
        result.put("excerpt", excerpt(content, 360));
        result.put("scope", "retrieved_context");
        result.put("url", "/api/learning/courseware/chunks/" + chunkId
            + "?courseId=" + courseId + "&lessonId=" + lessonId);
        return result;
    }

    private static String excerpt(String value, int length) {
        if (value == null) return "";
        if (value.length() <= length) return value;
        int end = Character.isHighSurrogate(value.charAt(length - 1)) ? length - 1 : length;
        return value.substring(0, end) + "…";
    }
}
