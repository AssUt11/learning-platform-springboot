package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class KnowledgeProgressController {
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;

    public KnowledgeProgressController(JdbcTemplate jdbc, CurrentUserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @GetMapping("/knowledge-progress")
    public Result<List<Map<String, Object>>> progress(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Object userId = users.requireLearner(authorization).get("id");
        // Only actual lesson progress counts; historical graph clicks/manual marks are not evidence of study.
        return Result.success(jdbc.queryForList("""
                select subject,knowledgeNode,max(progress) progress from (
                  select coalesce(d.name,r.subject) subject,l.title knowledgeNode,
                    case when l.lesson_type='video' then
                      case when coalesce(nullif(l.duration_seconds,0),p.duration_seconds,0)>0
                          and p.last_position_seconds>0 then
                        greatest(1,least(100,floor(p.last_position_seconds*100.0 /
                          coalesce(nullif(l.duration_seconds,0),p.duration_seconds))))
                        else 0 end
                      when p.completed=1 and p.progress_percent>=100 then 100
                      else greatest(0,least(99,coalesce(p.progress_percent,0))) end progress
                  from lesson_progress p
                  join course_lessons l on l.id=p.lesson_id
                  join course_chapters ch on ch.id=l.chapter_id
                  join courses c on c.id=ch.course_id
                  join resources r on r.id=c.legacy_resource_id
                  left join directions d on d.id=r.direction_id
                  where p.user_id=? and l.status='published' and ch.status='published'
                    and r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
                ) points group by subject,knowledgeNode
                """, userId));
    }
}
