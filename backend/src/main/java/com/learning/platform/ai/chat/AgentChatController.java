package com.learning.platform.ai.chat;

import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;

@RestController
@RequestMapping("/api/agent-chat")
public class AgentChatController {
    private final CurrentUserService users;
    private final AgentChatRepository repository;
    private final AgentChatService service;
    private final AiProperties properties;
    public AgentChatController(CurrentUserService users, AgentChatRepository repository, AgentChatService service, AiProperties properties) {
        this.users = users; this.repository = repository; this.service = service; this.properties = properties;
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestHeader(value="Authorization", required=false) String authorization) {
        userId(authorization);
        return Result.success(service.status());
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> list(@RequestHeader(value="Authorization", required=false) String authorization) {
        return Result.success(repository.list(userId(authorization)));
    }

    @GetMapping("/conversations/{id}")
    public Result<Map<String, Object>> conversation(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable long id) {
        return Result.success(repository.conversation(userId(authorization), id));
    }

    @PostMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value="Authorization", required=false) String authorization, @RequestBody AgentChatRequest request) {
        long userId = userId(authorization);
        SseEmitter emitter = new SseEmitter(Math.max(30000, properties.getStreamTimeoutMs()));
        service.start(userId, request, emitter);
        return emitter;
    }

    @PostMapping("/requests/{id}/cancel")
    public Result<Map<String, Object>> cancel(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable String id) {
        return Result.success(Map.of("cancelled", service.cancel(userId(authorization), id)));
    }

    private long userId(String authorization) { return ((Number) users.requireLearner(authorization).get("id")).longValue(); }
}
