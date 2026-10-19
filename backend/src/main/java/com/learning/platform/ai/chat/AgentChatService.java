package com.learning.platform.ai.chat;

import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.*;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentChatService {
    private static final Logger LOG = LoggerFactory.getLogger(AgentChatService.class);
    private static final String SYSTEM_PROMPT = "你是知度课堂的学习助理。可以与用户连续对话，解释知识、梳理思路并给出简短例子。"
            + "使用清晰自然的中文，不确定时直接说明，不展示内部推理。你没有读取课程、修改数据库或执行工具的能力，"
            + "不要声称完成了这些操作。只有本次提供的对话是已知信息，不虚构用户成绩、课程或个人资料。";
    private final AiProperties properties;
    private final AiModelProvider provider;
    private final AgentChatRepository repository;
    private final AiUserRateLimiter limiter;
    private final ThreadPoolExecutor executor;
    private final Semaphore capacity;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> userJobs = new ConcurrentHashMap<>();

    public AgentChatService(AiProperties properties, AiModelProvider provider, AgentChatRepository repository, AiUserRateLimiter limiter) {
        this.properties = properties;
        this.provider = provider;
        this.repository = repository;
        this.limiter = limiter;
        int workers = Math.max(1, properties.getMaxConcurrent());
        int queue = Math.max(1, properties.getMaxQueue());
        this.capacity = new Semaphore(workers + queue);
        this.executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queue), task -> {
            Thread thread = new Thread(task, "agent-chat-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<String, Object> status() {
        if (!properties.isEnabled()) return Map.of("available", false, "message", "AI 服务尚未启用。");
        if (!"llamafactory".equals(provider.providerCode())) return Map.of("available", false,
                "message", "请使用 LLaMA-Factory 启动配置连接微调模型。");
        Map<String, Object> result = new LinkedHashMap<>(provider.health());
        if (!Boolean.TRUE.equals(result.get("available"))) result.put("message", "微调模型服务暂不可用，请确认模型服务已启动。");
        return result;
    }

    public String start(long userId, AgentChatRequest request, SseEmitter emitter) {
        if (!properties.isEnabled() || !"llamafactory".equals(provider.providerCode())) {
            throw new BusinessException(503, "请启用 LLaMA-Factory 微调模型服务。");
        }
        String question = request == null || request.getMessage() == null ? "" : request.getMessage().trim();
        if (question.isEmpty() || question.length() > Math.max(100, properties.getMessageMaxChars())) {
            throw new BusinessException(400, "请输入有效问题，长度不超过 " + Math.max(100, properties.getMessageMaxChars()) + " 字。");
        }
        if (request.getConversationId() != null && request.getConversationId() <= 0) throw new BusinessException(400, "对话参数无效。");
        String requestId = UUID.randomUUID().toString();
        if (userJobs.putIfAbsent(userId, requestId) != null) throw new BusinessException(429, "已有回答正在生成，请先等待或停止。");
        boolean reserved = false;
        Job job = null;
        try {
            if (!limiter.tryAcquire(userId)) throw new BusinessException(429, "提问过于频繁，请稍后再试。");
            if (!(reserved = capacity.tryAcquire())) throw new BusinessException(429, "模型服务繁忙，请稍后再试。");
            AgentChatRepository.Turn turn = repository.prepare(userId, request.getConversationId(), question,
                    requestId, provider.providerCode(), provider.modelCode());
            job = new Job(userId, requestId, turn, prompt(turn.history(), question), emitter);
            Job scheduled = job;
            job.future = new FutureTask<>(() -> { generate(scheduled); return null; });
            jobs.put(requestId, job);
            emitter.onCompletion(() -> cancel(userId, requestId));
            emitter.onTimeout(() -> cancel(userId, requestId));
            emitter.onError(error -> cancel(userId, requestId));
            send(emitter, "conversation.created", Map.of("conversationId", turn.conversationId(), "requestId", requestId,
                    "messageId", turn.messageId(), "provider", provider.providerCode(), "model", provider.modelCode()));
            if (!job.terminal.get()) executor.execute(job.future);
            return requestId;
        } catch (RuntimeException error) {
            if (job != null) terminate(job, "failed", "回答未能开始，请重新发送。", null);
            else {
                if (reserved) capacity.release();
                userJobs.remove(userId, requestId);
            }
            if (job != null) return requestId; // SSE headers may already have been sent.
            throw error;
        }
    }

    public boolean cancel(long userId, String requestId) {
        Job job = jobs.get(requestId);
        if (job == null || job.userId != userId) return false;
        return terminate(job, "cancelled", "已停止生成。", null);
    }

    private void generate(Job job) {
        try {
            job.cancellation.throwIfCancelled();
            synchronized (job) {
                if (job.terminal.get()) return;
                repository.generating(job.turn.messageId());
            }
            send(job.emitter, "message.started", Map.of("messageId", job.turn.messageId(), "requestId", job.requestId));
            ThinkingContentFilter filter = new ThinkingContentFilter();
            AiUsage usage = provider.chatStream(new AiProviderRequest(job.prompt), raw -> {
                String content = filter.accept(raw);
                if (!content.isEmpty()) append(job, content);
            }, job.cancellation);
            String tail = filter.finish();
            if (!tail.isEmpty()) append(job, tail);
            synchronized (job) {
                if (job.terminal.get()) return;
                if (job.answer.toString().isBlank()) throw new IllegalStateException("Empty model answer");
                repository.finish(job.turn, job.answer.toString(), "completed", usage);
                job.terminal.set(true);
            }
            cleanup(job);
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("messageId", job.turn.messageId());
            completed.put("requestId", job.requestId);
            completed.put("model", provider.modelCode());
            completed.put("outputTokens", usage.getOutputTokens() < 0 ? null : usage.getOutputTokens());
            send(job.emitter, "message.completed", completed);
            job.emitter.complete();
        } catch (Exception error) {
            boolean cancelled = job.cancellation.isCancelled() || error instanceof CancellationException || error instanceof InterruptedException;
            terminate(job, cancelled ? "cancelled" : "failed", cancelled ? "已停止生成。" : errorMessage(error), null);
            if (!cancelled) LOG.warn("Agent chat {} failed: {}", job.requestId, error.getClass().getSimpleName());
        }
    }

    private void append(Job job, String content) {
        synchronized (job) {
            job.cancellation.throwIfCancelled();
            if (job.terminal.get()) throw new CancellationException();
            job.answer.append(content);
            send(job.emitter, "message.delta", Map.of("content", content));
        }
    }

    private boolean terminate(Job job, String state, String message, AiUsage usage) {
        synchronized (job) {
            if (!job.terminal.compareAndSet(false, true)) return false;
            job.cancellation.cancel();
            if (job.future != null) {
                job.future.cancel(true);
                executor.remove(job.future);
            }
            try { repository.finish(job.turn, job.answer.toString(), state, usage); }
            catch (RuntimeException error) { LOG.warn("Unable to save Agent chat {}: {}", job.requestId, error.getClass().getSimpleName()); }
        }
        cleanup(job);
        try {
            send(job.emitter, "error", Map.of("message", message, "code", "cancelled".equals(state) ? "AI_REQUEST_CANCELLED" : "AI_CHAT_FAILED"));
            job.emitter.complete();
        } catch (RuntimeException ignored) { /* The client may already be disconnected. */ }
        return true;
    }

    private void cleanup(Job job) {
        if (!job.cleaned.compareAndSet(false, true)) return;
        jobs.remove(job.requestId, job);
        userJobs.remove(job.userId, job.requestId);
        capacity.release();
    }

    private List<AiProviderMessage> prompt(List<AiProviderMessage> history, String question) {
        List<AiProviderMessage> selected = new ArrayList<>(history);
        int budget = Math.max(1000, properties.getContextMaxChars()) - question.length() - SYSTEM_PROMPT.length();
        while (!selected.isEmpty() && selected.stream().mapToInt(item -> item.getContent().length()).sum() > budget) {
            selected.remove(0);
            if (!selected.isEmpty() && "assistant".equals(selected.get(0).getRole())) selected.remove(0);
        }
        while (!selected.isEmpty() && !"user".equals(selected.get(0).getRole())) selected.remove(0);
        selected.add(0, new AiProviderMessage("system", SYSTEM_PROMPT));
        selected.add(new AiProviderMessage("user", question));
        return selected;
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try { emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON)); }
        catch (IOException | IllegalStateException error) { throw new CancellationException("Chat client disconnected"); }
    }

    private String errorMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) return "回答超时，请稍后重试。";
            if (cause instanceof ConnectException) return "无法连接微调模型服务，请确认推理服务已启动。";
        }
        if (error instanceof BusinessException) return error.getMessage();
        return "回答生成失败，请稍后重试。";
    }

    @PreDestroy public void shutdown() {
        jobs.values().forEach(job -> terminate(job, "cancelled", "服务正在重启，请稍后继续对话。", null));
        executor.shutdownNow();
    }

    private static class Job {
        final long userId;
        final String requestId;
        final AgentChatRepository.Turn turn;
        final List<AiProviderMessage> prompt;
        final SseEmitter emitter;
        final AiCancellation cancellation = new AiCancellation();
        final StringBuilder answer = new StringBuilder();
        final AtomicBoolean terminal = new AtomicBoolean();
        final AtomicBoolean cleaned = new AtomicBoolean();
        FutureTask<Void> future;
        Job(long userId, String requestId, AgentChatRepository.Turn turn, List<AiProviderMessage> prompt, SseEmitter emitter) {
            this.userId = userId; this.requestId = requestId; this.turn = turn; this.prompt = prompt; this.emitter = emitter;
        }
    }
}
