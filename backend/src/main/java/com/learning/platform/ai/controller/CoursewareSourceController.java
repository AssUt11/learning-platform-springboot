package com.learning.platform.ai.controller;

import com.learning.platform.ai.context.CoursewareRetrievalService;
import com.learning.platform.common.Result;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.LessonPlaybackService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/courseware/chunks")
public class CoursewareSourceController {

    private final CurrentUserService users;
    private final LessonPlaybackService playback;
    private final CoursewareRetrievalService retrieval;

    public CoursewareSourceController(CurrentUserService users, LessonPlaybackService playback,
        CoursewareRetrievalService retrieval) {
        this.users = users;
        this.playback = playback;
        this.retrieval = retrieval;
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> source(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable long id, @RequestParam long courseId, @RequestParam long lessonId) {
        Map<String, Object> viewer = users.requireLogin(authorization);
        if (id <= 0 || courseId <= 0 || lessonId <= 0) throw new BusinessException(400, "参考资料编号无效。");
        playback.playback(viewer, courseId, lessonId);
        return Result.success(retrieval.source(courseId, lessonId, id));
    }
}
