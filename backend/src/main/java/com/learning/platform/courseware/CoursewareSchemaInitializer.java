package com.learning.platform.courseware;

import javax.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaInitializer")
public class CoursewareSchemaInitializer {
    private final JdbcTemplate jdbc;

    public CoursewareSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void initialize() {
        jdbc.execute("""
            create table if not exists courseware_documents (
              id varchar(36) primary key,asset_id bigint not null,public_course_id bigint not null,
              lesson_id bigint not null,teacher_id bigint not null,file_name varchar(255) not null,
              file_url varchar(500) not null,asset_version char(64) not null,version_hash char(64) null,
              status varchar(20) not null default 'QUEUED',error_message varchar(500) null,
              segment_count int not null default 0,character_count int not null default 0,
              attempt_count int not null default 0,lease_until timestamp null,worker_token varchar(36) null,
              created_at timestamp not null default current_timestamp,
              updated_at timestamp not null default current_timestamp on update current_timestamp,
              confirmed_at timestamp null,
              unique key uk_courseware_asset_version(asset_id,asset_version),
              index idx_courseware_queue(status,created_at),
              index idx_courseware_lesson(lesson_id,status),
              constraint fk_courseware_asset foreign key(asset_id) references lesson_assets(id) on delete restrict
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
        jdbc.execute("""
            create table if not exists courseware_chunks (
              id bigint primary key auto_increment,document_id varchar(36) not null,
              chunk_index int not null,location_type varchar(16) not null,location_index int not null,
              text_content text not null,
              unique key uk_courseware_chunk(document_id,chunk_index),
              constraint fk_courseware_chunk_document foreign key(document_id)
                references courseware_documents(id) on delete cascade
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
    }
}
