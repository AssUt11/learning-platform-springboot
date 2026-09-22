package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.pattern.RecommendationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LearningAgentToolbox {
    private final Neo4jLearningContextService graphService;

    public LearningAgentToolbox(Neo4jLearningContextService graphService) {
        this.graphService = graphService;
    }

    public List<Map<String, Object>> definitions(String type) {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
        tools.add(tool("get_learning_profile", "读取当前登录用户的学习概况，不需要参数。",
                Collections.emptyMap(), Collections.emptyList()));
        tools.add(tool("get_weak_knowledge", "读取当前登录用户的薄弱知识点，不需要参数。",
                Collections.emptyMap(), Collections.emptyList()));
        if ("recommend".equals(type) || "analysis".equals(type) || "path".equals(type)) {
            tools.add(tool("get_learning_resources", "读取当前用户可访问且已审核的候选学习资源，不需要参数。",
                    Collections.emptyMap(), Collections.emptyList()));
        }
        if ("path".equals(type)) {
            Map<String, Object> properties = new LinkedHashMap<String, Object>();
            properties.put("topic", Map.of("type", "string", "description", "需要规划的知识主题"));
            tools.add(tool("get_graph_prerequisites", "从 Neo4j 查询某个主题的明确先修知识关系。",
                    properties, Collections.singletonList("topic")));
        }
        return tools;
    }

    public List<String> requiredTools(String type) {
        if ("guide".equals(type) || "analysis".equals(type)) {
            return Collections.singletonList("get_learning_profile");
        }
        if ("recommend".equals(type)) {
            return Collections.singletonList("get_learning_resources");
        }
        if ("path".equals(type)) {
            return java.util.Arrays.asList("get_learning_profile", "get_learning_resources", "get_graph_prerequisites");
        }
        return Collections.emptyList();
    }

    public ToolResult execute(String name, JsonNode arguments, RecommendationContext context) {
        if ("get_learning_profile".equals(name)) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("subject", context.getSubject());
            data.put("recordCount", context.getRecordCount());
            data.put("recentSevenDayRecordCount", context.getRecentRecordCount());
            data.put("averageProgress", context.getAverageProgress());
            data.put("recentRecords", limited(context.getRecentRecords(), 5));
            return new ToolResult(data, Collections.singletonList("learning_records"), false);
        }
        if ("get_weak_knowledge".equals(name)) {
            return new ToolResult(Map.of("weakNodes", limited(context.getWeakNodes(), 5)),
                    Collections.singletonList("learning_records:weak_nodes"), false);
        }
        if ("get_learning_resources".equals(name)) {
            List<Map<String, Object>> resources = limited(context.getResources(), 8);
            Set<String> sources = new LinkedHashSet<String>();
            for (Map<String, Object> resource : resources) {
                Object id = resource.get("id");
                if (id != null) sources.add("resource:" + id);
            }
            return new ToolResult(Map.of("resources", resources), new ArrayList<String>(sources), false);
        }
        if ("get_graph_prerequisites".equals(name)) {
            String topic = arguments == null ? "" : arguments.path("topic").asText("").trim();
            if (topic.isEmpty() || topic.length() > 120) {
                return ToolResult.error("topic 参数不能为空且不能超过 120 个字符");
            }
            Neo4jLearningContextService.PrerequisiteContext graph = graphService.findPrerequisites(topic);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("available", graph.isAvailable());
            data.put("topicFound", graph.isTopicFound());
            data.put("topic", graph.getTopic());
            data.put("prerequisites", graph.getPrerequisites());
            if (graph.getReason() != null) data.put("reason", graph.getReason());
            boolean evidenceUsed = graph.isAvailable() && graph.isTopicFound()
                    && graph.getPrerequisites() != null && !graph.getPrerequisites().isEmpty();
            List<String> sources = evidenceUsed
                    ? Collections.singletonList("neo4j:knowledge_graph") : Collections.emptyList();
            return new ToolResult(data, sources, evidenceUsed);
        }
        return ToolResult.error("未知工具：" + name);
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties,
                                     List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        return Map.of("type", "function", "function", function);
    }

    private List<Map<String, Object>> limited(List<Map<String, Object>> rows, int limit) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < rows.size() && index < limit; index++) {
            Map<String, Object> row = rows.get(index);
            if (row != null) result.add(new LinkedHashMap<String, Object>(row));
        }
        return result;
    }

    public static class ToolResult {
        private final Map<String, Object> data;
        private final List<String> sources;
        private final boolean graphUsed;

        public ToolResult(Map<String, Object> data, List<String> sources, boolean graphUsed) {
            this.data = data;
            this.sources = sources;
            this.graphUsed = graphUsed;
        }

        public static ToolResult error(String message) {
            return new ToolResult(Map.of("error", message), Collections.emptyList(), false);
        }

        public Map<String, Object> getData() { return data; }
        public List<String> getSources() { return sources; }
        public boolean isGraphUsed() { return graphUsed; }
    }
}
