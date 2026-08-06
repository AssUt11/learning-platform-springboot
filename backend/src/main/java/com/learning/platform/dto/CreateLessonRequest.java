package com.learning.platform.dto;

public class CreateLessonRequest {
    private String title;
    private String lessonType;
    private Integer durationSeconds;
    private Boolean freePreview;
    private Integer sortOrder;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLessonType() { return lessonType; }
    public void setLessonType(String lessonType) { this.lessonType = lessonType; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Boolean getFreePreview() { return freePreview; }
    public void setFreePreview(Boolean freePreview) { this.freePreview = freePreview; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
