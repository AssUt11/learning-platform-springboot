package com.learning.platform.ai.controller;

import com.learning.platform.ai.application.AiTutorApplicationService;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.dto.AiChatRequest;
import com.learning.platform.ai.dto.AiFeedbackRequest;
import com.learning.platform.ai.memory.AiConversationRepository;
import com.learning.platform.common.Result;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiTutorController {
    private final CurrentUserService currentUserService;
    private final AiTutorApplicationService tutorService;
    private final AiConversationRepository repository;
    private final AiProperties properties;

    public AiTutorController(CurrentUserService currentUserService,
                             AiTutorApplicationService tutorService,
                             AiConversationRepository repository,
                             AiProperties properties) {
        this.currentUserService = currentUserService;
        this.tutorService = tutorService;
        this.repository = repository;
        this.properties = properties;
    }

    @PostMapping(value = "/tutor/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AiChatRequest request) {
        Map<String, Object> student = currentUserService.requireStudent(authorization);
        SseEmitter emitter = new SseEmitter(Math.max(30000L, properties.getStreamTimeoutMs()));
        tutorService.start(student, request, emitter);
        return emitter;
    }

    @PostMapping("/requests/{requestId}/cancel")
    public Result<Map<String, Object>> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String requestId) {
        Map<String, Object> student = currentUserService.requireStudent(authorization);
        boolean cancelled = tutorService.cancel(number(student.get("id")), requestId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("cancelled", cancelled);
        return Result.success(result);
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> student = currentUserService.requireStudent(authorization);
        return Result.success(repository.listConversations(number(student.get("id"))));
    }

    @GetMapping("/conversations/{id}")
    public Result<Map<String, Object>> conversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        Map<String, Object> student = currentUserService.requireStudent(authorization);
        return Result.success(repository.conversation(id, number(student.get("id"))));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        Map<String, Object> student = currentUserService.requireStudent(authorization);
        repository.deleteConversation(id, number(student.get("id")));
        return Result.success();
    }

    @PostMapping("/messages/{id}/feedback")
    public Result<Void> feedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id,
            @RequestBody AiFeedbackRequest request) {
        Map<String, Object> student = currentUserService.requireStudent(authorization);
        if (request == null || request.getRating() == null
                || !(request.getRating() == 1 || request.getRating() == -1)) {
            throw new BusinessException(400, "反馈评分必须是 1 或 -1。");
        }
        repository.feedback(id, number(student.get("id")), request.getRating(), request.getReason());
        return Result.success();
    }

    private long number(Object value) { return ((Number) value).longValue(); }
}
