package com.learning.platform.vo;

import java.sql.Timestamp;

public class LessonProgressView {
    private final long courseId;
    private final long lessonId;
    private final int lastPositionSeconds;
    private final int durationSeconds;
    private final int progressPercent;
    private final boolean completed;
    private final Timestamp lastStudiedAt;

    public LessonProgressView(long courseId, long lessonId, int lastPositionSeconds, int durationSeconds,
                              int progressPercent, boolean completed, Timestamp lastStudiedAt) {
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.lastPositionSeconds = lastPositionSeconds;
        this.durationSeconds = durationSeconds;
        this.progressPercent = progressPercent;
        this.completed = completed;
        this.lastStudiedAt = lastStudiedAt;
    }

    public long getCourseId() { return courseId; }
    public long getLessonId() { return lessonId; }
    public int getLastPositionSeconds() { return lastPositionSeconds; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getProgressPercent() { return progressPercent; }
    public boolean isCompleted() { return completed; }
    public Timestamp getLastStudiedAt() { return lastStudiedAt; }
}
