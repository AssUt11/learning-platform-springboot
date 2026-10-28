package com.learning.platform.pattern;

import java.util.List;
import java.util.Map;

public class RecommendationContext {
    private final Object studentId;
    private final String studentName;
    private final String subject;
    private final int recordCount;
    private final int recentRecordCount;
    private final int averageProgress;
    private final List<Map<String, Object>> resources;
    private final List<Map<String, Object>> weakNodes;
    private final List<Map<String, Object>> recentRecords;
    private final List<Map<String, Object>> favoriteResources;
    private final List<Map<String, Object>> learningEvidence;
    private final Map<String, Object> studyIntent;

    private RecommendationContext(Builder builder) {
        this.studentId = builder.studentId;
        this.studentName = builder.studentName;
        this.subject = builder.subject;
        this.recordCount = builder.recordCount;
        this.recentRecordCount = builder.recentRecordCount;
        this.averageProgress = builder.averageProgress;
        this.resources = builder.resources;
        this.weakNodes = builder.weakNodes;
        this.recentRecords = builder.recentRecords;
        this.favoriteResources = builder.favoriteResources;
        this.learningEvidence = builder.learningEvidence;
        this.studyIntent = builder.studyIntent;
    }

    public Object getStudentId() {
        return studentId;
    }

    public List<Map<String, Object>> getLearningEvidence() { return learningEvidence; }
    public Map<String, Object> getStudyIntent() { return studyIntent; }

    public String getStudentName() {
        return studentName;
    }

    public String getSubject() {
        return subject;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public int getRecentRecordCount() {
        return recentRecordCount;
    }

    public int getAverageProgress() {
        return averageProgress;
    }

    public List<Map<String, Object>> getResources() {
        return resources;
    }

    public List<Map<String, Object>> getWeakNodes() {
        return weakNodes;
    }

    public List<Map<String, Object>> getRecentRecords() {
        return recentRecords;
    }

    public List<Map<String, Object>> getFavoriteResources() {
        return favoriteResources;
    }

    public static class Builder {
        private Object studentId;
        private String studentName;
        private String subject = "Java";
        private int recordCount;
        private int recentRecordCount;
        private int averageProgress;
        private List<Map<String, Object>> resources;
        private List<Map<String, Object>> weakNodes;
        private List<Map<String, Object>> recentRecords;
        private List<Map<String, Object>> favoriteResources;
        private List<Map<String, Object>> learningEvidence;
        private Map<String, Object> studyIntent = java.util.Collections.emptyMap();

        public Builder studyIntent(Map<String, Object> value) { this.studyIntent = value; return this; }

        public Builder learningEvidence(List<Map<String, Object>> value) { this.learningEvidence = value; return this; }

        public Builder studentId(Object studentId) {
            this.studentId = studentId;
            return this;
        }

        public Builder studentName(String studentName) {
            this.studentName = studentName;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder recordCount(int recordCount) {
            this.recordCount = recordCount;
            return this;
        }

        public Builder recentRecordCount(int recentRecordCount) {
            this.recentRecordCount = recentRecordCount;
            return this;
        }

        public Builder averageProgress(int averageProgress) {
            this.averageProgress = averageProgress;
            return this;
        }

        public Builder resources(List<Map<String, Object>> resources) {
            this.resources = resources;
            return this;
        }

        public Builder weakNodes(List<Map<String, Object>> weakNodes) {
            this.weakNodes = weakNodes;
            return this;
        }

        public Builder recentRecords(List<Map<String, Object>> recentRecords) {
            this.recentRecords = recentRecords;
            return this;
        }

        public Builder favoriteResources(List<Map<String, Object>> favoriteResources) {
            this.favoriteResources = favoriteResources;
            return this;
        }

        public RecommendationContext build() {
            return new RecommendationContext(this);
        }
    }
}
