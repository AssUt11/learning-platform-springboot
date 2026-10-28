package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.service.CatalogService;
import com.learning.platform.service.CourseOutlineService;
import com.learning.platform.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalogService;
    private final CourseOutlineService courseOutlineService;
    private final CurrentUserService currentUserService;

    public CatalogController(CatalogService catalogService, CourseOutlineService courseOutlineService,
                             CurrentUserService currentUserService) {
        this.catalogService = catalogService;
        this.courseOutlineService = courseOutlineService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.success(catalogService.categories());
    }

    @GetMapping("/categories/{categoryId}/directions")
    public Result<List<Map<String, Object>>> directions(@PathVariable long categoryId) {
        return Result.success(catalogService.directions(categoryId));
    }

    @GetMapping("/courses")
    public Result<PageResult<Map<String, Object>>> courses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long directionId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String learningStage,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "12") int pageSize) {
        Map<String, Object> user = currentUserService.optional(authorization);
        Long learnerId = learnerId(user);
        return Result.success(catalogService.courses(learnerId, keyword, categoryId, directionId,
                type, difficulty, pageNum, pageSize, learningStage));
    }

    @GetMapping("/courses/{courseId}")
    public Result<Map<String, Object>> courseDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId) {
        Map<String, Object> user = currentUserService.optional(authorization);
        Map<String, Object> detail = catalogService.courseDetail(user, learnerId(user), courseId);
        detail.put("outline", courseOutlineService.outline(user, courseId));
        return Result.success(detail);
    }

    private Long learnerId(Map<String, Object> user) {
        if (user == null) {
            return null;
        }
        Object role = user.get("role");
        return "student".equals(role) || "teacher".equals(role)
                ? ((Number) user.get("id")).longValue()
                : null;
    }
}
