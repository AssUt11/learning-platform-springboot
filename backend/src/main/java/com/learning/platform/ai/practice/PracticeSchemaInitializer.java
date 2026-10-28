package com.learning.platform.ai.practice;

import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

/** 练习独立存储，不把答题成绩当作视频进度，也不改写教师课程内容。 */
@Component
@DependsOn("schemaInitializer")
public class PracticeSchemaInitializer {
    private final JdbcTemplate jdbc;
    public PracticeSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void initialize() {
        jdbc.execute("""
            create table if not exists ai_practice_sessions (
              id bigint primary key auto_increment,
              quiz_id varchar(36) not null unique,
              user_id bigint not null,
              course_id bigint not null,
              lesson_id bigint not null,
              difficulty varchar(20) not null,
              questions_json mediumtext not null,
              answers_json text null,
              score int null,
              provider_code varchar(40) not null,
              model_code varchar(160) not null,
              created_at timestamp(3) not null default current_timestamp(3),
              submitted_at timestamp(3) null,
              index idx_practice_user_lesson(user_id,lesson_id,id),
              constraint fk_practice_user foreign key(user_id) references users(id) on delete cascade,
              constraint fk_practice_course foreign key(course_id) references resources(id) on delete restrict,
              constraint fk_practice_lesson foreign key(lesson_id) references course_lessons(id) on delete restrict
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
    }
}
