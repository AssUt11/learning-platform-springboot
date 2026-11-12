package com.learning.platform.ai.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.context.AiLearningContextService;
import com.learning.platform.ai.context.CoursewareSnippet;
import com.learning.platform.ai.context.LearningContext;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.ai.domain.AiProviderRequest;
import com.learning.platform.ai.domain.AiUsage;
import com.learning.platform.ai.dto.AiChatRequest;
import com.learning.platform.ai.memory.AiConversationRepository;
import com.learning.platform.ai.prompt.TutorPromptService;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AiTutorCoursewareReferenceTest {
    @Test
    void connectionFailureCannotPublishOrPersistReferences() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.provider.chatStream(any(), any(), any())).thenThrow(new ConnectException("offline"));
            fixture.service.start(Map.of("id", 7L), request(), fixture.emitter);
            assertTrue(fixture.emitter.done.await(3, TimeUnit.SECONDS));
            assertFalse(fixture.emitter.events.contains("citation.added"));
            verify(fixture.repository, never()).insertCitation(anyLong(), anyMap());
        }
    }

    @Test
    void cancellationDoesNotPersistReferencesForAnUnfinishedAnswer() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch partial = new CountDownLatch(1);
            when(fixture.provider.chatStream(any(), any(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked") Consumer<String> output = invocation.getArgument(1, Consumer.class);
                output.accept("一段尚未完成的回答");
                partial.countDown();
                new CountDownLatch(1).await();
                return new AiUsage(1, 1, 0, 1, 1);
            });
            String id = fixture.service.start(Map.of("id", 7L), request(), fixture.emitter);
            assertTrue(partial.await(3, TimeUnit.SECONDS));
            assertTrue(fixture.service.cancel(7L, id));
            assertTrue(fixture.emitter.done.await(3, TimeUnit.SECONDS));
            verify(fixture.repository, never()).insertCitation(anyLong(), anyMap());
        }
    }

    @Test
    void completedAnswerPublishesBudgetedReferencesOnceAndPersistsTheirMetadata() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.provider.chatStream(any(), any(), any())).thenAnswer(invocation -> {
                AiProviderRequest input = invocation.getArgument(0);
                assertTrue(input.getMessages().stream().anyMatch(message -> message.getContent().contains("[资料1]")));
                @SuppressWarnings("unchecked") Consumer<String> output = invocation.getArgument(1, Consumer.class);
                output.accept("事务隔离级别"); output.accept("决定并发读取边界。");
                return new AiUsage(10, 8, 0, 100, 80);
            });
            fixture.service.start(Map.of("id", 7L), request(), fixture.emitter);
            assertTrue(fixture.emitter.done.await(3, TimeUnit.SECONDS));
            assertEquals(3, fixture.emitter.events.stream().filter("citation.added"::equals).count());
            verify(fixture.repository).insertCitation(eq(11L), argThat(source ->
                "COURSEWARE_CHUNK".equals(source.get("sourceType")) && source.get("chunkId").equals(8L)
                    && source.get("locationIndex").equals(4) && source.get("versionHash").equals("a".repeat(64))));
        }
    }

    private static AiChatRequest request() {
        AiChatRequest request = new AiChatRequest();
        request.setCourseId(21L); request.setLessonId(302L); request.setMessage("事务隔离是什么？");
        return request;
    }

    private static final class Fixture implements AutoCloseable {
        private final AiModelProvider provider = mock(AiModelProvider.class);
        private final AiConversationRepository repository = mock(AiConversationRepository.class);
        private final RecordingEmitter emitter = new RecordingEmitter();
        private final AiTutorApplicationService service;
        private Fixture() {
            AiProperties properties = new AiProperties(); properties.setEnabled(true);
            AiLearningContextService contexts = mock(AiLearningContextService.class);
            LearningContext context = new LearningContext(21, 302, "数据库", "事务隔离", "数据库", "当前课时公开文字", false, 0,
                List.of(new CoursewareSnippet(8, "doc", 9, 21, 302, "a".repeat(64), "讲义.pdf", "PAGE", 4, "事务隔离级别限制并发读写。")));
            when(contexts.load(anyMap(), eq(21L), eq(302L), anyString())).thenReturn(context);
            when(repository.resolveConversation(isNull(), eq(7L), eq(21L), eq(302L), anyString())).thenReturn(1L);
            when(repository.insertMessage(anyLong(), anyString(), anyString(), nullable(String.class), nullable(String.class), anyString(), anyString(), anyString()))
                .thenReturn(10L, 11L);
            when(provider.providerCode()).thenReturn("llamafactory"); when(provider.modelCode()).thenReturn("test-model");
            AiUserRateLimiter limiter = mock(AiUserRateLimiter.class);
            when(limiter.tryAcquire(7L)).thenReturn(true);
            service = new AiTutorApplicationService(properties, provider, contexts, repository,
                new TutorPromptService(properties, mock(JdbcTemplate.class)), limiter);
        }
        @Override public void close() { service.close(); }
    }

    private static final class RecordingEmitter extends SseEmitter {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch done = new CountDownLatch(1);
        @Override public void send(SseEventBuilder event) {
            for (ResponseBodyEmitter.DataWithMediaType part : event.build()) {
                if (part.getData() instanceof String text) {
                    text.lines().filter(line -> line.startsWith("event:"))
                        .forEach(line -> events.add(line.substring("event:".length())));
                }
            }
        }
        @Override public void complete() { done.countDown(); }
    }
}
