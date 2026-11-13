package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.KnowledgeCourseTargetService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeCourseTargetController {

    private final KnowledgeCourseTargetService targets;

    public KnowledgeCourseTargetController(KnowledgeCourseTargetService targets) {
        this.targets = targets;
    }

    // Like the public course catalogue, this only returns approved, published metadata.
    @GetMapping("/course-targets")
    public Result<List<Map<String, Object>>> targets(
        @RequestParam String category,
        @RequestParam String direction,
        @RequestParam String title
    ) {
        return Result.success(targets.targets(category, direction, title));
    }
}
