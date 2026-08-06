package com.learning.platform.vo;

import java.util.List;

public class LessonView {
    private final long id;
    private final String title;
    private final String lessonType;
    private final int durationSeconds;
    private final boolean freePreview;
    private final int sortOrder;
    private final String status;
    private final boolean assetReady;
    private final String playbackUrl;
    private final String contentText;
    private final int progress;
    private final boolean completed;
    private final int lastPositionSeconds;
    private final List<LessonAssetView> assets;

    public LessonView(long id, String title, String lessonType, int durationSeconds, boolean freePreview,
                      int sortOrder, String status, boolean assetReady, String playbackUrl, String contentText,
                      List<LessonAssetView> assets) {
        this.id = id;
        this.title = title;
        this.lessonType = lessonType;
        this.durationSeconds = durationSeconds;
        this.freePreview = freePreview;
        this.sortOrder = sortOrder;
        this.status = status;
        this.assetReady = assetReady;
        this.playbackUrl = playbackUrl;
        this.contentText = contentText;
        this.progress = 0;
        this.completed = false;
        this.lastPositionSeconds = 0;
        this.assets = assets;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getLessonType() { return lessonType; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isFreePreview() { return freePreview; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }
    public boolean isAssetReady() { return assetReady; }
    public String getPlaybackUrl() { return playbackUrl; }
    public String getContentText() { return contentText; }
    public int getProgress() { return progress; }
    public boolean isCompleted() { return completed; }
    public int getLastPositionSeconds() { return lastPositionSeconds; }
    public List<LessonAssetView> getAssets() { return assets; }
}
