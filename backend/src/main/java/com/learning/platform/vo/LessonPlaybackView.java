package com.learning.platform.vo;

public class LessonPlaybackView {
    private final long courseId;
    private final long lessonId;
    private final String lessonType;
    private final boolean playable;
    private final Long assetId;
    private final String videoUrl;
    private final int durationSeconds;
    private final String reasonCode;

    public LessonPlaybackView(long courseId, long lessonId, String lessonType, boolean playable,
                              Long assetId, String videoUrl, int durationSeconds, String reasonCode) {
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.lessonType = lessonType;
        this.playable = playable;
        this.assetId = assetId;
        this.videoUrl = videoUrl;
        this.durationSeconds = durationSeconds;
        this.reasonCode = reasonCode;
    }

    public long getCourseId() { return courseId; }
    public long getLessonId() { return lessonId; }
    public String getLessonType() { return lessonType; }
    public boolean isPlayable() { return playable; }
    public Long getAssetId() { return assetId; }
    public String getVideoUrl() { return videoUrl; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getReasonCode() { return reasonCode; }
}
