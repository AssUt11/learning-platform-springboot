package com.learning.platform.vo;

import java.sql.Timestamp;

public class ResumeLearningView {
    private final boolean available;
    private final Long courseId;
    private final String courseTitle;
    private final Long lessonId;
    private final String lessonTitle;
    private final int lastPositionSeconds;
    private final int progressPercent;
    private final Timestamp lastStudiedAt;

    public ResumeLearningView(boolean available, Long courseId, String courseTitle, Long lessonId,
                              String lessonTitle, int lastPositionSeconds, int progressPercent,
                              Timestamp lastStudiedAt) {
        this.available = available;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.lessonId = lessonId;
        this.lessonTitle = lessonTitle;
        this.lastPositionSeconds = lastPositionSeconds;
        this.progressPercent = progressPercent;
        this.lastStudiedAt = lastStudiedAt;
    }

    public static ResumeLearningView unavailable() {
        return new ResumeLearningView(false, null, null, null, null, 0, 0, null);
    }

    public boolean isAvailable() { return available; }
    public Long getCourseId() { return courseId; }
    public String getCourseTitle() { return courseTitle; }
    public Long getLessonId() { return lessonId; }
    public String getLessonTitle() { return lessonTitle; }
    public int getLastPositionSeconds() { return lastPositionSeconds; }
    public int getProgressPercent() { return progressPercent; }
    public Timestamp getLastStudiedAt() { return lastStudiedAt; }
}
