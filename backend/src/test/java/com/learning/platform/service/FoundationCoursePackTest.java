package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class FoundationCoursePackTest {
    @Test void everyCourseHasDistinctContentAndAnsweredExercises() throws Exception {
        var pack=new ObjectMapper().readTree(new ClassPathResource("catalog/foundation-courses.json").getInputStream());
        assertEquals(30,pack.path("courses").size());
        var keys=new HashSet<String>(); var titles=new HashSet<String>(); var content=new HashSet<String>();
        int postgraduate=0, lessons=0;
        for(var course:pack.path("courses")) {
            assertTrue(keys.add(course.path("key").asText())); assertTrue(titles.add(course.path("title").asText()));
            assertFalse(course.path("subject").asText().isBlank()); assertFalse(course.path("description").asText().isBlank());
            if("考研备考".equals(course.path("category").asText())) postgraduate++;
            assertEquals(2,course.path("lessons").size());
            for(var lesson:course.path("lessons")) {
                String text=lesson.path("content").asText();
                assertTrue(text.length()>=200,lesson.path("title").asText()); assertTrue(content.add(text));
                assertTrue(text.contains("参考")); assertTrue(text.contains("例题")||text.contains("示例")||text.contains("例句")||text.contains("例段")||text.contains("案例")||text.contains("例：")||text.contains("短段"));
                assertTrue(lesson.path("minutes").asInt()>0); lessons++;
            }
        }
        assertEquals(10,postgraduate); assertEquals(60,lessons);
    }
}
