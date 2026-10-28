package com.learning.platform.ai.practice;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {
    private final PracticeService practice;
    private final CurrentUserService users;
    public PracticeController(PracticeService practice, CurrentUserService users) { this.practice = practice; this.users = users; }

    @GetMapping("/courses/{courseId}/lessons/{lessonId}/latest")
    public Result<Map<String, Object>> latest(@RequestHeader(value="Authorization", required=false) String auth,
                                             @PathVariable long courseId, @PathVariable long lessonId) {
        return Result.success(practice.latest(userId(auth), courseId, lessonId));
    }
    @PostMapping("/courses/{courseId}/lessons/{lessonId}/generate")
    public Result<Map<String, Object>> generate(@RequestHeader(value="Authorization", required=false) String auth,
                                               @PathVariable long courseId, @PathVariable long lessonId) {
        return Result.success(practice.generate(userId(auth), courseId, lessonId));
    }
    @PostMapping("/{quizId}/submit")
    public Result<Map<String, Object>> submit(@RequestHeader(value="Authorization", required=false) String auth,
                                             @PathVariable String quizId, @RequestBody Submission body) {
        return Result.success(practice.submit(userId(auth), quizId, body.answers()));
    }
    public record Submission(List<Integer> answers) { }
    private long userId(String auth) { return ((Number) users.requireLearner(auth).get("id")).longValue(); }
}
