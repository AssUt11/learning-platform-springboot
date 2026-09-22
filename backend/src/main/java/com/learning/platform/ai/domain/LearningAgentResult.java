package com.learning.platform.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LearningAgentResult {
    private final String requestId;
    private final String content;
    private final List<String> toolsUsed;
    private final List<String> sources;
    private final boolean graphUsed;
    private final Map<String, Object> learningPath;

    public LearningAgentResult(String requestId, String content, List<String> toolsUsed,
                               List<String> sources, boolean graphUsed) {
        this(requestId, content, toolsUsed, sources, graphUsed, Collections.emptyMap());
    }

    public LearningAgentResult(String requestId, String content, List<String> toolsUsed,
                               List<String> sources, boolean graphUsed, Map<String, Object> learningPath) {
        this.requestId = requestId;
        this.content = content;
        this.toolsUsed = Collections.unmodifiableList(new ArrayList<String>(toolsUsed));
        this.sources = Collections.unmodifiableList(new ArrayList<String>(sources));
        this.graphUsed = graphUsed;
        this.learningPath = learningPath;
    }

    public String getRequestId() { return requestId; }
    public String getContent() { return content; }
    public List<String> getToolsUsed() { return toolsUsed; }
    public List<String> getSources() { return sources; }
    public boolean isGraphUsed() { return graphUsed; }
    public Map<String, Object> getLearningPath() { return learningPath; }
}
