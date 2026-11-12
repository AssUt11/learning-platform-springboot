package com.learning.platform.courseware;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/lessons/{lessonId}/assets/{assetId}/courseware")
public class CoursewareController {
    private final CurrentUserService users;
    private final CoursewareService documents;
    public CoursewareController(CurrentUserService users, CoursewareService documents) {
        this.users = users;
        this.documents = documents;
    }
    @GetMapping
    public Result<Map<String, Object>> status(@RequestHeader(value="Authorization",required=false) String authorization,
        @PathVariable long lessonId, @PathVariable long assetId) {
        return Result.success(documents.status(teacherId(authorization), lessonId, assetId));
    }
    @PostMapping("/extract")
    public Result<Map<String, Object>> extract(@RequestHeader(value="Authorization",required=false) String authorization,
        @PathVariable long lessonId, @PathVariable long assetId) {
        return Result.success(documents.request(teacherId(authorization), lessonId, assetId));
    }
    @PostMapping("/{documentId}/confirm")
    public Result<Map<String, Object>> confirm(@RequestHeader(value="Authorization",required=false) String authorization,
        @PathVariable long lessonId, @PathVariable long assetId, @PathVariable String documentId,
        @RequestBody Map<String, String> body) {
        return Result.success(documents.confirm(teacherId(authorization), lessonId, assetId, documentId, body.get("versionHash")));
    }
    private long teacherId(String authorization) { return ((Number) users.requireTeacher(authorization).get("id")).longValue(); }
}
