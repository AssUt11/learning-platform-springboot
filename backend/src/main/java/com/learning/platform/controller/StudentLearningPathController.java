package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.StudentLearningPathService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/learning/paths")
public class StudentLearningPathController {
    private final CurrentUserService users;
    private final StudentLearningPathService paths;
    public StudentLearningPathController(CurrentUserService users, StudentLearningPathService paths) {
        this.users = users;
        this.paths = paths;
    }
    @GetMapping("/latest")
    public Result<Map<String, Object>> latest(@RequestHeader(value="Authorization", required=false) String authorization) {
        return Result.success(paths.latest(userId(authorization)));
    }
    @GetMapping("/{pathId}")
    public Result<Map<String, Object>> get(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable String pathId) {
        return Result.success(paths.get(userId(authorization), pathId));
    }
    @PostMapping("/{pathId}/sync")
    public Result<Map<String, Object>> sync(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable String pathId) {
        return Result.success(paths.retrySync(userId(authorization), pathId));
    }
    private long userId(String authorization) {
        return ((Number) users.requireLearner(authorization).get("id")).longValue();
    }
}
