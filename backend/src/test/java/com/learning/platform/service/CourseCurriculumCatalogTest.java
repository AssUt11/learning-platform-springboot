package com.learning.platform.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CourseCurriculumCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CourseCurriculumCatalog catalog = new CourseCurriculumCatalog(mapper);

    CourseCurriculumCatalogTest() throws Exception {}

    @Test
    void allThirtyFoundationAndElevenLegacyCoursesHaveFourAuthoredAdditions() throws Exception {
        var pack = mapper.readTree(new ClassPathResource("catalog/foundation-courses.json").getInputStream());
        int checked = 0;
        for (var course : pack.path("courses")) {
            verifyCourse(course.path("title").asText());
            checked++;
        }
        var core = mapper.readTree(new ClassPathResource("catalog/course-curricula.json").getInputStream());
        var names = core.path("legacyProfiles").fieldNames();
        while (names.hasNext()) { verifyCourse(names.next()); checked++; }
        assertEquals(41, checked);
    }

    private void verifyCourse(String title) {
        String profile = catalog.profile(title);
        assertNotNull(profile, title);
        var lessons = catalog.lessons(profile);
        assertNotNull(lessons, profile);
        assertEquals(4, lessons.size(), profile);
        var titles = new HashSet<String>();
        for (var lesson : lessons) {
            assertEquals(7, lesson.size(), lesson.toString());
            for (var field : lesson) assertFalse(field.asText().isBlank());
            assertTrue(titles.add(lesson.path(0).asText()));
            String reading = catalog.reading(title, lesson);
            assertTrue(reading.length() >= 350, title + ": " + lesson.path(0).asText() + " length=" + reading.length());
            assertTrue(reading.contains("案例推演"));
            assertTrue(reading.contains("进阶："));
            assertTrue(reading.contains(lesson.path(6).asText()));
        }
        assertTrue(catalog.baseTopics(profile, 0).isArray(), profile);
    }

    @Test
    void unknownTeacherCoursesAreNotGivenAnUnrelatedCurriculum() {
        assertNull(catalog.profile("教师自定义的陌生课程"));
    }
}
