package com.learning.platform.ai.config;

import com.learning.platform.ai.prompt.TutorPromptService;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@DependsOn("schemaInitializer")
public class AiSchemaInitializer {
    private final JdbcTemplate jdbcTemplate;

    public AiSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("create table if not exists student_learning_paths ("
                + "id bigint primary key auto_increment,path_id varchar(64) not null unique,"
                + "user_id bigint not null,snapshot_json longtext not null,neo4j_synced tinyint not null default 0,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_student_paths_user(user_id,id),"
                + "constraint fk_student_paths_user foreign key(user_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");
        jdbcTemplate.execute("create table if not exists ai_conversation ("
                + "id bigint primary key auto_increment,"
                + "user_id bigint not null,"
                + "course_id bigint not null,"
                + "lesson_id bigint not null,"
                + "title varchar(160) not null,"
                + "status enum('active','deleted') not null default 'active',"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_ai_conversation_user_updated(user_id,status,updated_at),"
                + "index idx_ai_conversation_lesson(user_id,course_id,lesson_id),"
                + "constraint fk_ai_conversation_user foreign key(user_id) references users(id) on delete cascade,"
                + "constraint fk_ai_conversation_course foreign key(course_id) references resources(id) on delete restrict,"
                + "constraint fk_ai_conversation_lesson foreign key(lesson_id) references course_lessons(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists ai_message ("
                + "id bigint primary key auto_increment,"
                + "conversation_id bigint not null,"
                + "role enum('USER','ASSISTANT','SYSTEM_SUMMARY') not null,"
                + "content mediumtext not null,"
                + "provider_code varchar(40) null,"
                + "model_code varchar(120) null,"
                + "request_id varchar(64) null,"
                + "prompt_version varchar(40) null,"
                + "status enum('queued','generating','completed','failed','cancelled') not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_ai_message_request_role(request_id,role),"
                + "index idx_ai_message_conversation_created(conversation_id,created_at),"
                + "constraint fk_ai_message_conversation foreign key(conversation_id) references ai_conversation(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists ai_usage_log ("
                + "id bigint primary key auto_increment,"
                + "request_id varchar(64) not null unique,"
                + "user_id bigint not null,"
                + "course_id bigint not null,"
                + "lesson_id bigint not null,"
                + "provider_code varchar(40) not null,"
                + "model_code varchar(120) not null,"
                + "prompt_eval_count int not null default 0,"
                + "eval_count int not null default 0,"
                + "load_duration_ms bigint not null default 0,"
                + "total_duration_ms bigint not null default 0,"
                + "tokens_per_second decimal(10,2) not null default 0,"
                + "neo4j_used tinyint not null default 0,"
                + "graph_node_count int not null default 0,"
                + "status enum('completed','failed','cancelled') not null,"
                + "error_code varchar(60) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_ai_usage_user_created(user_id,created_at),"
                + "index idx_ai_usage_status_created(status,created_at)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists ai_message_citation ("
                + "id bigint primary key auto_increment,"
                + "message_id bigint not null,"
                + "source_type enum('COURSE','LESSON','KNOWLEDGE_NODE') not null,"
                + "source_id varchar(80) not null,"
                + "title varchar(200) not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_ai_citation_message_source(message_id,source_type,source_id),"
                + "constraint fk_ai_citation_message foreign key(message_id) references ai_message(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists ai_feedback ("
                + "id bigint primary key auto_increment,"
                + "user_id bigint not null,"
                + "message_id bigint not null,"
                + "rating tinyint not null,"
                + "reason varchar(500) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_ai_feedback_user_message(user_id,message_id),"
                + "constraint fk_ai_feedback_user foreign key(user_id) references users(id) on delete cascade,"
                + "constraint fk_ai_feedback_message foreign key(message_id) references ai_message(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists ai_prompt_template ("
                + "id bigint primary key auto_increment,"
                + "scene_code varchar(60) not null,"
                + "version varchar(40) not null,"
                + "content text not null,"
                + "enabled tinyint not null default 1,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_ai_prompt_scene_version(scene_code,version)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");
        jdbcTemplate.update("insert ignore into ai_prompt_template(scene_code,version,content,enabled) values(?,?,?,1)",
                TutorPromptService.SCENE_CODE, TutorPromptService.VERSION, TutorPromptService.DEFAULT_SYSTEM_PROMPT);
    }
}
