package com.learning.platform.service;

import com.learning.platform.common.PageResult;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.CourseContentValidationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {
    private final JdbcTemplate jdbcTemplate;
    private final CourseContentValidationService validationService;

    public CatalogService(JdbcTemplate jdbcTemplate, CourseContentValidationService validationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.validationService = validationService;
    }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select c.id,c.name,c.description,c.icon,c.sort_order sortOrder,"
                        + "count(distinct r.id) courseCount,count(distinct lr.student_id) learnerCount,"
                        + "coalesce(round(avg(case when lr.progress>=100 then 100 else lr.progress end),0),0) completionRate "
                        + "from categories c "
                        + "left join resources r on r.category_id=c.id and r.status=1 and r.review_status='approved' "
                        + "left join learning_records lr on lr.resource_id=r.id "
                        + "where c.status=1 group by c.id,c.name,c.description,c.icon,c.sort_order "
                        + "order by c.sort_order,c.id");
        Map<Long, List<Map<String, Object>>> directionsByCategory = enabledDirectionsByCategory();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> category = new LinkedHashMap<String, Object>(row);
            List<Map<String, Object>> categoryDirections = directionsByCategory.get(number(row.get("id")));
            category.put("directions", categoryDirections == null
                    ? new ArrayList<Map<String, Object>>() : categoryDirections);
            result.add(category);
        }
        return result;
    }

    private Map<Long, List<Map<String, Object>>> enabledDirectionsByCategory() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select d.id,d.category_id categoryId,d.name,d.description,d.sort_order sortOrder,"
                        + "count(distinct r.id) courseCount,count(distinct lr.student_id) learnerCount "
                        + "from directions d "
                        + "left join resources r on r.direction_id=d.id and r.status=1 and r.review_status='approved' "
                        + "left join learning_records lr on lr.resource_id=r.id "
                        + "where d.status=1 "
                        + "group by d.id,d.category_id,d.name,d.description,d.sort_order "
                        + "order by d.category_id,d.sort_order,d.id");
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<Long, List<Map<String, Object>>>();
        for (Map<String, Object> row : rows) {
            Long categoryId = number(row.get("categoryId"));
            grouped.computeIfAbsent(categoryId, key -> new ArrayList<Map<String, Object>>()).add(row);
        }
        return grouped;
    }

    public List<Map<String, Object>> directions(long categoryId) {
        return jdbcTemplate.queryForList(
                "select d.id,d.category_id categoryId,d.name,d.description,d.sort_order sortOrder,"
                        + "count(distinct r.id) courseCount,count(distinct lr.student_id) learnerCount "
                        + "from directions d "
                        + "left join resources r on r.direction_id=d.id and r.status=1 and r.review_status='approved' "
                        + "left join learning_records lr on lr.resource_id=r.id "
                        + "where d.category_id=? and d.status=1 "
                        + "group by d.id,d.category_id,d.name,d.description,d.sort_order "
                        + "order by d.sort_order,d.id",
                categoryId);
    }

    public PageResult<Map<String, Object>> courses(Long studentId,
                                                    String keyword,
                                                    Long categoryId,
                                                    Long directionId,
                                                    String type,
                                                    String difficulty,
                                                    int pageNum,
                                                    int pageSize) {
        int resolvedPage = Math.max(pageNum, 1);
        int resolvedSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (resolvedPage - 1) * resolvedSize;
        List<String> conditions = new ArrayList<String>();
        List<Object> filters = new ArrayList<Object>();
        conditions.add("r.status=1");
        conditions.add("r.review_status='approved'");
        if (categoryId != null) {
            conditions.add("r.category_id=?");
            filters.add(categoryId);
        }
        if (directionId != null) {
            conditions.add("r.direction_id=?");
            filters.add(directionId);
        }
        if (hasText(type)) {
            conditions.add("r.type=?");
            filters.add(type.trim());
        }
        if (hasText(difficulty)) {
            conditions.add("r.level=?");
            filters.add(difficulty.trim());
        }
        if (hasText(keyword)) {
            conditions.add("(r.title like ? or r.description like ? or r.subject like ? or c.name like ? or d.name like ?)");
            String like = "%" + keyword.trim() + "%";
            for (int index = 0; index < 5; index++) {
                filters.add(like);
            }
        }
        String where = " where " + String.join(" and ", conditions);
        String from = " from resources r "
                + "left join categories c on c.id=r.category_id "
                + "left join directions d on d.id=r.direction_id "
                + "left join users u on u.id=r.teacher_id ";
        String personalFields = studentId == null
                ? "false favorited,0 progress,"
                : "exists(select 1 from resource_favorites rf where rf.resource_id=r.id and rf.student_id=?) favorited,"
                + lessonProgressField();
        List<Object> params = new ArrayList<Object>();
        if (studentId != null) {
            params.add(studentId);
            params.add(studentId);
        }
        params.addAll(filters);
        String sql = "select r.id,r.title,r.subject,r.type,r.level difficulty,r.level,"
                + "r.description,"
                + "coalesce(c.name,r.subject) categoryName,coalesce(d.name,r.subject) directionName,"
                + "coalesce(u.name,'平台教师') teacher,coalesce(u.name,'平台教师') teacherName,"
                + "coalesce((select count(*) from resource_favorites rf where rf.resource_id=r.id),0) favoriteCount,"
                + learnerCountField()
                + personalFields
                + "r.created_at createdAt,r.updated_at updatedAt" + from + where
                + " order by learnerCount desc,r.created_at desc,r.id desc";
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(sql, params.toArray());
        List<Map<String, Object>> visible = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> candidate : candidates) {
            CourseContentValidationResult validation = validationService.validatePublic(number(candidate.get("id")));
            if (!validation.isContentComplete()) continue;
            Map<String, Object> card = new LinkedHashMap<String, Object>(candidate);
            card.put("chapterCount", validation.getChapterCount());
            card.put("lessonCount", validation.getPublishedLessonCount());
            card.put("totalDurationSeconds", validation.getTotalDurationSeconds());
            card.put("minutes", (validation.getTotalDurationSeconds() + 59) / 60);
            card.put("estimatedHours", Math.round(validation.getTotalDurationSeconds() / 360.0) / 10.0);
            card.put("hasPlayableVideo", validation.getPlayableVideoCount() > 0);
            visible.add(card);
        }
        long total = visible.size();
        if (offset >= visible.size()) {
            return new PageResult<Map<String, Object>>(new ArrayList<Map<String, Object>>(), total,
                    resolvedPage, resolvedSize);
        }
        int end = Math.min(offset + resolvedSize, visible.size());
        return new PageResult<Map<String, Object>>(new ArrayList<Map<String, Object>>(visible.subList(offset, end)),
                total, resolvedPage, resolvedSize);
    }

    public Map<String, Object> courseDetail(Map<String, Object> viewer, Long studentId, long courseId) {
        String personalFields = studentId == null
                ? "false favorited,0 progress,"
                : "exists(select 1 from resource_favorites rf where rf.resource_id=r.id and rf.student_id=?) favorited,"
                + lessonProgressField();
        List<Object> params = new ArrayList<Object>();
        if (studentId != null) {
            params.add(studentId);
            params.add(studentId);
        }
        params.add(courseId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id,r.title,r.subject,r.type,r.level difficulty,r.level,r.description,r.content,"
                        + "r.teacher_id teacherId,r.status,r.review_status reviewStatus,"
                        + "r.estimated_minutes minutes,round(r.estimated_minutes/60.0,1) estimatedHours,"
                        + "coalesce(c.name,r.subject) categoryName,coalesce(d.name,r.subject) directionName,"
                        + "coalesce(u.name,'平台教师') teacher,"
                        + "coalesce((select count(*) from resource_favorites rf where rf.resource_id=r.id),0) favoriteCount,"
                        + learnerCountField()
                        + personalFields
                        + "r.created_at createdAt from resources r "
                        + "left join categories c on c.id=r.category_id "
                        + "left join directions d on d.id=r.direction_id "
                        + "left join users u on u.id=r.teacher_id "
                        + "where r.id=?",
                params.toArray());
        if (rows.isEmpty()) {
            throw new BusinessException(404, "课程不存在或已下架");
        }
        Map<String, Object> matched = new LinkedHashMap<String, Object>(rows.get(0));
        boolean privileged = viewer != null && ("admin".equals(viewer.get("role"))
                || ("teacher".equals(viewer.get("role")) && matched.get("teacherId") != null
                && number(viewer.get("id")) == number(matched.get("teacherId"))));
        CourseContentValidationResult validation = validationService.validatePublic(courseId);
        boolean publicCourse = number(matched.get("status")) == 1L
                && "approved".equals(String.valueOf(matched.get("reviewStatus")))
                && validation.isContentComplete();
        if (!publicCourse && !privileged) {
            throw new BusinessException(404, "课程不存在或已下架");
        }
        matched.remove("teacherId");
        matched.remove("status");
        matched.remove("reviewStatus");
        matched.put("chapterCount", validation.getChapterCount());
        matched.put("lessonCount", validation.getPublishedLessonCount());
        matched.put("totalDurationSeconds", validation.getTotalDurationSeconds());
        matched.put("hasPlayableVideo", validation.getPlayableVideoCount() > 0);
        String direction = String.valueOf(matched.get("directionName"));
        matched.put("knowledgeNodes", jdbcTemplate.queryForList(
                "select id,title name,node_level level,description,sort_order sortOrder "
                        + "from knowledge_nodes where subject=? order by sort_order,id",
                direction));
        return matched;
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String lessonProgressField() {
        return "coalesce((select round(avg(coalesce(lp.progress_percent,0))) from courses pc "
                + "join course_chapters pch on pch.course_id=pc.id and pch.status='published' "
                + "join course_lessons pl on pl.chapter_id=pch.id and pl.status='published' "
                + "left join lesson_progress lp on lp.lesson_id=pl.id and lp.user_id=? "
                + "where pc.legacy_resource_id=r.id),0) progress,";
    }

    private String learnerCountField() {
        return "greatest(coalesce((select count(distinct lr.student_id) from learning_records lr "
                + "where lr.resource_id=r.id),0),coalesce((select count(distinct lp.user_id) "
                + "from courses pc join course_chapters pch on pch.course_id=pc.id "
                + "join course_lessons pl on pl.chapter_id=pch.id "
                + "join lesson_progress lp on lp.lesson_id=pl.id where pc.legacy_resource_id=r.id),0),"
                + "coalesce(r.learner_count,0)) learnerCount,";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
