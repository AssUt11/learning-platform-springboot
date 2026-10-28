package com.learning.platform.ai.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CourseContentValidationService;
import com.learning.platform.vo.CourseContentValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AiModelProvider provider = mock(AiModelProvider.class);
    final CourseContentValidationService validation = mock(CourseContentValidationService.class);
    final AiProperties properties = new AiProperties();
    final PracticeService service = new PracticeService(jdbc,new ObjectMapper(),provider,properties,new AiUserRateLimiter(properties),validation);
    Map<String,Object> row;
    @BeforeEach void setup() {
        CourseContentValidationResult complete = mock(CourseContentValidationResult.class);
        when(complete.isContentComplete()).thenReturn(true); when(validation.validatePublic(12L)).thenReturn(complete);
        when(jdbc.queryForList(contains("select l.title"),eq(12L),eq(34L))).thenReturn(List.of(Map.of("title","事务")));
        row = new HashMap<>(); row.put("quiz_id","quiz-a"); row.put("course_id",12L); row.put("lesson_id",34L);
        row.put("questions_json",new ObjectMapper().convertValue(PracticeQuestionParser.parse(PracticeQuestionParserTest.QUESTIONS,new ObjectMapper()),com.fasterxml.jackson.databind.JsonNode.class).toString()); row.put("difficulty","基础");
        when(jdbc.queryForList(startsWith("select * from ai_practice_sessions where quiz_id=?"),eq("quiz-a"),eq(7L))).thenAnswer(call->List.of(row));
    }
    @Test void unansweredQuizNeverLeaksCorrectAnswersOrExplanations() {
        when(jdbc.queryForList(contains("order by id desc limit 1"),eq(7L),eq(12L),eq(34L))).thenReturn(List.of(row));
        Map<String,Object> result = service.latest(7L,12L,34L);
        Map<?,?> question = (Map<?,?>)((List<?>)result.get("questions")).get(0);
        assertFalse(question.containsKey("answer")); assertFalse(question.containsKey("explanation"));
        assertFalse(question.containsKey("reference"));
    }
    @Test void gradesOnServerAndPersistsBeforeReturningExplanations() {
        when(jdbc.update(startsWith("update ai_practice_sessions"),any(),any(),eq("quiz-a"),eq(7L))).thenAnswer(call->{
            row.put("score",call.getArgument(2)); row.put("answers_json",call.getArgument(1));
            row.put("submitted_at",Timestamp.valueOf("2026-10-20 12:00:00")); return 1;
        });
        // JdbcTemplate varargs argument indices are SQL, answers_json, score, quiz_id, user_id.
        Map<String,Object> result = service.submit(7L,"quiz-a",List.of(0,2));
        assertEquals(50,result.get("score")); assertEquals(List.of(0,2),result.get("answers"));
        assertEquals(0,((Map<?,?>)((List<?>)result.get("questions")).get(0)).get("answer"));
    }
    @Test void repeatedSubmissionCannotRewriteAnExistingScore() {
        row.put("submitted_at",Timestamp.valueOf("2026-10-20 12:00:00")); row.put("score",0); row.put("answers_json","[3,3]");
        assertEquals(0,service.submit(7L,"quiz-a",List.of(0,1)).get("score"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void anotherUserCannotSubmitOrReadTheQuizById() {
        assertEquals(404,assertThrows(BusinessException.class,()->service.submit(9L,"quiz-a",List.of(0,1))).getCode());
    }
    @Test void incompleteAnswersAreRejectedWithoutWriting() {
        assertEquals(400,assertThrows(BusinessException.class,()->service.submit(7L,"quiz-a",List.of(0))).getCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void missingLessonTextDoesNotCallModelOrInventQuestions() {
        properties.setEnabled(true);
        assertEquals(409,assertThrows(BusinessException.class,()->service.generate(7L,12L,34L)).getCode());
        verifyNoInteractions(provider);
    }
    @Test void invalidModelOutputGetsOneRepairBeforeSavingAValidatedQuiz() throws Exception {
        properties.setEnabled(true);
        when(jdbc.queryForList(contains("select content_text"),eq(34L))).thenReturn(List.of(Map.of("content_text","保证一致性。确认修改。".repeat(8))));
        ObjectMapper mapper = new ObjectMapper();
        when(provider.chatTurn(anyList(),anyList(),eq(true),any())).thenReturn(
                mapper.readTree("{\"message\":{\"content\":\"invalid\"}}"),
                mapper.createObjectNode().set("message",mapper.createObjectNode().put("content",PracticeQuestionParserTest.QUESTIONS)));
        when(jdbc.queryForList(startsWith("select * from ai_practice_sessions where quiz_id=?"),anyString(),eq(7L))).thenReturn(List.of(row));
        service.generate(7L,12L,34L);
        verify(provider,times(2)).chatTurn(anyList(),anyList(),eq(true),any());
        verify(jdbc,times(1)).update(startsWith("insert into ai_practice_sessions"),any(),eq(7L),eq(12L),eq(34L),eq("基础"),any(),any(),any());
    }
}
