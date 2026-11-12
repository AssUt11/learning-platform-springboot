package com.learning.platform.ai.practice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CourseContentValidationService;
import com.learning.platform.vo.CourseContentValidationResult;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PracticeServiceTest {

    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AiModelProvider provider = mock(AiModelProvider.class);
    final CourseContentValidationService validation = mock(CourseContentValidationService.class);
    final AiProperties properties = new AiProperties();
    final PracticeService service = new PracticeService(
        jdbc,
        new ObjectMapper(),
        provider,
        properties,
        new AiUserRateLimiter(properties),
        validation,
        new PracticePersonalizationService(jdbc, new ObjectMapper())
    );
    Map<String, Object> row;

    @BeforeEach
    void setup() {
        CourseContentValidationResult complete = mock(CourseContentValidationResult.class);
        when(complete.isContentComplete()).thenReturn(true);
        when(validation.validatePublic(12L)).thenReturn(complete);
        when(jdbc.queryForList(contains("select l.title"), eq(12L), eq(34L))).thenReturn(
            List.of(Map.of("title", "事务"))
        );
        row = new HashMap<>();
        row.put("quiz_id", "quiz-a");
        row.put("course_id", 12L);
        row.put("lesson_id", 34L);
        row.put(
            "questions_json",
            new ObjectMapper()
                .convertValue(
                    PracticeQuestionParser.parse(
                        PracticeQuestionParserTest.QUESTIONS,
                        new ObjectMapper()
                    ),
                    com.fasterxml.jackson.databind.JsonNode.class
                )
                .toString()
        );
        row.put("difficulty", "基础");
        when(
            jdbc.queryForList(
                startsWith("select * from ai_practice_sessions where quiz_id=?"),
                eq("quiz-a"),
                eq(7L)
            )
        ).thenAnswer((call) -> List.of(row));
    }

    @Test
    void unansweredQuizNeverLeaksCorrectAnswersOrExplanations() {
        when(
            jdbc.queryForList(contains("order by id desc limit 1"), eq(7L), eq(12L), eq(34L))
        ).thenReturn(List.of(row));
        Map<String, Object> result = service.latest(7L, 12L, 34L);
        Map<?, ?> question = (Map<?, ?>) ((List<?>) result.get("questions")).get(0);
        assertFalse(question.containsKey("answer"));
        assertFalse(question.containsKey("explanation"));
        assertFalse(question.containsKey("reference"));
    }

    @Test
    void gradesOnServerAndPersistsBeforeReturningExplanations() {
        when(
            jdbc.update(
                startsWith("update ai_practice_sessions"),
                any(),
                any(),
                eq("quiz-a"),
                eq(7L)
            )
        ).thenAnswer((call) -> {
            row.put("score", call.getArgument(2));
            row.put("answers_json", call.getArgument(1));
            row.put("submitted_at", Timestamp.valueOf("2026-10-20 12:00:00"));
            return 1;
        });
        // JdbcTemplate varargs argument indices are SQL, answers_json, score, quiz_id, user_id.
        Map<String, Object> result = service.submit(7L, "quiz-a", List.of(0, 2));
        assertEquals(50, result.get("score"));
        assertEquals(List.of(0, 2), result.get("answers"));
        assertEquals(0, ((Map<?, ?>) ((List<?>) result.get("questions")).get(0)).get("answer"));
    }

    @Test
    void repeatedSubmissionCannotRewriteAnExistingScore() {
        row.put("submitted_at", Timestamp.valueOf("2026-10-20 12:00:00"));
        row.put("score", 0);
        row.put("answers_json", "[3,3]");
        assertEquals(0, service.submit(7L, "quiz-a", List.of(0, 1)).get("score"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void anotherUserCannotSubmitOrReadTheQuizById() {
        assertEquals(
            404,
            assertThrows(BusinessException.class, () ->
                service.submit(9L, "quiz-a", List.of(0, 1))
            ).getCode()
        );
    }

    @Test
    void incompleteAnswersAreRejectedWithoutWriting() {
        assertEquals(
            400,
            assertThrows(BusinessException.class, () ->
                service.submit(7L, "quiz-a", List.of(0))
            ).getCode()
        );
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void missingLessonTextDoesNotCallModelOrInventQuestions() {
        properties.setEnabled(true);
        assertEquals(
            409,
            assertThrows(BusinessException.class, () -> service.generate(7L, 12L, 34L)).getCode()
        );
        verifyNoInteractions(provider);
    }

    @Test
    void invalidModelOutputGetsOneRepairBeforeSavingAValidatedQuiz() throws Exception {
        properties.setEnabled(true);
        when(jdbc.queryForList(contains("select content_text"), eq(34L))).thenReturn(
            List.of(Map.of("content_text", "保证一致性。确认修改。".repeat(8)))
        );
        ObjectMapper mapper = new ObjectMapper();
        when(provider.chatTurn(anyList(), anyList(), eq(true), any())).thenReturn(
            mapper.readTree("{\"message\":{\"content\":\"invalid\"}}"),
            mapper
                .createObjectNode()
                .set(
                    "message",
                    mapper.createObjectNode().put("content", PracticeQuestionParserTest.QUESTIONS)
                )
        );
        when(
            jdbc.queryForList(
                startsWith("select * from ai_practice_sessions where quiz_id=?"),
                anyString(),
                eq(7L)
            )
        ).thenReturn(List.of(row));
        service.generate(7L, 12L, 34L);
        verify(provider, times(2)).chatTurn(anyList(), anyList(), eq(true), any());
        verify(jdbc, times(1)).update(
            startsWith("insert into ai_practice_sessions"),
            any(),
            eq(7L),
            eq(12L),
            eq(34L),
            eq("基础"),
            any(),
            any(),
            any()
        );
    }

    private void courseState(int completed, int pendingVideos) {
        when(jdbc.queryForObject(contains("select count(*) from resources"), eq(Integer.class), eq(12L))).thenReturn(1);
        when(jdbc.queryForObject(contains("catalog_learning_upgrades"), eq(Integer.class), eq(12L))).thenReturn(pendingVideos);
        when(jdbc.queryForList(contains("select l.id lessonId"), eq(7L), eq(12L))).thenReturn(List.of(
            Map.of("lessonId", 34L, "title", "事务", "completed", completed >= 1 ? 1 : 0),
            Map.of("lessonId", 35L, "title", "索引", "completed", completed >= 2 ? 1 : 0)
        ));
    }

    @Test
    void fullCourseQuizCannotBeRequestedBeforeEveryLessonIsComplete() {
        courseState(1, 0);
        properties.setEnabled(true);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.generateAssessment(7L, 12L, null)).getCode());
        verifyNoInteractions(provider);
    }

    @Test
    void missingPlannedRecordingsKeepCourseAssessmentLocked() {
        courseState(2, 1);
        Map<String, Object> state = service.courseAssessment(7L, 12L);
        assertEquals(false, state.get("unlocked"));
        assertEquals(1, state.get("pendingVideos"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.generateAssessment(7L, 12L, null)).getCode());
        verifyNoInteractions(provider);
    }

    @Test
    void lessonCompletionUnlocksOnlyFoundationAndDoesNotAllowSkippingStages() {
        courseState(2, 0);
        Map<String, Object> state = service.courseAssessment(7L, 12L);
        assertEquals(true, state.get("unlocked"));
        assertEquals("基础", state.get("nextDifficulty"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.generateAssessment(7L, 12L, "进阶")).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.generateAssessment(7L, 12L, "困难")).getCode());
        verifyNoInteractions(provider);
    }

    @Test
    void progressionUsesBestSavedScoreAndRequiresEightyAtEachStage() {
        courseState(2, 0);
        when(jdbc.queryForList(contains("select difficulty,max(score)"), eq(7L), eq(12L))).thenReturn(List.of(
            Map.of("difficulty", "基础", "bestScore", 80, "attempts", 2),
            Map.of("difficulty", "巩固", "bestScore", 50, "attempts", 1)
        ));
        Map<String, Object> state = service.courseAssessment(7L, 12L);
        assertEquals("巩固", state.get("nextDifficulty"));
        List<?> stages = (List<?>) state.get("stages");
        assertEquals(true, ((Map<?, ?>) stages.get(1)).get("unlocked"));
        assertEquals(false, ((Map<?, ?>) stages.get(2)).get("unlocked"));
    }

    @Test
    void anUnsubmittedCourseQuizIsRestoredWithoutSpendingAnotherModelRequest() {
        courseState(2, 0);
        row.put("practice_scope", "course");
        when(jdbc.queryForList(contains("practice_scope='course' order by"), eq(7L), eq(12L))).thenReturn(List.of(row));
        Map<String, Object> quiz = service.generateAssessment(7L, 12L, null);
        assertEquals("quiz-a", quiz.get("quizId"));
        assertFalse(((Map<?, ?>) ((List<?>) quiz.get("questions")).get(0)).containsKey("answer"));
        verifyNoInteractions(provider);
    }

    @Test
    void courseGenerationUsesLaterLessonsAsWellAsTheFirstAndPersistsCourseScope() throws Exception {
        courseState(2, 0);
        properties.setEnabled(true);
        when(jdbc.queryForList(contains("select l.id lessonId,l.title,a.content_text"), eq(12L))).thenReturn(List.of(
            Map.of("lessonId", 34L, "title", "事务", "contentText", "保证一致性。".repeat(12)),
            Map.of("lessonId", 35L, "title", "后续索引课时", "contentText", "确认修改。".repeat(12))
        ));
        ObjectMapper mapper = new ObjectMapper();
        when(provider.chatTurn(anyList(), anyList(), eq(true), any())).thenAnswer(call -> {
            List<Map<String, Object>> messages = call.getArgument(0);
            assertTrue(String.valueOf(messages.get(1).get("content")).contains("后续索引课时"));
            assertTrue(String.valueOf(messages.get(1).get("content")).contains("整门课程"));
            return mapper.createObjectNode().set("message", mapper.createObjectNode().put("content", PracticeQuestionParserTest.QUESTIONS));
        });
        when(jdbc.queryForList(startsWith("select * from ai_practice_sessions where quiz_id=?"), anyString(), eq(7L))).thenReturn(List.of(row));
        service.generateAssessment(7L, 12L, null);
        verify(jdbc).update(contains("practice_scope) values"), any(), eq(7L), eq(12L), eq(34L), eq("基础"), any(), any(), any());
    }

    @Test
    void addingRequiredLessonsAlsoBlocksSubmissionOfAnOlderCourseQuiz() {
        courseState(1, 0);
        row.put("practice_scope", "course");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.submit(7L, "quiz-a", List.of(0, 1))).getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void smartPracticeWithoutStudyDoesNotCallModelOrCreateSession() {
        courseState(0, 0);
        when(jdbc.queryForList(contains("a.content_text contentText"), eq(7L), eq(12L))).thenReturn(List.of(
            Map.of("lessonId", 34L, "lessonTitle", "事务", "progressPercent", 0, "completed", 0,
                "contentText", "事务用于保证一致性。提交表示确认修改。".repeat(4))));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.generateSmart(7L, 12L)).getCode());
        verifyNoInteractions(provider);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void smartPendingQuizIsResumedWithoutModelAndWithoutLeakingAnswers() {
        courseState(0, 0);
        row.put("practice_scope", "smart");
        when(jdbc.queryForList(contains("practice_scope='smart' order by"), eq(7L), eq(12L))).thenReturn(List.of(row));
        var quiz = service.generateSmart(7L, 12L);
        assertEquals("quiz-a", quiz.get("quizId"));
        assertFalse(((Map<?, ?>) ((List<?>) quiz.get("questions")).get(0)).containsKey("answer"));
        verifyNoInteractions(provider);
    }

    @Test
    void smartGenerationPersistsActualSourceAndBasisButKeepsAnswersPrivate() throws Exception {
        courseState(0, 0);
        properties.setEnabled(true);
        when(jdbc.queryForList(contains("a.content_text contentText"), eq(7L), eq(12L))).thenReturn(List.of(
            Map.of("lessonId", 34L, "lessonTitle", "事务", "progressPercent", 70, "completed", 0,
                "contentText", "事务用于保证一致性。提交表示确认修改。".repeat(4))));
        ObjectMapper mapper = new ObjectMapper();
        when(provider.chatTurn(anyList(), anyList(), eq(true), any())).thenAnswer(call -> {
            List<Map<String, Object>> messages = call.getArgument(0);
            assertTrue(String.valueOf(messages.get(1).get("content")).contains("尚无有效答题记录"));
            return mapper.createObjectNode().set("message", mapper.createObjectNode().put("content", PracticeQuestionParserTest.QUESTIONS));
        });
        when(jdbc.update(contains("'smart')"), any(), eq(7L), eq(12L), eq(34L), eq("基础"), any(), any(), any())).thenAnswer(call -> {
            row.put("questions_json", call.getArgument(6));
            row.put("practice_scope", "smart");
            return 1;
        });
        when(jdbc.queryForList(startsWith("select * from ai_practice_sessions where quiz_id=?"), anyString(), eq(7L))).thenReturn(List.of(row));
        var quiz = service.generateSmart(7L, 12L);
        var question = (Map<?, ?>) ((List<?>) quiz.get("questions")).get(0);
        assertEquals(34, question.get("sourceLessonId"));
        assertEquals("事务", question.get("sourceLessonTitle"));
        assertTrue(quiz.containsKey("personalization"));
        assertFalse(question.containsKey("answer"));
        assertFalse(question.containsKey("reference"));
        var stored = mapper.readTree(String.valueOf(row.get("questions_json")));
        assertEquals(0, stored.get(0).path("answer").asInt());
        assertEquals("事务用于保证一致性。", stored.get(0).path("reference").asText());
    }
}
