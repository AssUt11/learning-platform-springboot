package com.learning.platform.ai.chat;

import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.*;
import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentChatServiceTest {
    private static class Emitter extends SseEmitter {
        final CountDownLatch done = new CountDownLatch(1);
        @Override public void send(SseEventBuilder event) throws IOException { }
        @Override public void complete() { done.countDown(); }
    }

    private static class Fixture implements AutoCloseable {
        final AiProperties properties = new AiProperties();
        final AiModelProvider provider = mock(AiModelProvider.class);
        final AgentChatRepository repository = mock(AgentChatRepository.class);
        final AgentChatRepository.Turn turn = new AgentChatRepository.Turn(40, 50,
                List.of(new AiProviderMessage("user", "我在学习Java"), new AiProviderMessage("assistant", "我们可以一起讨论Java")));
        final AgentChatService service;
        Fixture() {
            properties.setEnabled(true);
            when(provider.providerCode()).thenReturn("llamafactory");
            when(provider.modelCode()).thenReturn("zhidu-qwen3-4b-lora");
            when(repository.prepare(anyLong(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(turn);
            service = new AgentChatService(properties, provider, repository, new AiUserRateLimiter(properties));
        }
        public void close() { service.shutdown(); }
    }

    private AgentChatRequest question() {
        AgentChatRequest request = new AgentChatRequest(); request.setConversationId(40L); request.setMessage("再解释一下变量"); return request;
    }

    @Test void sendsBoundedHistoryAndPersistsOnlyVisibleResponse() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.provider.chatStream(any(), any(), any())).thenAnswer(call -> {
                AiProviderRequest request = call.getArgument(0);
                assertEquals(List.of("system", "user", "assistant", "user"), request.getMessages().stream().map(AiProviderMessage::getRole).toList());
                assertEquals("我在学习Java", request.getMessages().get(1).getContent());
                Consumer<String> consumer = call.getArgument(1);
                consumer.accept("<think>内部推理</think>变量用于保存数据。");
                return new AiUsage(-1,-1,0,10,0);
            });
            Emitter emitter = new Emitter();
            f.service.start(15, question(), emitter);
            assertTrue(emitter.done.await(3, TimeUnit.SECONDS));
            verify(f.repository).prepare(eq(15L), eq(40L), eq("再解释一下变量"), anyString(), eq("llamafactory"), eq("zhidu-qwen3-4b-lora"));
            verify(f.repository).finish(eq(f.turn), eq("变量用于保存数据。"), eq("completed"), any(AiUsage.class));
        }
    }

    @Test void cancellationChecksOwnerAndReleasesTheUserForAnotherQuestion() throws Exception {
        try (Fixture f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1);
            when(f.provider.chatStream(any(), any(), any())).thenAnswer(call -> {
                entered.countDown();
                new CountDownLatch(1).await();
                return null;
            });
            Emitter first = new Emitter();
            String requestId = f.service.start(15, question(), first);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertEquals(429, assertThrows(BusinessException.class, () -> f.service.start(15, question(), new Emitter())).getCode());
            assertFalse(f.service.cancel(16, requestId));
            assertTrue(f.service.cancel(15, requestId));
            assertTrue(first.done.await(3, TimeUnit.SECONDS));
            verify(f.repository).finish(f.turn, "", "cancelled", null);
            doAnswer(call -> {
                Consumer<String> consumer = call.getArgument(1);
                consumer.accept("第二次回答");
                return new AiUsage(-1,-1,0,10,0);
            }).when(f.provider).chatStream(any(), any(), any());
            Emitter second = new Emitter();
            f.service.start(15, question(), second);
            assertTrue(second.done.await(3, TimeUnit.SECONDS));
            verify(f.repository).finish(eq(f.turn), eq("第二次回答"), eq("completed"), any(AiUsage.class));
        }
    }

    @Test void offlineFailureDoesNotLeaveTheAccountPermanentlyBusy() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.provider.chatStream(any(), any(), any())).thenThrow(new ConnectException("offline"));
            Emitter first = new Emitter();
            f.service.start(15, question(), first);
            assertTrue(first.done.await(3, TimeUnit.SECONDS));
            verify(f.repository).finish(f.turn, "", "failed", null);
            doAnswer(call -> {
                Consumer<String> consumer = call.getArgument(1);
                consumer.accept("连接已恢复");
                return new AiUsage(-1,-1,0,10,0);
            }).when(f.provider).chatStream(any(), any(), any());
            Emitter second = new Emitter(); f.service.start(15, question(), second);
            assertTrue(second.done.await(3, TimeUnit.SECONDS));
            verify(f.repository).finish(eq(f.turn), eq("连接已恢复"), eq("completed"), any(AiUsage.class));
        }
    }

    @Test void validatesModelAndInputBeforeCreatingMessages() {
        try (Fixture f = new Fixture()) {
            when(f.provider.providerCode()).thenReturn("ollama");
            assertEquals(503, assertThrows(BusinessException.class, () -> f.service.start(15, question(), new Emitter())).getCode());
            when(f.provider.providerCode()).thenReturn("llamafactory");
            assertEquals(400, assertThrows(BusinessException.class, () -> f.service.start(15, null, new Emitter())).getCode());
            AgentChatRequest longQuestion = question(); longQuestion.setMessage("a".repeat(1001));
            assertEquals(400, assertThrows(BusinessException.class, () -> f.service.start(15, longQuestion, new Emitter())).getCode());
            verifyNoInteractions(f.repository);
        }
    }
}
