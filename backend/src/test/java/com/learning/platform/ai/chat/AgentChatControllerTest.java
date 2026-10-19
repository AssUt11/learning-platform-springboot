package com.learning.platform.ai.chat;

import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.exception.GlobalExceptionHandler;
import com.learning.platform.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentChatControllerTest {
    @Test void usesAuthenticatedOwnerAndRejectsUnauthenticatedHistoryRequests() throws Exception {
        CurrentUserService users = mock(CurrentUserService.class);
        AgentChatRepository repository = mock(AgentChatRepository.class);
        when(users.requireLearner(null)).thenThrow(new BusinessException(401,"请先登录"));
        when(users.requireLearner("Bearer other")).thenReturn(Map.of("id",16L,"role","student"));
        when(repository.conversation(16,40)).thenThrow(new BusinessException(404,"对话不存在"));
        var mvc = MockMvcBuilders.standaloneSetup(new AgentChatController(users,repository,mock(AgentChatService.class),new AiProperties()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/agent-chat/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agent-chat/conversations/40?userId=15").header("Authorization","Bearer other")).andExpect(status().isNotFound());
        verify(repository).conversation(16,40);
        verify(repository,never()).conversation(15,40);
    }
}
