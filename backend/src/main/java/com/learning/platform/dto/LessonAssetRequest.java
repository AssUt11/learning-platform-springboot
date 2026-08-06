package com.learning.platform.dto;

public class LessonAssetRequest {
    private String assetType;
    private String contentText;
    private String coverUrl;
    private String transcodingStatus;

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getTranscodingStatus() { return transcodingStatus; }
    public void setTranscodingStatus(String transcodingStatus) { this.transcodingStatus = transcodingStatus; }
}
