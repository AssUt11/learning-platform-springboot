package com.learning.platform.vo;

import java.util.List;

public class ChapterView {
    private final long id;
    private final String title;
    private final String description;
    private final int sortOrder;
    private final String status;
    private final List<LessonView> lessons;

    public ChapterView(long id, String title, String description, int sortOrder, String status,
                       List<LessonView> lessons) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
        this.status = status;
        this.lessons = lessons;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }
    public List<LessonView> getLessons() { return lessons; }
}
