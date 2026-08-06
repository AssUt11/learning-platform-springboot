package com.learning.platform.vo;

import java.util.List;

public class CourseLearningProgressView {
    private final long courseId;
    private final String courseTitle;
    private final int totalLessons;
    private final int completedLessons;
    private final int progressPercent;
    private final int totalDurationSeconds;
    private final int watchedSeconds;
    private final Long lastLessonId;
    private final List<LessonProgressView> lessons;

    public CourseLearningProgressView(long courseId, String courseTitle, int totalLessons,
                                      int completedLessons, int progressPercent, int totalDurationSeconds,
                                      int watchedSeconds, Long lastLessonId, List<LessonProgressView> lessons) {
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.totalLessons = totalLessons;
        this.completedLessons = completedLessons;
        this.progressPercent = progressPercent;
        this.totalDurationSeconds = totalDurationSeconds;
        this.watchedSeconds = watchedSeconds;
        this.lastLessonId = lastLessonId;
        this.lessons = lessons;
    }

    public long getCourseId() { return courseId; }
    public String getCourseTitle() { return courseTitle; }
    public int getTotalLessons() { return totalLessons; }
    public int getCompletedLessons() { return completedLessons; }
    public int getProgressPercent() { return progressPercent; }
    public int getTotalDurationSeconds() { return totalDurationSeconds; }
    public int getWatchedSeconds() { return watchedSeconds; }
    public Long getLastLessonId() { return lastLessonId; }
    public List<LessonProgressView> getLessons() { return lessons; }
}
