package com.learning.platform.ai.dto;

public class AiFeedbackRequest {
    private Integer rating;
    private String reason;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
