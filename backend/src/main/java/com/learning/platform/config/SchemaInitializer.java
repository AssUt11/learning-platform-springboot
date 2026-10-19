package com.learning.platform.config;

import com.learning.platform.service.LegacyCourseMigrationService;
import com.learning.platform.service.DemoCourseInitializerService;
import com.learning.platform.service.FoundationCourseInitializerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.SQLException;

@Component
public class SchemaInitializer {
    private final JdbcTemplate jdbcTemplate;
    private final LegacyCourseMigrationService legacyCourseMigrationService;
    private final DemoCourseInitializerService demoCourseInitializerService;
    private final FoundationCourseInitializerService foundationCourses;
    private final String adminEmail;
    private final String adminPassword;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SchemaInitializer(JdbcTemplate jdbcTemplate,
                             LegacyCourseMigrationService legacyCourseMigrationService,
                             DemoCourseInitializerService demoCourseInitializerService,
                             FoundationCourseInitializerService foundationCourses,
                             @Value("${app.auth.admin-email}") String adminEmail,
                             @Value("${app.auth.admin-password}") String adminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.legacyCourseMigrationService = legacyCourseMigrationService;
        this.demoCourseInitializerService = demoCourseInitializerService;
        this.foundationCourses = foundationCourses;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @PostConstruct
    public void init() {
        createTables();
        seedAdminAccount();
        seedCatalog();
        seedStructuredCatalog();
        seedCourses();
        seedResources();
        backfillResourceContent();
        backfillStructuredCatalog();
        legacyCourseMigrationService.migrateAll();
        demoCourseInitializerService.ensureDemoCourse();
        foundationCourses.ensureCourses();
    }

    private void createTables() {
        jdbcTemplate.execute("create table if not exists users ("
                + "id bigint primary key auto_increment,"
                + "name varchar(80) not null,"
                + "email varchar(120) not null unique,"
                + "password_hash varchar(255) not null,"
                + "role enum('admin','teacher','student') not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_users_role(role)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists student_preferences ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "subject varchar(60) not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_student_subject(student_id, subject),"
                + "constraint fk_pref_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists subject_categories ("
                + "id bigint primary key auto_increment,"
                + "name varchar(60) not null unique,"
                + "description text null,"
                + "created_at timestamp not null default current_timestamp"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists categories ("
                + "id bigint primary key auto_increment,"
                + "name varchar(100) not null unique,"
                + "description varchar(500) null,"
                + "icon varchar(100) null,"
                + "sort_order int not null default 0,"
                + "status tinyint not null default 1,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_categories_status_sort(status,sort_order)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists directions ("
                + "id bigint primary key auto_increment,"
                + "category_id bigint not null,"
                + "name varchar(100) not null,"
                + "description varchar(500) null,"
                + "sort_order int not null default 0,"
                + "status tinyint not null default 1,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_direction_category_name(category_id,name),"
                + "index idx_directions_category_status(category_id,status,sort_order),"
                + "constraint fk_directions_category foreign key(category_id) references categories(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists courses ("
                + "id bigint primary key auto_increment,"
                + "legacy_resource_id bigint null,"
                + "teacher_id bigint null,"
                + "title varchar(160) not null,"
                + "subject varchar(60) not null,"
                + "description text null,"
                + "cover_url varchar(255) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_courses_legacy_resource(legacy_resource_id),"
                + "index idx_courses_subject(subject),"
                + "constraint fk_courses_teacher foreign key(teacher_id) references users(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists resources ("
                + "id bigint primary key auto_increment,"
                + "teacher_id bigint null,"
                + "course_id bigint null,"
                + "title varchar(160) not null,"
                + "subject varchar(60) not null,"
                + "type enum('video','material') not null default 'material',"
                + "level varchar(40) not null default '入门',"
                + "description text null,"
                + "content text null,"
                + "file_url varchar(255) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_resources_subject(subject),"
                + "index idx_resources_type(type),"
                + "constraint fk_resources_teacher foreign key(teacher_id) references users(id) on delete set null,"
                + "constraint fk_resources_course foreign key(course_id) references courses(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_chapters ("
                + "id bigint primary key auto_increment,"
                + "course_id bigint not null,"
                + "title varchar(160) not null,"
                + "description varchar(500) null,"
                + "sort_order int not null default 1,"
                + "status enum('draft','published','disabled') not null default 'draft',"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_course_chapters_course_sort(course_id,sort_order),"
                + "constraint fk_course_chapters_course foreign key(course_id) references courses(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_lessons ("
                + "id bigint primary key auto_increment,"
                + "chapter_id bigint not null,"
                + "title varchar(180) not null,"
                + "lesson_type enum('video','article','document','exercise') not null,"
                + "duration_seconds int not null default 0,"
                + "free_preview tinyint not null default 0,"
                + "sort_order int not null default 1,"
                + "status enum('draft','ready','published','disabled') not null default 'draft',"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_course_lessons_chapter_sort(chapter_id,sort_order),"
                + "constraint fk_course_lessons_chapter foreign key(chapter_id) references course_chapters(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists lesson_assets ("
                + "id bigint primary key auto_increment,"
                + "lesson_id bigint not null,"
                + "asset_type enum('video','article','document','exercise') not null,"
                + "file_url varchar(255) null,"
                + "file_name varchar(255) null,"
                + "file_size bigint null,"
                + "mime_type varchar(120) null,"
                + "cover_url varchar(255) null,"
                + "content_text mediumtext null,"
                + "transcoding_status enum('pending','processing','ready','failed') not null default 'pending',"
                + "status tinyint not null default 1,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_lesson_assets_lesson(lesson_id),"
                + "index idx_lesson_assets_lesson_status(lesson_id,status,transcoding_status),"
                + "constraint fk_lesson_assets_lesson foreign key(lesson_id) references course_lessons(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_legacy_migration ("
                + "resource_id bigint primary key,"
                + "previous_course_id bigint null,"
                + "course_id bigint not null,"
                + "chapter_id bigint not null,"
                + "lesson_id bigint not null,"
                + "asset_id bigint null,"
                + "migrated_at timestamp not null default current_timestamp,"
                + "unique key uk_course_legacy_migration_course(course_id),"
                + "index idx_course_legacy_migration_lesson(lesson_id),"
                + "constraint fk_course_legacy_resource foreign key(resource_id) references resources(id) on delete restrict,"
                + "constraint fk_course_legacy_course foreign key(course_id) references courses(id) on delete restrict,"
                + "constraint fk_course_legacy_chapter foreign key(chapter_id) references course_chapters(id) on delete restrict,"
                + "constraint fk_course_legacy_lesson foreign key(lesson_id) references course_lessons(id) on delete restrict,"
                + "constraint fk_course_legacy_asset foreign key(asset_id) references lesson_assets(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_demo_seeds ("
                + "seed_key varchar(100) primary key,"
                + "resource_id bigint not null,"
                + "teacher_id bigint not null,"
                + "chapter_a_id bigint not null,"
                + "chapter_b_id bigint not null,"
                + "lesson_a_id bigint not null,"
                + "lesson_b_id bigint not null,"
                + "asset_a_id bigint not null,"
                + "asset_b_id bigint not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_course_demo_resource(resource_id),"
                + "constraint fk_course_demo_resource foreign key(resource_id) references resources(id) on delete restrict,"
                + "constraint fk_course_demo_teacher foreign key(teacher_id) references users(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists knowledge_nodes ("
                + "id bigint primary key auto_increment,"
                + "subject varchar(60) not null,"
                + "title varchar(120) not null,"
                + "node_level tinyint not null default 1,"
                + "description text null,"
                + "sort_order int not null default 0,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_subject_title(subject, title),"
                + "index idx_knowledge_subject(subject)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists knowledge_prerequisites ("
                + "id bigint primary key auto_increment,"
                + "subject varchar(60) not null,"
                + "prerequisite_key varchar(220) not null,"
                + "prerequisite_title varchar(120) not null,"
                + "target_key varchar(220) not null,"
                + "target_title varchar(120) not null,"
                + "source_reference varchar(500) not null,"
                + "sync_status enum('pending','synced','failed') not null default 'pending',"
                + "sync_error varchar(500) null,"
                + "created_by bigint null,"
                + "updated_by bigint null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_knowledge_prerequisite(subject,prerequisite_key,target_key),"
                + "index idx_knowledge_prerequisite_target(subject,target_key),"
                + "index idx_knowledge_prerequisite_sync(sync_status,updated_at),"
                + "constraint fk_knowledge_prerequisite_creator foreign key(created_by) references users(id) on delete set null,"
                + "constraint fk_knowledge_prerequisite_updater foreign key(updated_by) references users(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");
        seedKnowledgePrerequisites();

        jdbcTemplate.execute("create table if not exists learning_records ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "resource_id bigint null,"
                + "record_key varchar(220) null,"
                + "subject varchar(60) null,"
                + "knowledge_node varchar(120) null,"
                + "progress tinyint not null default 0,"
                + "duration_seconds int not null default 0,"
                + "score decimal(5,2) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_records_student(student_id),"
                + "index idx_records_subject(subject),"
                + "index idx_records_student_created(student_id,created_at),"
                + "index idx_records_resource_student(resource_id,student_id),"
                + "unique key uk_student_record_key(student_id, record_key),"
                + "constraint fk_records_student foreign key(student_id) references users(id) on delete cascade,"
                + "constraint fk_records_resource foreign key(resource_id) references resources(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists lesson_progress ("
                + "id bigint primary key auto_increment,"
                + "user_id bigint not null,"
                + "lesson_id bigint not null,"
                + "last_position_seconds int not null default 0,"
                + "duration_seconds int not null default 0,"
                + "progress_percent tinyint not null default 0,"
                + "completed tinyint not null default 0,"
                + "last_studied_at timestamp not null default current_timestamp on update current_timestamp,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_lesson_progress_user_lesson(user_id,lesson_id),"
                + "index idx_lesson_progress_user_recent(user_id,last_studied_at),"
                + "index idx_lesson_progress_lesson(lesson_id),"
                + "constraint fk_lesson_progress_user foreign key(user_id) references users(id) on delete cascade,"
                + "constraint fk_lesson_progress_lesson foreign key(lesson_id) references course_lessons(id) on delete restrict"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists resource_favorites ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "resource_id bigint not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_student_resource(student_id, resource_id),"
                + "index idx_favorites_resource(resource_id),"
                + "constraint fk_favorites_student foreign key(student_id) references users(id) on delete cascade,"
                + "constraint fk_favorites_resource foreign key(resource_id) references resources(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_questions ("
                + "id bigint primary key auto_increment,"
                + "resource_id bigint not null,"
                + "student_id bigint not null,"
                + "content varchar(500) not null,"
                + "answer_count int not null default 0,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_course_questions_resource_created(resource_id,created_at),"
                + "constraint fk_questions_resource foreign key(resource_id) references resources(id) on delete cascade,"
                + "constraint fk_questions_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_notes ("
                + "id bigint primary key auto_increment,"
                + "resource_id bigint not null,"
                + "student_id bigint not null,"
                + "lesson_title varchar(180) not null default '课程总览',"
                + "content text not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_course_notes_student_resource(student_id,resource_id,updated_at),"
                + "constraint fk_notes_resource foreign key(resource_id) references resources(id) on delete cascade,"
                + "constraint fk_notes_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_reviews ("
                + "id bigint primary key auto_increment,"
                + "resource_id bigint not null,"
                + "student_id bigint not null,"
                + "rating tinyint not null,"
                + "content varchar(1000) not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "unique key uk_course_review_student(resource_id,student_id),"
                + "index idx_course_reviews_resource_updated(resource_id,updated_at),"
                + "constraint fk_reviews_resource foreign key(resource_id) references resources(id) on delete cascade,"
                + "constraint fk_reviews_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists course_feedback_replies ("
                + "id bigint primary key auto_increment,"
                + "target_type enum('question','review') not null,"
                + "target_id bigint not null,"
                + "teacher_id bigint not null,"
                + "content varchar(1000) not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_feedback_replies_target(target_type,target_id,created_at),"
                + "constraint fk_feedback_reply_teacher foreign key(teacher_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists direct_messages ("
                + "id bigint primary key auto_increment,"
                + "sender_id bigint not null,"
                + "recipient_id bigint not null,"
                + "content varchar(2000) not null,"
                + "is_read tinyint not null default 0,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_messages_conversation(sender_id,recipient_id,created_at),"
                + "index idx_messages_recipient_read(recipient_id,is_read,created_at),"
                + "constraint fk_messages_sender foreign key(sender_id) references users(id) on delete cascade,"
                + "constraint fk_messages_recipient foreign key(recipient_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists admin_audit_logs ("
                + "id bigint primary key auto_increment,"
                + "admin_id bigint not null,"
                + "action_type varchar(80) not null,"
                + "target_type varchar(80) not null,"
                + "target_id bigint null,"
                + "detail varchar(500) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_admin_audit_created(created_at),"
                + "constraint fk_admin_audit_user foreign key(admin_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists agent_logs ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "agent_type enum('guide','analysis','path','recommend') not null,"
                + "input_summary text null,"
                + "output_result text null,"
                + "report_url varchar(255) null,"
                + "request_id varchar(64) null,"
                + "provider_code varchar(40) null,"
                + "model_code varchar(120) null,"
                + "prompt_version varchar(40) null,"
                + "tools_used text null,"
                + "sources_used text null,"
                + "graph_used tinyint not null default 0,"
                + "status enum('running','completed','failed') not null default 'completed',"
                + "duration_ms bigint null,"
                + "error_code varchar(60) null,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_agent_student(student_id),"
                + "index idx_agent_student_type_created(student_id,agent_type,created_at),"
                + "unique key uk_agent_request(request_id),"
                + "constraint fk_agent_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        safeExecute("alter table learning_records add column record_key varchar(220) null after resource_id");
        safeExecute("alter table learning_records add unique key uk_student_record_key(student_id, record_key)");
        safeExecute("alter table courses add column legacy_resource_id bigint null after id");
        safeExecute("alter table courses add unique key uk_courses_legacy_resource(legacy_resource_id)");
        safeExecute("alter table course_legacy_migration add column previous_course_id bigint null after resource_id");
        safeExecute("alter table resources add column content text null after description");
        safeExecute("alter table resources add column category_id bigint null after course_id");
        safeExecute("alter table resources add column direction_id bigint null after category_id");
        safeExecute("alter table resources add column estimated_minutes int not null default 45 after level");
        safeExecute("alter table resources add column learner_count int not null default 0 after estimated_minutes");
        safeExecute("alter table resources add column status tinyint not null default 1 after learner_count");
        safeExecute("alter table resources add column review_status enum('pending','approved','rejected') not null default 'approved' after status");
        safeExecute("alter table resources add column review_feedback varchar(500) null after review_status");
        safeExecute("alter table resources add column reviewed_by bigint null after review_feedback");
        safeExecute("alter table resources add column reviewed_at timestamp null after reviewed_by");
        safeExecute("alter table resources add column lifecycle_status enum('active','archived') not null default 'active' after reviewed_at");
        safeExecute("alter table resources add column archived_at timestamp null after lifecycle_status");
        safeExecute("alter table resources add column archive_reason varchar(500) null after archived_at");
        safeExecute("alter table users modify column role enum('admin','teacher','student') not null");
        safeExecute("alter table resources add index idx_resources_category(category_id)");
        safeExecute("alter table resources add index idx_resources_direction(direction_id)");
        safeExecute("alter table resources add index idx_resources_level(level)");
        safeExecute("alter table resources add index idx_resources_catalog_sort(status,category_id,direction_id,created_at)");
        safeExecute("alter table resources add index idx_resources_review(review_status,status,created_at)");
        safeExecute("alter table resources add index idx_resources_lifecycle(lifecycle_status,status,updated_at)");
        safeExecute("alter table learning_records add index idx_records_student_created(student_id,created_at)");
        safeExecute("alter table learning_records add index idx_records_resource_student(resource_id,student_id)");
        safeExecute("alter table agent_logs add index idx_agent_student_type_created(student_id,agent_type,created_at)");
        safeExecute("alter table agent_logs add column request_id varchar(64) null after report_url");
        safeExecute("alter table agent_logs add column provider_code varchar(40) null after request_id");
        safeExecute("alter table agent_logs add column model_code varchar(120) null after provider_code");
        safeExecute("alter table agent_logs add column prompt_version varchar(40) null after model_code");
        safeExecute("alter table agent_logs add column tools_used text null after prompt_version");
        safeExecute("alter table agent_logs add column sources_used text null after tools_used");
        safeExecute("alter table agent_logs add column graph_used tinyint not null default 0 after sources_used");
        safeExecute("alter table agent_logs add column status enum('running','completed','failed') not null default 'completed' after graph_used");
        safeExecute("alter table agent_logs add column duration_ms bigint null after status");
        safeExecute("alter table agent_logs add column error_code varchar(60) null after duration_ms");
        safeExecute("alter table agent_logs add column updated_at timestamp not null default current_timestamp on update current_timestamp after error_code");
        safeExecute("alter table agent_logs add unique key uk_agent_request(request_id)");
    }

    private void seedAdminAccount() {
        if (adminEmail.isEmpty() || adminPassword.length() < 8) {
            throw new IllegalStateException("Admin email and password must be configured, and the password must contain at least 8 characters.");
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from users where email=?", Integer.class, adminEmail);
        if (count != null && count > 0) {
            jdbcTemplate.update("update users set role='admin' where email=?", adminEmail);
            return;
        }
        jdbcTemplate.update("insert into users(name,email,password_hash,role) values(?,?,?,'admin')",
                "平台管理员", adminEmail, passwordEncoder.encode(adminPassword));
    }

    private void seedKnowledgePrerequisites() {
        String subject = "Java";
        String source = "知度课堂默认 Java 学习路径 v1";
        Object[][] relations = new Object[][]{
                {"software_engineering/java/java基础", "Java基础", "software_engineering/java/面向对象", "面向对象"},
                {"software_engineering/java/面向对象", "面向对象", "software_engineering/java/spring", "Spring"},
                {"software_engineering/java/spring", "Spring", "software_engineering/java/spring_mvc", "Spring MVC"},
                {"software_engineering/java/spring_mvc", "Spring MVC", "software_engineering/java/spring_boot", "Spring Boot"}
        };
        for (Object[] relation : relations) {
            jdbcTemplate.update("insert ignore into knowledge_prerequisites("
                            + "subject,prerequisite_key,prerequisite_title,target_key,target_title,source_reference) "
                            + "values(?,?,?,?,?,?)",
                    subject, relation[0], relation[1], relation[2], relation[3], source);
        }
    }

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException exception) {
            if (isDuplicateColumnOrIndex(exception)) {
                return;
            }
            throw new IllegalStateException("Database migration failed: " + sql, exception);
        }
    }

    private boolean isDuplicateColumnOrIndex(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException) {
                int errorCode = ((SQLException) cause).getErrorCode();
                if (errorCode == 1060 || errorCode == 1061) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void seedCatalog() {
        seedCategory("Java", "Java 后端开发、Spring Boot 与企业项目基础");
        seedCategory("C#", "C# 桌面应用、Web 开发与 .NET 生态");
        seedCategory("Python", "Python 基础、数据分析、爬虫与轻量 Web 开发");
        seedCategory("数据库", "关系型数据库、缓存、SQL 优化与事务设计");
        seedCategory("前端开发", "HTML/CSS、JavaScript、React/Vue 与前端工程化");
        seedCategory("人工智能", "机器学习、深度学习、NLP 与推荐系统");
        seedCategory("软件工程", "需求分析、建模、测试、敏捷与项目协作");
        seedCategory("网络安全", "Web 安全、密码学、渗透测试与安全运维");
        seedCategory("移动开发", "Android、iOS、小程序与跨端应用开发");
        seedCategory("大数据", "Hadoop、Spark、Hive 与实时计算");
        seedCategory("云计算", "Docker、Kubernetes、云原生部署与 DevOps");
        seedCategory("物联网", "单片机、传感器、MQTT 与边缘计算");

        seedNodes("Java", "Java基础", "面向对象", "集合框架", "Spring Boot");
        seedNodes("C#", "C#基础", "WinForms", "ASP.NET Core", "Entity Framework");
        seedNodes("Python", "Python基础", "数据分析", "网络爬虫", "Flask");
        seedNodes("数据库", "MySQL", "Redis", "SQL优化", "数据库事务");
        seedNodes("前端开发", "HTML/CSS", "JavaScript", "React", "Vue");
        seedNodes("人工智能", "机器学习", "深度学习", "自然语言处理", "推荐系统");
        seedNodes("软件工程", "需求分析", "UML建模", "软件测试", "敏捷开发");
        seedNodes("网络安全", "Web安全", "密码学基础", "渗透测试", "安全运维");
        seedNodes("移动开发", "Android开发", "iOS开发", "微信小程序", "Flutter");
        seedNodes("大数据", "Hadoop", "Spark", "Hive", "实时计算");
        seedNodes("云计算", "Docker容器", "Kubernetes", "云原生部署", "DevOps");
        seedNodes("物联网", "单片机基础", "传感器应用", "MQTT通信", "边缘计算");
    }

    private void seedStructuredCatalog() {
        seedLearningCategory("软件工程", "面向软件开发、测试、协作和交付的综合学习大类", 1,
                "Java", "Python", "C#", "前端开发", "数据库", "软件测试");
        seedLearningCategory("人工智能", "从机器学习、深度学习到自然语言处理和视觉任务", 2,
                "机器学习", "深度学习", "自然语言处理", "计算机视觉");
        seedLearningCategory("大数据", "覆盖数据采集、存储、计算、建模和可视化", 3,
                "Hadoop", "Spark", "数据仓库", "数据可视化");
        seedLearningCategory("云计算", "面向 Linux、容器、集群编排与云原生服务治理", 4,
                "Linux", "Docker", "Kubernetes", "微服务");
        seedLearningCategory("网络安全", "覆盖 Web 安全、密码学、协议安全和安全运维", 5,
                "Web安全", "密码学", "网络协议安全", "安全运维");
        seedLearningCategory("移动与物联网", "面向移动应用、设备通信、边缘计算和嵌入式开发", 6,
                "移动开发", "物联网", "嵌入式开发");
    }

    private void seedLearningCategory(String name, String description, int sortOrder, String... directions) {
        jdbcTemplate.update("insert into categories(name,description,sort_order,status) values(?,?,?,1) "
                        + "on duplicate key update description=values(description),sort_order=values(sort_order),status=1",
                name, description, sortOrder);
        Long categoryId = jdbcTemplate.queryForObject("select id from categories where name=?", Long.class, name);
        for (int index = 0; index < directions.length; index++) {
            jdbcTemplate.update("insert into directions(category_id,name,sort_order,status) values(?,?,?,1) "
                            + "on duplicate key update sort_order=values(sort_order),status=1",
                    categoryId, directions[index], index + 1);
        }
    }

    private void backfillStructuredCatalog() {
        jdbcTemplate.update("update resources r join directions d on d.name=r.subject "
                + "set r.direction_id=d.id,r.category_id=d.category_id where r.direction_id is null or r.category_id is null");
        jdbcTemplate.update("update resources r join categories c on c.name=r.subject "
                + "set r.category_id=c.id where r.category_id is null");
        backfillDirectionByTitle("机器学习", "机器学习");
        backfillDirectionByTitle("软件测试", "软件测试");
        backfillDirectionByTitle("Web 安全", "Web安全");
        backfillDirectionByTitle("Android", "移动开发");
        backfillDirectionByTitle("Spark", "Spark");
        backfillDirectionByTitle("Docker", "Docker");
        backfillDirectionByTitle("MQTT", "物联网");
        jdbcTemplate.update("update resources set estimated_minutes=case when estimated_minutes<=0 then 45 else estimated_minutes end");
        jdbcTemplate.update("update resources r set learner_count=(select count(distinct lr.student_id) from learning_records lr where lr.resource_id=r.id)");
    }

    private void backfillDirectionByTitle(String keyword, String directionName) {
        jdbcTemplate.update("update resources r join directions d on d.name=? "
                        + "set r.direction_id=d.id,r.category_id=d.category_id "
                        + "where r.direction_id is null and r.title like ?",
                directionName, "%" + keyword + "%");
    }

    private void seedCourses() {
        seedCourse("Java 企业级后端开发", "Java", "从 Java 基础到 Spring Boot 接口开发与项目实战");
        seedCourse("C# 桌面与 Web 开发", "C#", "学习 WinForms、ASP.NET Core 与 .NET 数据访问");
        seedCourse("Python 数据分析入门", "Python", "覆盖 Python 基础、Pandas 数据处理和可视化");
        seedCourse("MySQL 与 Redis 数据存储", "数据库", "学习 SQL 查询、索引、事务和缓存设计");
        seedCourse("React 前端项目开发", "前端开发", "完成组件拆分、状态管理和接口联调");
        seedCourse("机器学习与推荐系统", "人工智能", "理解常见模型、特征工程和推荐流程");
    }

    private void seedResources() {
        seedResource("Java Spring Boot 接口实战", "Java", "video", "入门", "完成用户、课程和学习记录接口开发");
        seedResource("C# WinForms 小项目训练", "C#", "material", "入门", "用窗体控件完成桌面应用练习");
        seedResource("Python Pandas 数据清洗", "Python", "video", "入门", "使用 Pandas 完成数据读取、清洗和统计");
        seedResource("MySQL 索引与事务优化", "数据库", "material", "进阶", "学习索引选择、事务隔离和慢 SQL 分析");
        seedResource("React 组件化开发", "前端开发", "video", "入门", "构建可复用组件并完成接口请求");
        seedResource("机器学习模型入门", "人工智能", "material", "进阶", "从特征、训练到评估理解模型流程");
    }

    private void backfillResourceContent() {
        seedResource("Java Spring Boot 接口实战", "Java", "video", "入门", "完成用户、课程和学习记录接口开发");
        seedResource("C# WinForms 小项目训练", "C#", "material", "入门", "用窗体控件完成桌面应用练习");
        seedResource("Python Pandas 数据清洗", "Python", "video", "入门", "使用 Pandas 完成数据读取、清洗和统计");
        seedResource("MySQL 索引与事务优化", "数据库", "material", "进阶", "学习索引选择、事务隔离和慢 SQL 分析");
        seedResource("React 组件化开发", "前端开发", "video", "入门", "构建可复用组件并完成接口请求");
        seedResource("机器学习模型入门", "人工智能", "material", "进阶", "从特征、训练到评估理解模型流程");
        seedResource("软件测试用例设计", "软件工程", "material", "入门", "围绕需求编写等价类、边界值和场景测试用例");
        seedResource("Web 安全漏洞识别", "网络安全", "video", "进阶", "识别 XSS、SQL 注入、权限绕过和日志审计风险");
        seedResource("Android 页面与数据交互", "移动开发", "video", "入门", "完成移动端页面布局、接口请求和状态展示");
        seedResource("Spark 数据处理实战", "大数据", "material", "进阶", "使用 Spark 处理日志、宽表和统计指标");
        seedResource("Docker 到 Kubernetes 部署", "云计算", "video", "进阶", "从镜像构建到 K8s 发布完成应用部署流程");
        seedResource("MQTT 物联网通信基础", "物联网", "material", "入门", "学习设备上报、消息订阅、QoS 和边缘网关设计");
        jdbcTemplate.update("update resources set content=concat("
                + "'【文本学习】',title,'。',coalesce(description,''),'。本节课程适合在观看视频或下载资料之前先进行文字预习。学习时先明确课程目标：理解',subject,'方向中的核心概念、常见术语、典型场景和基本工作流程；再把知识放到一个可执行的小任务中验证。阅读过程中请准备笔记，记录三个问题：这个知识点解决什么问题，它依赖哪些前置知识，实际项目中容易在哪一步出错。',"
                + "'第一阶段关注概念。不要急着记忆大量细节，先用自己的话解释课程标题中的关键词，并把它和已经学过的知识建立联系。如果遇到陌生名词，可以先写下猜测，再通过示例代码、流程图或操作截图验证。第二阶段关注流程。把课程内容拆成输入、处理、输出三个部分，说明每一步产生什么结果，为什么需要这样设计。第三阶段关注实践。选择一个最小练习，例如完成一个接口、编写一个查询、实现一个组件、整理一组数据或搭建一个简单环境。练习完成后要回看错误信息和调试过程，因为这些细节往往比最终结果更能反映掌握程度。',"
                + "'学习完成后，请用五句话总结：本节最重要的概念是什么，关键步骤有哪些，容易混淆的点在哪里，可以迁移到什么项目场景，下一步应该补强哪项能力。建议把完成度分为了解、会做、能解释、能迁移四级。如果只能跟着步骤完成，说明还停留在会做阶段；如果能够脱离教程复述原因并改造练习，才算真正掌握。',"
                + "'本课程的文本学习内容也可以作为复习清单使用。再次复习时，请优先检查自己是否能独立画出流程、说出关键参数、解释一次失败案例，并把学习记录更新到平台中。这样系统后续生成学习路径、薄弱点分析和资源推荐时，才能更准确地反映你的真实状态。') "
                + "where content is null or char_length(trim(content))=0");
    }

    private void seedCategory(String name, String description) {
        jdbcTemplate.update("insert ignore into subject_categories(name, description) values(?, ?)", name, description);
    }

    private void seedNodes(String subject, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            jdbcTemplate.update("insert ignore into knowledge_nodes(subject, title, node_level, sort_order) values(?, ?, 1, ?)",
                    subject, titles[i], i + 1);
        }
    }

    private void seedCourse(String title, String subject, String description) {
        jdbcTemplate.update("insert into courses(title, subject, description) "
                        + "select ?, ?, ? where not exists (select 1 from courses where title=?)",
                title, subject, description, title);
    }

    private void seedResource(String title, String subject, String type, String level, String description) {
        jdbcTemplate.update("insert into resources(title, subject, type, level, description) "
                        + "select ?, ?, ?, ?, ? where not exists (select 1 from resources where title=?)",
                title, subject, type, level, description, title);
    }
}
