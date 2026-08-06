package com.learning.platform.vo;

public class LessonAssetView {
    private final long id;
    private final String assetType;
    private final String fileName;
    private final Long fileSize;
    private final String mimeType;
    private final String coverUrl;
    private final String contentText;
    private final String transcodingStatus;
    private final String playbackUrl;

    public LessonAssetView(long id, String assetType, String fileName, Long fileSize, String mimeType,
                           String coverUrl, String contentText, String transcodingStatus, String playbackUrl) {
        this.id = id;
        this.assetType = assetType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.coverUrl = coverUrl;
        this.contentText = contentText;
        this.transcodingStatus = transcodingStatus;
        this.playbackUrl = playbackUrl;
    }

    public long getId() { return id; }
    public String getAssetType() { return assetType; }
    public String getFileName() { return fileName; }
    public Long getFileSize() { return fileSize; }
    public String getMimeType() { return mimeType; }
    public String getCoverUrl() { return coverUrl; }
    public String getContentText() { return contentText; }
    public String getTranscodingStatus() { return transcodingStatus; }
    public String getPlaybackUrl() { return playbackUrl; }
}
