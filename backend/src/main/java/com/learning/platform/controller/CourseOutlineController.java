package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CourseOutlineService;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.vo.CourseOutlineView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseOutlineController {
    private final CourseOutlineService courseOutlineService;
    private final CurrentUserService currentUserService;

    public CourseOutlineController(CourseOutlineService courseOutlineService, CurrentUserService currentUserService) {
        this.courseOutlineService = courseOutlineService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{courseId}/outline")
    public Result<CourseOutlineView> outline(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId) {
        Map<String, Object> viewer = currentUserService.optional(authorization);
        return Result.success(courseOutlineService.outline(viewer, courseId));
    }
}
