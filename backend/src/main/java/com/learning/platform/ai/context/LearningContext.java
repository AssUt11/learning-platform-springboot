package com.learning.platform.ai.context;

public class LearningContext {
    private final long publicCourseId;
    private final long lessonId;
    private final String courseTitle;
    private final String lessonTitle;
    private final String subject;
    private final String promptContext;
    private final boolean neo4jAvailable;
    private final int graphNodeCount;

    public LearningContext(long publicCourseId, long lessonId, String courseTitle, String lessonTitle,
                           String subject, String promptContext, boolean neo4jAvailable, int graphNodeCount) {
        this.publicCourseId = publicCourseId;
        this.lessonId = lessonId;
        this.courseTitle = courseTitle;
        this.lessonTitle = lessonTitle;
        this.subject = subject;
        this.promptContext = promptContext;
        this.neo4jAvailable = neo4jAvailable;
        this.graphNodeCount = graphNodeCount;
    }

    public long getPublicCourseId() { return publicCourseId; }
    public long getLessonId() { return lessonId; }
    public String getCourseTitle() { return courseTitle; }
    public String getLessonTitle() { return lessonTitle; }
    public String getSubject() { return subject; }
    public String getPromptContext() { return promptContext; }
    public boolean isNeo4jAvailable() { return neo4jAvailable; }
    public int getGraphNodeCount() { return graphNodeCount; }
}
