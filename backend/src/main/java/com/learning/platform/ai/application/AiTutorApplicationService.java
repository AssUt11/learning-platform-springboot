package com.learning.platform.ai.application;

import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.context.AiLearningContextService;
import com.learning.platform.ai.context.LearningContext;
import com.learning.platform.ai.domain.AiCancellation;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.ai.domain.AiProviderMessage;
import com.learning.platform.ai.domain.AiProviderRequest;
import com.learning.platform.ai.domain.AiUsage;
import com.learning.platform.ai.dto.AiChatRequest;
import com.learning.platform.ai.memory.AiConversationRepository;
import com.learning.platform.ai.prompt.TutorPromptService;
import com.learning.platform.ai.safety.ThinkingContentFilter;
import com.learning.platform.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiTutorApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiTutorApplicationService.class);
    private final AiProperties properties;
    private final AiModelProvider provider;
    private final AiLearningContextService contextService;
    private final AiConversationRepository repository;
    private final TutorPromptService promptService;
    private final ThreadPoolExecutor executor;
    private final ConcurrentMap<String, GenerationControl> requests = new ConcurrentHashMap<String, GenerationControl>();
    private final ConcurrentMap<Long, String> userRequests = new ConcurrentHashMap<Long, String>();
    private final ConcurrentMap<Long, UserRateWindow> rateWindows = new ConcurrentHashMap<Long, UserRateWindow>();

    public AiTutorApplicationService(AiProperties properties,
                                     AiModelProvider provider,
                                     AiLearningContextService contextService,
                                     AiConversationRepository repository,
                                     TutorPromptService promptService) {
        this.properties = properties;
        this.provider = provider;
        this.contextService = contextService;
        this.repository = repository;
        this.promptService = promptService;
        int concurrent = Math.max(1, properties.getMaxConcurrent());
        int queue = Math.max(1, properties.getMaxQueue());
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ai-tutor-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(concurrent, concurrent, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queue), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public String start(Map<String, Object> student, AiChatRequest request, SseEmitter emitter) {
        if (!properties.isEnabled()) throw new BusinessException(503, "AI 伴学当前未启用。");
        validate(request);
        long userId = number(student.get("id"));
        String requestId = UUID.randomUUID().toString();
        if (userRequests.putIfAbsent(userId, requestId) != null) {
            throw new BusinessException(429, "你已有一个回答正在生成，请先等待或停止它。");
        }
        if (!allowRequest(userId)) {
            userRequests.remove(userId, requestId);
            throw new BusinessException(429, "提问过于频繁，请稍后再试。");
        }

        GenerationControl control = null;
        try {
            LearningContext learningContext = contextService.load(student, request.getCourseId(), request.getLessonId());
            long conversationId = repository.resolveConversation(request.getConversationId(), userId,
                    request.getCourseId(), request.getLessonId(), learningContext.getLessonTitle());
            List<AiProviderMessage> history = repository.recentHistory(conversationId, 8);
            repository.insertMessage(conversationId, "USER", request.getMessage().trim(), null,
                    null, requestId, TutorPromptService.VERSION, "completed");
            long assistantMessageId = repository.insertMessage(conversationId, "ASSISTANT", "",
                    provider.providerCode(), provider.modelCode(), requestId, TutorPromptService.VERSION, "queued");
            List<AiProviderMessage> prompt = promptService.messages(
                    learningContext, history, request.getMessage().trim());
            control = new GenerationControl(requestId, userId, request.getCourseId(), request.getLessonId(),
                    conversationId, assistantMessageId, learningContext, prompt, emitter);
            requests.put(requestId, control);
            GenerationControl scheduled = control;
            Future<?> future = executor.submit(() -> generate(scheduled));
            control.future = future;

            registerLifecycle(control);
            Map<String, Object> created = new LinkedHashMap<String, Object>();
            created.put("conversationId", conversationId);
            created.put("requestId", requestId);
            send(emitter, "conversation.created", created);
            control.ready.countDown();
            return requestId;
        } catch (RejectedExecutionException exception) {
            if (control != null) failBeforeStart(control, "AI_BUSY");
            userRequests.remove(userId, requestId);
            throw new BusinessException(429, "本地 AI 正忙，请稍后重试。");
        } catch (RuntimeException exception) {
            if (control != null) failBeforeStart(control, "AI_RESPONSE_INVALID");
            userRequests.remove(userId, requestId);
            throw exception;
        }
    }

    public boolean cancel(long userId, String requestId) {
        GenerationControl control = requests.get(requestId);
        if (control == null || control.userId != userId) return false;
        synchronized (control) {
            if (!control.terminal.compareAndSet(false, true)) return false;
            control.cancellation.cancel();
            if (control.future != null) control.future.cancel(true);
            try {
                repository.updateMessage(control.assistantMessageId, control.answer.toString(), "cancelled");
                repository.insertUsage(control.requestId, control.userId, control.courseId, control.lessonId,
                        provider.providerCode(), provider.modelCode(), null, control.context.isNeo4jAvailable(),
                        control.context.getGraphNodeCount(), "cancelled", "AI_REQUEST_CANCELLED");
            } catch (RuntimeException persistenceError) {
                LOGGER.warn("Unable to persist cancellation for AI request {}: {}", control.requestId,
                        persistenceError.getClass().getSimpleName());
            }
        }
        cleanup(control);
        try {
            sendError(control.emitter, "AI_REQUEST_CANCELLED", "已停止生成。", control.requestId);
            control.emitter.complete();
        } catch (RuntimeException ignored) {
            // A disconnected browser is a normal cancellation path.
        }
        return true;
    }

    public int activeCount() { return executor.getActiveCount(); }
    public int queueSize() { return executor.getQueue().size(); }

    private void generate(GenerationControl control) {
        try {
            control.ready.await();
            control.cancellation.throwIfCancelled();
            repository.updateMessage(control.assistantMessageId, "", "generating");
            Map<String, Object> started = new LinkedHashMap<String, Object>();
            started.put("messageId", control.assistantMessageId);
            started.put("requestId", control.requestId);
            started.put("provider", provider.providerCode());
            started.put("model", provider.modelCode());
            started.put("knowledgeSource", control.context.isNeo4jAvailable() ? "mysql+neo4j" : "mysql");
            started.put("graphNodeCount", control.context.getGraphNodeCount());
            send(control.emitter, "message.started", started);
            sendCitation(control.emitter, "COURSE", control.courseId, control.context.getCourseTitle());
            sendCitation(control.emitter, "LESSON", control.lessonId, control.context.getLessonTitle());

            ThinkingContentFilter filter = new ThinkingContentFilter();
            AiUsage usage = provider.chatStream(new AiProviderRequest(control.prompt), raw -> {
                control.cancellation.throwIfCancelled();
                String visible = filter.accept(raw);
                if (visible.isEmpty()) return;
                control.answer.append(visible);
                send(control.emitter, "message.delta", Collections.singletonMap("content", visible));
            }, control.cancellation);
            String tail = filter.finish();
            if (!tail.isEmpty()) {
                control.answer.append(tail);
                send(control.emitter, "message.delta", Collections.singletonMap("content", tail));
            }
            if (control.answer.toString().trim().isEmpty()) {
                throw new IllegalStateException("Model returned an empty visible response");
            }
            complete(control, usage);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminate(control, "cancelled", "AI_REQUEST_CANCELLED", null);
        } catch (CancellationException exception) {
            terminate(control, "cancelled", "AI_REQUEST_CANCELLED", null);
        } catch (Exception exception) {
            String code = errorCode(exception);
            LOGGER.warn("AI request {} failed with {}: {}", control.requestId, code,
                    exception.getClass().getSimpleName());
            terminate(control, "failed", code, exception);
        }
    }

    private void complete(GenerationControl control, AiUsage usage) {
        try {
            synchronized (control) {
                if (control.terminal.get()) return;
                try {
                    repository.updateMessage(control.assistantMessageId, control.answer.toString(), "completed");
                    repository.insertCitation(control.assistantMessageId, "COURSE", control.courseId,
                            control.context.getCourseTitle());
                    repository.insertCitation(control.assistantMessageId, "LESSON", control.lessonId,
                            control.context.getLessonTitle());
                    repository.insertUsage(control.requestId, control.userId, control.courseId, control.lessonId,
                            provider.providerCode(), provider.modelCode(), usage, control.context.isNeo4jAvailable(),
                            control.context.getGraphNodeCount(), "completed", null);
                } finally {
                    control.terminal.set(true);
                }
            }
        } catch (RuntimeException persistenceError) {
            cleanup(control);
            LOGGER.warn("Unable to persist completed AI request {}: {}", control.requestId,
                    persistenceError.getClass().getSimpleName());
            try {
                sendError(control.emitter, "AI_RESPONSE_INVALID", "回答已生成，但保存失败，请重新提问。", control.requestId);
                control.emitter.complete();
            } catch (RuntimeException ignored) {
                // The client may already have disconnected.
            }
            return;
        }
        cleanup(control);
        Map<String, Object> completed = new LinkedHashMap<String, Object>();
        completed.put("messageId", control.assistantMessageId);
        completed.put("requestId", control.requestId);
        completed.put("promptTokens", usage.getPromptTokens());
        completed.put("outputTokens", usage.getOutputTokens());
        completed.put("totalDurationMs", usage.getTotalDurationMs());
        completed.put("tokensPerSecond", usage.getTokensPerSecond());
        completed.put("neo4jUsed", control.context.isNeo4jAvailable());
        completed.put("graphNodeCount", control.context.getGraphNodeCount());
        send(control.emitter, "message.completed", completed);
        control.emitter.complete();
    }

    private void terminate(GenerationControl control, String status, String code, Exception exception) {
        synchronized (control) {
            if (control.terminal.get()) return;
            try {
                repository.updateMessage(control.assistantMessageId, control.answer.toString(), status);
                repository.insertUsage(control.requestId, control.userId, control.courseId, control.lessonId,
                        provider.providerCode(), provider.modelCode(), null, control.context.isNeo4jAvailable(),
                        control.context.getGraphNodeCount(), status, code);
            } catch (RuntimeException persistenceError) {
                LOGGER.warn("Unable to persist terminal state for AI request {}: {}", control.requestId,
                        persistenceError.getClass().getSimpleName());
            } finally {
                control.terminal.set(true);
            }
        }
        cleanup(control);
        try {
            sendError(control.emitter, code, messageFor(code), control.requestId);
            control.emitter.complete();
        } catch (RuntimeException ignored) {
            // The client may already have disconnected.
        }
    }

    private void failBeforeStart(GenerationControl control, String code) {
        synchronized (control) {
            if (control.terminal.get()) return;
            control.cancellation.cancel();
            if (control.future != null) control.future.cancel(true);
            control.ready.countDown();
            control.terminal.set(true);
            try {
                repository.updateMessage(control.assistantMessageId, "", "failed");
                repository.insertUsage(control.requestId, control.userId, control.courseId, control.lessonId,
                        provider.providerCode(), provider.modelCode(), null, control.context.isNeo4jAvailable(),
                        control.context.getGraphNodeCount(), "failed", code);
            } catch (RuntimeException persistenceError) {
                LOGGER.warn("Unable to persist rejected AI request {}: {}", control.requestId,
                        persistenceError.getClass().getSimpleName());
            }
        }
        cleanup(control);
    }

    private void registerLifecycle(GenerationControl control) {
        control.emitter.onCompletion(() -> cancel(control.userId, control.requestId));
        control.emitter.onTimeout(() -> cancel(control.userId, control.requestId));
        control.emitter.onError(error -> cancel(control.userId, control.requestId));
    }

    private void cleanup(GenerationControl control) {
        requests.remove(control.requestId, control);
        userRequests.remove(control.userId, control.requestId);
    }

    private void validate(AiChatRequest request) {
        if (request == null || request.getCourseId() == null || request.getCourseId() <= 0L
                || request.getLessonId() == null || request.getLessonId() <= 0L) {
            throw new BusinessException(400, "课程和课时参数无效。");
        }
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) throw new BusinessException(400, "请输入学习问题。");
        if (message.length() > Math.max(100, properties.getMessageMaxChars())) {
            throw new BusinessException(400, "问题过长，请精简后重新提问。");
        }
        if (request.getConversationId() != null && request.getConversationId() <= 0L) {
            throw new BusinessException(400, "会话参数无效。");
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            throw new CancellationException("SSE client disconnected");
        }
    }

    private void sendError(SseEmitter emitter, String code, String message, String requestId) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", message);
        error.put("requestId", requestId);
        send(emitter, "error", error);
    }

    private void sendCitation(SseEmitter emitter, String sourceType, long sourceId, String title) {
        Map<String, Object> citation = new LinkedHashMap<String, Object>();
        citation.put("sourceType", sourceType);
        citation.put("sourceId", sourceId);
        citation.put("title", title);
        send(emitter, "citation.added", citation);
    }

    private String errorCode(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException) return "AI_TIMEOUT";
            if (current instanceof ConnectException) return "AI_SERVICE_OFFLINE";
            current = current.getCause();
        }
        return "AI_RESPONSE_INVALID";
    }

    private String messageFor(String code) {
        if ("AI_TIMEOUT".equals(code)) return "本次回答超时，请重新提问。";
        if ("AI_SERVICE_OFFLINE".equals(code)) return "本地 AI 服务未启动，请联系管理员。";
        if ("AI_REQUEST_CANCELLED".equals(code)) return "已停止生成。";
        return "AI 回答生成失败，请稍后重试。";
    }

    private long number(Object value) { return ((Number) value).longValue(); }

    private boolean allowRequest(long userId) {
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        UserRateWindow window = rateWindows.computeIfAbsent(userId, ignored -> new UserRateWindow());
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.startedAt >= 60000L) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= limit) return false;
            window.count++;
            return true;
        }
    }

    @PreDestroy
    public void close() {
        for (GenerationControl control : requests.values()) cancel(control.userId, control.requestId);
        executor.shutdownNow();
    }

    private static class GenerationControl {
        private final String requestId;
        private final long userId;
        private final long courseId;
        private final long lessonId;
        private final long conversationId;
        private final long assistantMessageId;
        private final LearningContext context;
        private final List<AiProviderMessage> prompt;
        private final SseEmitter emitter;
        private final AiCancellation cancellation = new AiCancellation();
        private final StringBuffer answer = new StringBuffer();
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile Future<?> future;

        private GenerationControl(String requestId, long userId, long courseId, long lessonId,
                                  long conversationId, long assistantMessageId, LearningContext context,
                                  List<AiProviderMessage> prompt, SseEmitter emitter) {
            this.requestId = requestId;
            this.userId = userId;
            this.courseId = courseId;
            this.lessonId = lessonId;
            this.conversationId = conversationId;
            this.assistantMessageId = assistantMessageId;
            this.context = context;
            this.prompt = prompt;
            this.emitter = emitter;
        }
    }

    private static class UserRateWindow {
        private long startedAt = System.currentTimeMillis();
        private int count;
    }
}
