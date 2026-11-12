package com.learning.platform.ai.practice;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practice;
    private final CurrentUserService users;

    public PracticeController(PracticeService practice, CurrentUserService users) {
        this.practice = practice;
        this.users = users;
    }

    @GetMapping("/courses/{courseId}/lessons/{lessonId}/latest")
    public Result<Map<String, Object>> latest(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable long courseId,
        @PathVariable long lessonId
    ) {
        return Result.success(practice.latest(userId(auth), courseId, lessonId));
    }

    @PostMapping("/courses/{courseId}/lessons/{lessonId}/generate")
    public Result<Map<String, Object>> generate(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable long courseId,
        @PathVariable long lessonId
    ) {
        return Result.success(practice.generate(userId(auth), courseId, lessonId));
    }

    @PostMapping("/{quizId}/submit")
    public Result<Map<String, Object>> submit(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable String quizId,
        @RequestBody Submission body
    ) {
        return Result.success(practice.submit(userId(auth), quizId, body.answers()));
    }

    public record Submission(List<Integer> answers) {}

    @GetMapping("/courses/{courseId}/assessment")
    public Result<Map<String, Object>> assessment(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable long courseId
    ) {
        return Result.success(practice.courseAssessment(userId(auth), courseId));
    }

    @PostMapping("/courses/{courseId}/assessment/generate")
    public Result<Map<String, Object>> generateAssessment(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable long courseId,
        @RequestBody(required = false) AssessmentRequest body
    ) {
        return Result.success(practice.generateAssessment(userId(auth), courseId, body == null ? null : body.difficulty()));
    }

    public record AssessmentRequest(String difficulty) {}

    @GetMapping("/courses/{courseId}/smart/latest")
    public Result<Map<String, Object>> smartLatest(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable long courseId
    ) {
        return Result.success(practice.smartLatest(userId(auth), courseId));
    }

    @PostMapping("/courses/{courseId}/smart/generate")
    public Result<Map<String, Object>> generateSmart(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @PathVariable long courseId
    ) {
        return Result.success(practice.generateSmart(userId(auth), courseId));
    }

    private long userId(String auth) {
        return ((Number) users.requireLearner(auth).get("id")).longValue();
    }
}
