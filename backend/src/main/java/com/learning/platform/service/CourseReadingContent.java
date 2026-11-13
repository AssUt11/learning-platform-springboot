package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Original course-specific explanations, diagrams and progressively harder worked exercises. */
@Component
public class CourseReadingContent {

    private final JsonNode expansions;

    public CourseReadingContent(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("catalog/lesson-expansions.json").getInputStream()) {
            expansions = mapper.readTree(input);
        }
    }

    public boolean supports(String title) {
        return expansions.has(title);
    }

    public String expand(String title, String original) {
        if (!supports(title)) return original;
        JsonNode item = expansions.get(title);
        StringBuilder flow = new StringBuilder();
        for (JsonNode step : item.path("flow")) {
            if (!flow.isEmpty()) flow.append(" → ");
            flow.append(step.asText());
        }
        String extension = "## 学习脉络\n流程图：" + flow + "\n\n## 深入讲解\n" + item.path("explanation").asText()
            + "\n\n## 分步案例\n" + item.path("example").asText()
            + "\n\n## 从基础到进阶\n" + item.path("challenge").asText()
            + "\n\n## 参考与解析\n" + item.path("answer").asText();
        if (original != null && original.contains(extension)) return original;
        return (original == null || original.isBlank() ? "# " + title : original.strip()) + "\n\n" + extension;
    }
}
