package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.LessonPlaybackService;
import com.learning.platform.vo.LessonPlaybackView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/learning/lessons")
public class LessonPlaybackController {
    private final LessonPlaybackService lessonPlaybackService;
    private final CurrentUserService currentUserService;

    public LessonPlaybackController(LessonPlaybackService lessonPlaybackService,
                                    CurrentUserService currentUserService) {
        this.lessonPlaybackService = lessonPlaybackService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{lessonId}/playback")
    public Result<LessonPlaybackView> playback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long lessonId,
            @RequestParam long courseId) {
        Map<String, Object> viewer = currentUserService.requireLogin(authorization);
        return Result.success(lessonPlaybackService.playback(viewer, courseId, lessonId));
    }
}
