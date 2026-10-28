package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.application.LearningAgentAiService;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.common.PageResult;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningPlanningServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final CatalogService catalog=mock(CatalogService.class);
    private final LearningAgentAiService ai=mock(LearningAgentAiService.class);
    private final StudentLearningPathService paths=mock(StudentLearningPathService.class);
    private final AdaptiveLearningService adaptive=mock(AdaptiveLearningService.class);
    private final AiUserRateLimiter limiter=mock(AiUserRateLimiter.class);
    private final LearningPlanningService service=new LearningPlanningService(jdbc,mapper,catalog,ai,paths,adaptive,limiter);
    private final Map<String,Object> answers=Map.of("subject","Java","background","basics","goal","project","targetStage","intermediate","dailyMinutes",30,"knownTopics",List.of("流程控制"));
    private Map<String,Object> profile() throws Exception {
        Map<String,Object> row=new LinkedHashMap<>(); row.put("user_id",51L);row.put("answers_json",mapper.writeValueAsString(answers));row.put("version",3);row.put("status","draft");return row;
    }
    @Test void validatesOptionsAndTimeButAllowsAnIncompleteDraft() {
        assertEquals(Map.of("subject","Java"),LearningPlanningService.validate(Map.of("subject","Java"),false));
        assertThrows(BusinessException.class,()->LearningPlanningService.validate(Map.of("subject","Java"),true));
        assertThrows(BusinessException.class,()->LearningPlanningService.validate(Map.of("dailyMinutes",0),false));
        assertThrows(BusinessException.class,()->LearningPlanningService.validate(Map.of("dailyMinutes",1.5),false));
        assertThrows(BusinessException.class,()->LearningPlanningService.validate(Map.of("targetStage","expert"),false));
        assertEquals(answers,LearningPlanningService.validate(answers,true));
    }
    @Test void generatingAPreviewUsesTheProfileAndDoesNotAdoptOrCreateLearningProgress() throws Exception {
        when(jdbc.queryForList(contains("select * from student_learning_profiles"),eq(51L))).thenReturn(List.of(profile()));
        when(limiter.tryAcquire(51L)).thenReturn(true);
        List<Map<String,Object>> courses=List.of(Map.of("id",7L,"title","Java基础","learningStage","foundation"),Map.of("id",8L,"title","JDBC","learningStage","intermediate"),Map.of("id",9L,"title","多线程","learningStage","advanced"));
        when(catalog.courses(51L,"Java",null,null,null,null,1,50)).thenReturn(new PageResult<>(courses,3,1,50));
        Map<String,Object> path=Map.of("title","Java学习路线","summary","按目标安排","nodes",List.of(Map.of("id","step-1","order",1,"title","JDBC","stage","intermediate","courseId",8L)),"links",List.of());
        when(ai.execute(eq("path"),any(),anyString())).thenAnswer(call->new LearningAgentResult(call.getArgument(2),"路径说明",List.of(),List.of(),false,path));
        Map<String,Object> result=service.preview(Map.of("id",51L,"name","学生"),3);
        assertNotNull(result.get("previewId")); assertEquals(path,result.get("path"));
        ArgumentCaptor<RecommendationContext> context=ArgumentCaptor.forClass(RecommendationContext.class);
        verify(ai).execute(eq("path"),context.capture(),anyString());assertEquals(answers,context.getValue().getStudyIntent());
        verify(jdbc).update(contains("insert into learning_plan_previews"),anyString(),eq(51L),eq(3),anyString());
        verifyNoInteractions(paths);
        verify(jdbc,never()).update(contains("lesson_progress"),any(Object[].class));
    }
    @Test void staleAnswersCannotGenerateANewPreview() throws Exception {
        when(jdbc.queryForList(contains("select * from student_learning_profiles"),eq(51L))).thenReturn(List.of(profile()));when(limiter.tryAcquire(51L)).thenReturn(true);
        BusinessException error=assertThrows(BusinessException.class,()->service.preview(Map.of("id",51L),2));
        assertEquals(409,error.getCode());verifyNoInteractions(ai,paths);
    }
    @Test void aPreviewFromAnotherAccountCannotBeAdopted() throws Exception {
        when(jdbc.queryForList(contains("select * from student_learning_profiles"),eq(51L))).thenReturn(List.of(profile()));
        BusinessException error=assertThrows(BusinessException.class,()->service.adopt(51L,"other-preview"));
        assertEquals(404,error.getCode());verifyNoInteractions(paths);
    }
    @Test void editingAnswersInvalidatesPreviouslyGeneratedPreviews() throws Exception {
        when(jdbc.queryForList(contains("select * from student_learning_profiles"),eq(51L))).thenReturn(List.of(profile()));
        when(jdbc.queryForList(contains("select * from learning_plan_previews"),eq("preview-1"),eq(51L))).thenReturn(List.of(Map.of("profile_version",2)));
        assertEquals(409,assertThrows(BusinessException.class,()->service.adopt(51L,"preview-1")).getCode());verifyNoInteractions(paths);
    }
    @Test void retryingAdoptionDoesNotCreateDuplicatePaths() throws Exception {
        Map<String,Object> row=profile(); row.put("active_path_id","preview-1");
        when(jdbc.queryForList(contains("select * from student_learning_profiles"),eq(51L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("select * from learning_plan_previews"),eq("preview-1"),eq(51L))).thenReturn(List.of(Map.of("profile_version",3)));
        when(paths.get(51L,"preview-1")).thenReturn(Map.of("pathId","preview-1"));
        assertEquals("preview-1",service.adopt(51L,"preview-1").get("pathId"));verify(paths,never()).save(anyLong(),any());
    }
}
