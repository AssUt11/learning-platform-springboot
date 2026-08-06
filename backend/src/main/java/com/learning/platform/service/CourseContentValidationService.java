package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.CourseContentValidationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseContentValidationService {
    private final JdbcTemplate jdbcTemplate;

    public CourseContentValidationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CourseContentValidationResult validatePublic(long publicCourseId) {
        return validate(publicCourseId, ValidationScope.PUBLIC);
    }

    public CourseContentValidationResult validateForPublication(long publicCourseId) {
        return validate(publicCourseId, ValidationScope.PUBLICATION_CANDIDATE);
    }

    public CourseContentValidationResult requirePublishable(long publicCourseId) {
        CourseContentValidationResult result = validateForPublication(publicCourseId);
        if (!result.isPublishable()) {
            throw new BusinessException(409, "课程尚不具备发布条件：" + String.join("；", result.getReasons()));
        }
        return result;
    }

    public List<CourseContentValidationResult> incompletePublishedCourses() {
        List<Long> ids = jdbcTemplate.query(
                "select id from resources where status=1 and review_status='approved' order by id",
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        List<CourseContentValidationResult> issues = new ArrayList<CourseContentValidationResult>();
        for (Long id : ids) {
            CourseContentValidationResult result = validatePublic(id.longValue());
            if (!result.isContentComplete()) issues.add(result);
        }
        return issues;
    }

    public List<CourseContentValidationResult> incompleteApprovedCourses() {
        List<Long> ids = jdbcTemplate.query(
                "select id from resources where review_status='approved' and lifecycle_status='active' order by id",
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        List<CourseContentValidationResult> issues = new ArrayList<CourseContentValidationResult>();
        for (Long id : ids) {
            CourseContentValidationResult result = validatePublic(id.longValue());
            if (!result.isContentComplete()) issues.add(result);
        }
        return issues;
    }

    @Transactional
    public List<Long> unpublishIncompleteCourses(long adminId, int expectedCount) {
        List<CourseContentValidationResult> issues = incompletePublishedCourses();
        if (issues.size() != expectedCount) {
            throw new BusinessException(409, "课程完整性检查数量已变化，预期 " + expectedCount
                    + " 门，实际 " + issues.size() + " 门，未执行批量下架。");
        }
        List<Long> updatedIds = new ArrayList<Long>();
        for (CourseContentValidationResult issue : issues) {
            int updated = jdbcTemplate.update(
                    "update resources set status=0 where id=? and status=1 and review_status='approved'",
                    issue.getCourseId());
            if (updated != 1) {
                throw new BusinessException(409, "课程状态发生变化，批量下架已回滚：" + issue.getCourseId());
            }
            jdbcTemplate.update(
                    "insert into admin_audit_logs(admin_id,action_type,target_type,target_id,detail) values(?,?,?,?,?)",
                    adminId, "COURSE_CONTENT_UNPUBLISHED", "resource", issue.getCourseId(),
                    "内容完整性校验未通过，status 由 1 调整为 0；原因：" + String.join("；", issue.getReasons()));
            updatedIds.add(issue.getCourseId());
        }
        return updatedIds;
    }

    private CourseContentValidationResult validate(long publicCourseId, ValidationScope scope) {
        List<Map<String, Object>> resourceRows = jdbcTemplate.queryForList(
                "select r.id,r.title,r.type,r.teacher_id teacherId,r.status,r.review_status reviewStatus,"
                        + "c.id internalCourseId from resources r left join courses c on c.legacy_resource_id=r.id "
                        + "where r.id=?",
                publicCourseId);
        if (resourceRows.isEmpty()) {
            throw new BusinessException(404, "课程不存在。");
        }
        Map<String, Object> resource = resourceRows.get(0);
        Long internalCourseId = nullableNumber(resource.get("internalCourseId"));
        List<Map<String, Object>> chapters = internalCourseId == null
                ? new ArrayList<Map<String, Object>>()
                : jdbcTemplate.queryForList(
                "select id,status from course_chapters where course_id=? and status<>'disabled' order by sort_order,id",
                internalCourseId);
        List<Map<String, Object>> lessons = internalCourseId == null
                ? new ArrayList<Map<String, Object>>()
                : jdbcTemplate.queryForList(
                "select l.id,l.lesson_type lessonType,l.duration_seconds durationSeconds,l.status,ch.status chapterStatus "
                        + "from course_lessons l join course_chapters ch on ch.id=l.chapter_id "
                        + "where ch.course_id=? and ch.status<>'disabled' and l.status<>'disabled' "
                        + "order by ch.sort_order,ch.id,l.sort_order,l.id",
                internalCourseId);
        List<Map<String, Object>> assets = internalCourseId == null
                ? new ArrayList<Map<String, Object>>()
                : jdbcTemplate.queryForList(
                "select a.id,a.lesson_id lessonId,a.asset_type assetType,a.file_url fileUrl,"
                        + "a.content_text contentText,a.transcoding_status transcodingStatus "
                        + "from lesson_assets a join course_lessons l on l.id=a.lesson_id "
                        + "join course_chapters ch on ch.id=l.chapter_id "
                        + "where ch.course_id=? and a.status=1 order by a.lesson_id,a.id",
                internalCourseId);

        Map<Long, List<Map<String, Object>>> assetsByLesson = new LinkedHashMap<Long, List<Map<String, Object>>>();
        for (Map<String, Object> asset : assets) {
            assetsByLesson.computeIfAbsent(number(asset.get("lessonId")), ignored -> new ArrayList<Map<String, Object>>())
                    .add(asset);
        }

        List<Map<String, Object>> eligibleLessons = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> lesson : lessons) {
            String lessonStatus = text(lesson.get("status"));
            String chapterStatus = text(lesson.get("chapterStatus"));
            boolean eligible = scope == ValidationScope.PUBLIC
                    ? "published".equals(chapterStatus) && "published".equals(lessonStatus)
                    : ("ready".equals(lessonStatus) || "published".equals(lessonStatus));
            if (eligible) eligibleLessons.add(lesson);
        }

        Set<String> blockingCodes = new LinkedHashSet<String>();
        List<String> blockingReasons = new ArrayList<String>();
        if (chapters.isEmpty()) addReason(blockingCodes, blockingReasons, "NO_CHAPTER", "课程没有有效章节");
        if (eligibleLessons.isEmpty()) {
            addReason(blockingCodes, blockingReasons, "NO_PUBLISHED_LESSON", "课程没有可发布课时");
        }

        int playableVideoCount = 0;
        int invalidPublishedLessonCount = 0;
        int totalDurationSeconds = 0;
        for (Map<String, Object> lesson : eligibleLessons) {
            long lessonId = number(lesson.get("id"));
            String lessonType = text(lesson.get("lessonType"));
            totalDurationSeconds += (int) number(lesson.get("durationSeconds"));
            List<Map<String, Object>> lessonAssets = assetsByLesson.getOrDefault(
                    lessonId, new ArrayList<Map<String, Object>>());
            int matchingAssets = 0;
            int readyAssets = 0;
            for (Map<String, Object> asset : lessonAssets) {
                if (!lessonType.equals(text(asset.get("assetType")))) continue;
                matchingAssets++;
                if (isReadyAsset(lessonType, asset)) readyAssets++;
            }
            if ("video".equals(lessonType)) {
                playableVideoCount += readyAssets;
                if (matchingAssets == 0) {
                    invalidPublishedLessonCount++;
                    addReason(blockingCodes, blockingReasons, "VIDEO_LESSON_WITHOUT_PLAYABLE_ASSET",
                            "视频课时 " + lessonId + " 没有可播放视频");
                } else if (matchingAssets > 1) {
                    invalidPublishedLessonCount++;
                    addReason(blockingCodes, blockingReasons, "DUPLICATE_ACTIVE_VIDEO_ASSET",
                            "视频课时 " + lessonId + " 同时存在多个有效视频");
                } else if (readyAssets == 0) {
                    invalidPublishedLessonCount++;
                    addReason(blockingCodes, blockingReasons, "ASSET_NOT_READY",
                            "视频课时 " + lessonId + " 的视频尚未就绪");
                }
            } else if (readyAssets != 1) {
                invalidPublishedLessonCount++;
                addReason(blockingCodes, blockingReasons, "LESSON_WITHOUT_READY_ASSET",
                        "课时 " + lessonId + " 没有唯一可用的学习资源");
            }
        }
        if ("video".equals(text(resource.get("type"))) && playableVideoCount == 0) {
            addReason(blockingCodes, blockingReasons, "NO_PLAYABLE_VIDEO", "视频课程没有可播放视频");
        }

        boolean ownershipMissing = resource.get("teacherId") == null;
        List<String> reasonCodes = new ArrayList<String>(blockingCodes);
        List<String> reasons = new ArrayList<String>(blockingReasons);
        if (ownershipMissing) {
            reasonCodes.add("TEACHER_MISSING");
            reasons.add("课程教师待补充");
        }
        boolean contentComplete = blockingCodes.isEmpty();
        return new CourseContentValidationResult(
                publicCourseId, text(resource.get("title")), text(resource.get("type")),
                contentComplete, contentComplete && !ownershipMissing,
                chapters.size(), lessons.size(), eligibleLessons.size(), totalDurationSeconds, playableVideoCount,
                invalidPublishedLessonCount, ownershipMissing, (int) number(resource.get("status")),
                text(resource.get("reviewStatus")), reasonCodes, reasons);
    }

    private boolean isReadyAsset(String lessonType, Map<String, Object> asset) {
        if (!"ready".equals(text(asset.get("transcodingStatus")))) return false;
        if ("article".equals(lessonType) || "exercise".equals(lessonType)) {
            return !text(asset.get("contentText")).isEmpty();
        }
        return !text(asset.get("fileUrl")).isEmpty();
    }

    private void addReason(Set<String> codes, List<String> reasons, String code, String reason) {
        if (codes.add(code)) reasons.add(reason);
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : Long.valueOf(number(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private enum ValidationScope {
        PUBLIC,
        PUBLICATION_CANDIDATE
    }
}
