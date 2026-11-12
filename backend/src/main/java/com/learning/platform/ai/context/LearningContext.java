package com.learning.platform.ai.context;

import java.util.Collections;
import java.util.List;

public class LearningContext {

    private final long publicCourseId;
    private final long lessonId;
    private final String courseTitle;
    private final String lessonTitle;
    private final String subject;
    private final String promptContext;
    private final boolean neo4jAvailable;
    private final int graphNodeCount;
    private final List<CoursewareSnippet> snippets;

    public LearningContext(
        long publicCourseId,
        long lessonId,
        String courseTitle,
        String lessonTitle,
        String subject,
        String promptContext,
        boolean neo4jAvailable,
        int graphNodeCount
    ) {
        this(publicCourseId, lessonId, courseTitle, lessonTitle, subject, promptContext,
            neo4jAvailable, graphNodeCount, Collections.emptyList());
    }

    public LearningContext(long publicCourseId, long lessonId, String courseTitle,
        String lessonTitle, String subject, String promptContext, boolean neo4jAvailable,
        int graphNodeCount, List<CoursewareSnippet> snippets) {
        this.publicCourseId = publicCourseId;
        this.lessonId = lessonId;
        this.courseTitle = courseTitle;
        this.lessonTitle = lessonTitle;
        this.subject = subject;
        this.promptContext = promptContext;
        this.neo4jAvailable = neo4jAvailable;
        this.graphNodeCount = graphNodeCount;
        this.snippets = snippets == null ? Collections.emptyList() : List.copyOf(snippets);
    }

    public long getPublicCourseId() {
        return publicCourseId;
    }

    public long getLessonId() {
        return lessonId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public String getLessonTitle() {
        return lessonTitle;
    }

    public String getSubject() {
        return subject;
    }

    public String getPromptContext() {
        return promptContext;
    }

    public boolean isNeo4jAvailable() {
        return neo4jAvailable;
    }

    public int getGraphNodeCount() {
        return graphNodeCount;
    }

    public List<CoursewareSnippet> getSnippets() {
        return snippets;
    }
}
