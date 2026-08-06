package com.learning.platform.vo;

import java.util.List;

public class CourseOutlineView {
    private final long courseId;
    private final String title;
    private final int chapterCount;
    private final int lessonCount;
    private final int totalDurationSeconds;
    private final List<ChapterView> chapters;

    public CourseOutlineView(long courseId, String title, int chapterCount, int lessonCount,
                             int totalDurationSeconds, List<ChapterView> chapters) {
        this.courseId = courseId;
        this.title = title;
        this.chapterCount = chapterCount;
        this.lessonCount = lessonCount;
        this.totalDurationSeconds = totalDurationSeconds;
        this.chapters = chapters;
    }

    public long getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public int getChapterCount() { return chapterCount; }
    public int getLessonCount() { return lessonCount; }
    public int getTotalDurationSeconds() { return totalDurationSeconds; }
    public List<ChapterView> getChapters() { return chapters; }
}
