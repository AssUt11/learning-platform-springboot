package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LearningStagesTest {
    @Test void assignsExistingCoursesAcrossThreeStages() {
        assertEquals("foundation",LearningStages.infer("Java 基础语法","入门"));
        assertEquals("intermediate",LearningStages.infer("JDBC 与数据库事务","入门"));
        assertEquals("advanced",LearningStages.infer("多线程并发与性能调优","入门"));
        assertEquals("advanced",LearningStages.infer("完整应用开发","项目实战"));
    }
    @Test void explicitTeacherChoiceOverridesTheInferredStage() {
        Map<String,Object> course=new LinkedHashMap<>(Map.of("id",7L,"title","Spring Boot 入门","level","入门"));
        LearningStageService.enrich(course,Map.of(7L,"foundation"));
        assertEquals("foundation",course.get("learningStage"));
        assertEquals("基础课程",course.get("stageLabel"));
    }
    @Test void invalidStagesAreRejectedRatherThanStored() {
        assertThrows(BusinessException.class,()->LearningStages.require("expert"));
    }
}
