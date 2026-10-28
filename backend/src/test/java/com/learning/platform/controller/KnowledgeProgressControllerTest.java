package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeProgressControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final KnowledgeProgressController controller = new KnowledgeProgressController(jdbc, users);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void returnsAllTopicsAndScopesActualLessonProgressToTheAuthenticatedLearner() throws Exception {
        when(users.requireLearner("Bearer student-a")).thenReturn(Map.of("id", 15L));
        List<Map<String, Object>> topics = IntStream.range(0, 12)
                .mapToObj(index -> Map.<String, Object>of("subject", "Java", "knowledgeNode", "知识点" + index, "progress", 100))
                .toList();
        when(jdbc.queryForList(anyString(), eq(15L))).thenReturn(topics);

        mvc.perform(get("/api/learning/knowledge-progress").header("Authorization", "Bearer student-a")
                        .param("studentId", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[11].progress").value(100));
        verify(jdbc).queryForList(anyString(), eq(15L));
    }

    @Test
    void anotherAccountDoesNotReceiveTheFirstAccountsProgress() throws Exception {
        when(users.requireLearner("Bearer student-b")).thenReturn(Map.of("id", 16L));
        when(jdbc.queryForList(anyString(), eq(16L))).thenReturn(List.of());
        mvc.perform(get("/api/learning/knowledge-progress").header("Authorization", "Bearer student-b"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(jdbc).queryForList(anyString(), eq(16L));
    }

    @Test
    void unauthenticatedRequestsNeverReadLearningRecords() {
        when(users.requireLearner(null)).thenThrow(new BusinessException(401, "Please log in first"));
        assertThrows(BusinessException.class, () -> controller.progress(null));
        verifyNoInteractions(jdbc);
    }
}
