package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.dto.CreateChapterRequest;
import com.learning.platform.dto.CreateLessonRequest;
import com.learning.platform.dto.LessonAssetRequest;
import com.learning.platform.dto.ReorderRequest;
import com.learning.platform.dto.UpdateChapterRequest;
import com.learning.platform.dto.UpdateLessonRequest;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.TeacherCourseStructureService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherCourseStructureController {
    private final TeacherCourseStructureService structureService;
    private final CurrentUserService currentUserService;

    public TeacherCourseStructureController(TeacherCourseStructureService structureService,
                                            CurrentUserService currentUserService) {
        this.structureService = structureService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/courses/{courseId}/chapters")
    public Result<Map<String, Long>> createChapter(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable long courseId,
                                                   @RequestBody CreateChapterRequest request) {
        long id = structureService.createChapter(teacherId(authorization), courseId, request);
        return Result.success(idResult("chapterId", id));
    }

    @PutMapping("/chapters/{chapterId}")
    public Result<Void> updateChapter(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable long chapterId,
                                      @RequestBody UpdateChapterRequest request) {
        structureService.updateChapter(teacherId(authorization), chapterId, request);
        return Result.success();
    }

    @DeleteMapping("/chapters/{chapterId}")
    public Result<Void> deleteChapter(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable long chapterId) {
        structureService.disableChapter(teacherId(authorization), chapterId);
        return Result.success();
    }

    @PutMapping("/courses/{courseId}/chapters/reorder")
    public Result<Void> reorderChapters(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable long courseId,
                                        @RequestBody ReorderRequest request) {
        structureService.reorderChapters(teacherId(authorization), courseId, request);
        return Result.success();
    }

    @PostMapping("/chapters/{chapterId}/lessons")
    public Result<Map<String, Long>> createLesson(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @PathVariable long chapterId,
                                                  @RequestBody CreateLessonRequest request) {
        long id = structureService.createLesson(teacherId(authorization), chapterId, request);
        return Result.success(idResult("lessonId", id));
    }

    @PutMapping("/lessons/{lessonId}")
    public Result<Void> updateLesson(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable long lessonId,
                                     @RequestBody UpdateLessonRequest request) {
        structureService.updateLesson(teacherId(authorization), lessonId, request);
        return Result.success();
    }

    @DeleteMapping("/lessons/{lessonId}")
    public Result<Void> deleteLesson(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable long lessonId) {
        structureService.disableLesson(teacherId(authorization), lessonId);
        return Result.success();
    }

    @PutMapping("/chapters/{chapterId}/lessons/reorder")
    public Result<Void> reorderLessons(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable long chapterId,
                                       @RequestBody ReorderRequest request) {
        structureService.reorderLessons(teacherId(authorization), chapterId, request);
        return Result.success();
    }

    @PostMapping(value = "/lessons/{lessonId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Long>> createAsset(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable long lessonId,
                                                 @RequestParam String assetType,
                                                 @RequestParam(defaultValue = "") String contentText,
                                                 @RequestParam(defaultValue = "") String coverUrl,
                                                 @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        long id = structureService.createAsset(teacherId(authorization), lessonId, assetType, contentText, coverUrl, file);
        return Result.success(idResult("assetId", id));
    }

    @PutMapping("/lessons/{lessonId}/assets/{assetId}")
    public Result<Void> updateAsset(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable long lessonId,
                                    @PathVariable long assetId,
                                    @RequestBody LessonAssetRequest request) {
        structureService.updateAsset(teacherId(authorization), lessonId, assetId, request);
        return Result.success();
    }

    @DeleteMapping("/lessons/{lessonId}/assets/{assetId}")
    public Result<Void> deleteAsset(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable long lessonId,
                                    @PathVariable long assetId) {
        structureService.disableAsset(teacherId(authorization), lessonId, assetId);
        return Result.success();
    }

    private long teacherId(String authorization) {
        Map<String, Object> teacher = currentUserService.requireTeacher(authorization);
        return ((Number) teacher.get("id")).longValue();
    }

    private Map<String, Long> idResult(String key, long id) {
        Map<String, Long> result = new LinkedHashMap<String, Long>();
        result.put(key, id);
        return result;
    }
}
