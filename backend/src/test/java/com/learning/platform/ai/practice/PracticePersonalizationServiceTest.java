package com.learning.platform.ai.practice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PracticePersonalizationServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final ObjectMapper mapper = new ObjectMapper();
    final PracticePersonalizationService service = new PracticePersonalizationService(jdbc, mapper);
    final String text = "## 核心讲解\n事务用于保证一致性。索引可以减少查找扫描。先明确业务目标，再根据实际执行计划选择索引。\n## 从基础到进阶\n基础：事务保证什么？巩固：如何选择索引？进阶：如何分析并发边界？";

    Map<String, Object> source(long id, int progress) {
        return Map.of("lessonId", id, "lessonTitle", "课时" + id, "progressPercent", progress,
            "completed", progress == 100 ? 1 : 0, "contentText", text);
    }

    Map<String, Object> attempt(long source, boolean correct, String level, String scope) throws Exception {
        var question = new LinkedHashMap<String, Object>(Map.of("stem", "历史题" + UUID.randomUUID() + "？", "answer", 0, "reference", "事务用于保证一致性。"));
        if (scope.equals("smart")) question.put("sourceLessonId", source);
        return Map.of("lesson_id", source, "practice_scope", scope, "difficulty", level,
            "questions_json", mapper.writeValueAsString(List.of(question)), "answers_json", correct ? "[0]" : "[1]");
    }

    void data(long user, List<Map<String, Object>> sources, List<Map<String, Object>> history) {
        when(jdbc.queryForList(contains("a.content_text contentText"), eq(user), eq(12L))).thenReturn(sources);
        when(jdbc.queryForList(contains("order by id desc limit 30"), eq(user), eq(12L))).thenReturn(history);
    }

    @Test void coldStartIsUnassessedAndDoesNotSelectUnstudiedMaterial() {
        data(7, List.of(source(34, 100), source(35, 0)), List.of());
        var plan = service.plan(7, 12, null, true, null);
        assertEquals(34, plan.lessonId());
        assertNull(plan.summary().get("recentAccuracy"));
        assertEquals(List.of(), plan.summary().get("weakTopics"));
        assertEquals("基础", plan.difficulty());
        assertTrue(text.contains(plan.target().correctPhrase()));
        assertTrue(text.contains(plan.target().reference()));
        assertEquals(1L, plan.summary().get("studiedTextLessons"));
    }

    @Test void noStudyRequiresReadingButCurrentLessonCanBePreviewed() {
        data(7, List.of(source(34, 0)), List.of());
        assertFalse(service.plan(7, 12, null, true, null).eligible());
        assertTrue(service.plan(7, 12, 34L, false, null).eligible());
    }

    @Test void twoStudentsReceiveDifferentPrioritiesBasedOnTheirOwnAnswers() throws Exception {
        var sources = List.of(source(34, 100), source(35, 100));
        data(7, sources, List.of(attempt(34, false, "基础", "smart"), attempt(35, true, "基础", "lesson")));
        data(8, sources, List.of(attempt(35, false, "巩固", "smart"), attempt(34, true, "基础", "lesson")));
        assertEquals(34, service.plan(7, 12, null, true, null).lessonId());
        assertEquals(35, service.plan(8, 12, null, true, null).lessonId());
        verify(jdbc).queryForList(contains("where user_id=? and course_id=? and submitted_at is not null"), eq(7L), eq(12L));
        verify(jdbc).queryForList(contains("where user_id=? and course_id=? and submitted_at is not null"), eq(8L), eq(12L));
        verify(jdbc, atLeastOnce()).queryForList(argThat(sql -> sql.contains("ch.status='published'") && sql.contains("l.status='published'") && sql.contains("r.review_status='approved'")), eq(7L), eq(12L));
    }

    @Test void recentWrongAnswersOutweighOldHighScoresAndWrongReferenceIsIncluded() throws Exception {
        var history = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 4; i++) history.add(attempt(34, false, "进阶", "smart"));
        for (int i = 0; i < 8; i++) history.add(attempt(34, true, "基础", "lesson"));
        data(7, List.of(source(34, 100), source(35, 100)), history);
        var plan = service.plan(7, 12, null, true, null);
        assertEquals(0, plan.summary().get("recentAccuracy"));
        assertEquals(4, plan.summary().get("recentQuestionCount"));
        assertEquals("基础", plan.difficulty());
        assertTrue(plan.evidence().contains("事务用于保证一致性。"));
    }

    @Test void advancementRequiresStableAnswersAtEachLevelAndEnoughStudy() throws Exception {
        var history = List.of(attempt(34, true, "巩固", "smart"), attempt(34, true, "巩固", "smart"),
            attempt(34, true, "基础", "lesson"), attempt(34, true, "基础", "lesson"));
        data(7, List.of(source(34, 100)), history);
        assertEquals("进阶", service.plan(7, 12, null, true, null).difficulty());
        data(7, List.of(source(34, 70)), history);
        assertEquals("巩固", service.plan(7, 12, null, true, null).difficulty());
        data(7, List.of(source(34, 20)), history);
        assertEquals("基础", service.plan(7, 12, null, true, null).difficulty());
        data(7, List.of(source(34, 100)), List.of(attempt(34, true, "基础", "lesson")));
        assertEquals("基础", service.plan(7, 12, null, true, null).difficulty());
    }

    @Test void malformedUnansweredAndAmbiguousOldCourseResultsAreNotMistakenForWeakness() throws Exception {
        var malformed = Map.<String, Object>of("questions_json", "not-json", "answers_json", "[]");
        var unanswered = new HashMap<>(attempt(34, false, "基础", "lesson"));
        unanswered.put("answers_json", "[]");
        var invalid = new HashMap<>(attempt(34, false, "基础", "lesson"));
        invalid.put("answers_json", "[9]");
        data(7, List.of(source(34, 100), source(35, 100)), List.of(malformed, unanswered, invalid, attempt(34, false, "基础", "course")));
        var plan = service.plan(7, 12, null, true, null);
        assertNull(plan.summary().get("recentAccuracy"));
        assertEquals(List.of(), plan.summary().get("weakTopics"));
    }

    @Test void metadataUsesVerifiedMaterialAndDoesNotIncludeHistoricalAnswers() throws Exception {
        data(7, List.of(source(34, 100)), List.of(attempt(34, false, "基础", "smart")));
        when(jdbc.queryForList(contains("kt.topic_title topic"), eq(12L))).thenReturn(List.of(Map.of("lessonId", 34L, "topic", "事务一致性")));
        var plan = service.plan(7, 12, null, true, null);
        var tagged = plan.annotate(List.of(Map.of("reference", "事务用于保证一致性。", "answer", 0)));
        assertEquals(34L, tagged.get(0).get("sourceLessonId"));
        assertEquals(List.of("事务一致性"), tagged.get(0).get("knowledgePoints"));
        String summary = mapper.writeValueAsString(plan.summary());
        assertFalse(summary.contains("reference"));
        assertFalse(summary.contains("answers_json"));
        assertFalse(summary.contains("历史题"));
        assertThrows(BusinessException.class, () -> plan.annotate(List.of(Map.of("reference", "不属于本课程的资料。"))));
    }

    @Test void coreEvidenceSurvivesLongIntroAndRecentExactQuestionIsRejected() throws Exception {
        var source = new HashMap<>(source(34, 100));
        source.put("contentText", "欢迎阅读本节内容。".repeat(500) + "\n" + text);
        var history = attempt(34, false, "基础", "smart");
        data(7, List.of(source), List.of(history));
        var plan = service.plan(7, 12, null, true, null);
        assertTrue(plan.evidence().contains("事务用于保证一致性。"));
        String oldStem = mapper.readTree((String) history.get("questions_json")).get(0).path("stem").asText();
        assertThrows(BusinessException.class, () -> plan.requireFresh(List.of(Map.of("stem", oldStem))));
        assertDoesNotThrow(() -> plan.requireFresh(List.of(Map.of("stem", "新的应用情境如何分析？"))));
    }

    @Test void advancedAnswerTargetIsKeptInEvidenceEvenWhenTheCoreSectionFillsTheBudget() {
        var source = new HashMap<>(source(34, 100));
        source.put("contentText", "## 核心讲解\n" + "需要详细核对每一步输入与处理过程。".repeat(200)
            + "\n## 参考与解析\n为每门课程创建独立进度对象，并通过受控方法修改状态。\n## 练习与思考\n进阶：如何独立维护两门课程的状态？");
        data(7, List.of(source), List.of());
        var plan = service.plan(7, 12, null, false, "进阶");
        assertEquals("为每门课程创建独立进度对象", plan.target().correctPhrase());
        assertTrue(plan.evidence().startsWith("【课时34】\n为每门课程创建独立进度对象"));
        assertTrue(plan.evidence().contains(plan.target().reference()));
    }

    @Test void consolidationUsesTheActualCaseConditionAndItsSupportedConclusion() {
        var source = new HashMap<>(source(34, 100));
        source.put("contentText", text + "\n## 案例推演\n创建 Course 对象后令 b=a，两个引用可能指向同一对象；修改 b 的状态，也可能通过 a 观察到该变化。");
        data(7, List.of(source), List.of());
        var plan = service.plan(7, 12, null, false, "巩固");
        assertEquals("创建 Course 对象后令 b=a时，两个引用可能指向什么？", plan.target().question());
        assertEquals("同一对象", plan.target().correctPhrase());
        assertTrue(plan.evidence().contains(plan.target().reference()));
    }
}
