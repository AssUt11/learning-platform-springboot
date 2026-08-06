package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.dto.LessonProgressRequest;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.LessonProgressService;
import com.learning.platform.vo.CourseLearningProgressView;
import com.learning.platform.vo.LessonProgressView;
import com.learning.platform.vo.ResumeLearningView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LessonProgressController {
    private final CurrentUserService currentUserService;
    private final LessonProgressService lessonProgressService;

    public LessonProgressController(CurrentUserService currentUserService,
                                    LessonProgressService lessonProgressService) {
        this.currentUserService = currentUserService;
        this.lessonProgressService = lessonProgressService;
    }

    @PutMapping("/lessons/{lessonId}/progress")
    public Result<LessonProgressView> save(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long lessonId,
            @RequestBody LessonProgressRequest request) {
        Map<String, Object> learner = currentUserService.requireLearner(authorization);
        return Result.success(lessonProgressService.save(number(learner.get("id")), lessonId, request));
    }

    @GetMapping("/courses/{courseId}/progress")
    public Result<CourseLearningProgressView> courseProgress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId) {
        Map<String, Object> learner = currentUserService.requireLearner(authorization);
        return Result.success(lessonProgressService.courseProgress(number(learner.get("id")), courseId));
    }

    @GetMapping("/resume")
    public Result<ResumeLearningView> resume(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> learner = currentUserService.requireLearner(authorization);
        return Result.success(lessonProgressService.resume(number(learner.get("id"))));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }
}
