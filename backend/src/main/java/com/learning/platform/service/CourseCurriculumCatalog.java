package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Authored topic content and explicit aliases. No fuzzy title matching or placeholder lessons. */
@Component
public class CourseCurriculumCatalog {
    private final JsonNode catalog;
    private final Map<String, JsonNode> profiles = new LinkedHashMap<>();
    private final Map<String, String> courseProfiles = new LinkedHashMap<>();

    public CourseCurriculumCatalog(ObjectMapper mapper) throws IOException {
        catalog = read(mapper, "course-curricula.json");
        catalog.path("profiles").fields().forEachRemaining(item -> profiles.put(item.getKey(), item.getValue()));
        read(mapper, "course-curricula-technical.json").path("profiles").fields()
            .forEachRemaining(item -> profiles.put(item.getKey(), item.getValue()));
        for (var course : read(mapper, "foundation-courses.json").path("courses"))
            courseProfiles.put(course.path("title").asText(), course.path("key").asText());
        catalog.path("legacyProfiles").fields()
            .forEachRemaining(item -> courseProfiles.put(item.getKey(), item.getValue().asText()));
    }

    private JsonNode read(ObjectMapper mapper, String file) throws IOException {
        try (var input = new ClassPathResource("catalog/" + file).getInputStream()) { return mapper.readTree(input); }
    }

    public String profile(String courseTitle) { return courseProfiles.get(courseTitle); }
    public JsonNode lessons(String profile) { return profiles.get(profile); }
    public JsonNode baseTopics(String profile, int index) { return catalog.path("baseTopics").path(profile).path(index); }
    public JsonNode baseTopics(String profile, String courseTitle, int index) {
        JsonNode legacy = catalog.path("legacyBaseTopics").path(courseTitle);
        return index == 0 && legacy.isArray() ? legacy : baseTopics(profile, index);
    }
    public String version() { return catalog.path("version").asText(); }

    public String reading(String courseTitle, JsonNode lesson) {
        return "# " + lesson.path(0).asText() + "\n\n学习目标：结合“" + courseTitle
            + "”，理解本节方法的适用条件，完成案例分析，并能解释边界情况。\n\n## 学习脉络\n流程图："
            + lesson.path(2).asText() + "\n\n## 核心讲解\n" + lesson.path(3).asText()
            + "\n\n## 案例推演\n" + lesson.path(4).asText()
            + "\n\n## 练习与思考\n基础：用自己的话解释“" + lesson.path(0).asText()
            + "”中的核心概念和适用条件。巩固：逐步复述本节案例，核对每一步的输入、处理与结果。进阶："
            + lesson.path(5).asText() + "\n\n## 参考与解析\n" + lesson.path(6).asText()
            + "\n\n完成建议：先独立写出结论与依据，再核对案例和解析；记录错误来自概念、条件还是执行步骤。";
    }
}
