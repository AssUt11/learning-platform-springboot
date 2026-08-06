package com.learning.platform.vo;

import java.util.List;

public class CourseContentValidationResult {
    private final long courseId;
    private final String title;
    private final String resourceType;
    private final boolean contentComplete;
    private final boolean publishable;
    private final int chapterCount;
    private final int totalLessonCount;
    private final int publishedLessonCount;
    private final int totalDurationSeconds;
    private final int playableVideoCount;
    private final int invalidPublishedLessonCount;
    private final boolean ownershipMissing;
    private final int status;
    private final String reviewStatus;
    private final List<String> reasonCodes;
    private final List<String> reasons;

    public CourseContentValidationResult(long courseId, String title, String resourceType,
                                         boolean contentComplete, boolean publishable,
                                         int chapterCount, int totalLessonCount, int publishedLessonCount,
                                         int totalDurationSeconds, int playableVideoCount, int invalidPublishedLessonCount,
                                         boolean ownershipMissing, int status, String reviewStatus,
                                         List<String> reasonCodes, List<String> reasons) {
        this.courseId = courseId;
        this.title = title;
        this.resourceType = resourceType;
        this.contentComplete = contentComplete;
        this.publishable = publishable;
        this.chapterCount = chapterCount;
        this.totalLessonCount = totalLessonCount;
        this.publishedLessonCount = publishedLessonCount;
        this.totalDurationSeconds = totalDurationSeconds;
        this.playableVideoCount = playableVideoCount;
        this.invalidPublishedLessonCount = invalidPublishedLessonCount;
        this.ownershipMissing = ownershipMissing;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.reasonCodes = reasonCodes;
        this.reasons = reasons;
    }

    public long getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public String getResourceType() { return resourceType; }
    public boolean isContentComplete() { return contentComplete; }
    public boolean isPublishable() { return publishable; }
    public int getChapterCount() { return chapterCount; }
    public int getTotalLessonCount() { return totalLessonCount; }
    public int getPublishedLessonCount() { return publishedLessonCount; }
    public int getTotalDurationSeconds() { return totalDurationSeconds; }
    public int getPlayableVideoCount() { return playableVideoCount; }
    public int getInvalidPublishedLessonCount() { return invalidPublishedLessonCount; }
    public boolean isOwnershipMissing() { return ownershipMissing; }
    public int getStatus() { return status; }
    public String getReviewStatus() { return reviewStatus; }
    public List<String> getReasonCodes() { return reasonCodes; }
    public List<String> getReasons() { return reasons; }
}
