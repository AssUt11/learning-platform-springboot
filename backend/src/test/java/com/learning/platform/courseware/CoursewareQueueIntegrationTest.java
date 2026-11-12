package com.learning.platform.courseware;

import static org.junit.jupiter.api.Assertions.*;

import com.learning.platform.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exercises the actual queue SQL in an isolated in-memory database, never the project DB. */
class CoursewareQueueIntegrationTest {
    @TempDir Path uploads;
    private JdbcTemplate jdbc;
    private CoursewareService service;

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:courseware_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table resources(id bigint primary key,teacher_id bigint,lifecycle_status varchar(20))");
        jdbc.execute("create table courses(id bigint primary key,legacy_resource_id bigint)");
        jdbc.execute("create table course_chapters(id bigint primary key,course_id bigint,status varchar(20))");
        jdbc.execute("create table course_lessons(id bigint primary key,chapter_id bigint,status varchar(20),lesson_type varchar(20))");
        jdbc.execute("create table lesson_assets(id bigint primary key,lesson_id bigint,file_url varchar(255),file_name varchar(255),file_size bigint,asset_type varchar(20),content_text text,status int,updated_at timestamp)");
        new CoursewareSchemaInitializer(jdbc).initialize();
        jdbc.update("insert into resources values(21,11,'active')");
        jdbc.update("insert into courses values(2,21)");
        jdbc.update("insert into course_chapters values(3,2,'published')");
        jdbc.update("insert into course_lessons values(7,3,'published','document')");
        Files.writeString(uploads.resolve("course.txt"), "索引帮助缩小需要扫描的数据范围。\n\n事务用于协调一组修改的提交和回滚。");
        jdbc.update("insert into lesson_assets values(5,7,'/uploads/course.txt','course.txt',100,'document','教师摘要',1,current_timestamp)");
        service = new CoursewareService(jdbc, new CoursewareFileParser(uploads.toString()), new DataSourceTransactionManager(dataSource));
    }

    @Test
    void durableJobProducesPreviewAndRequiresExplicitTeacherConfirmation() {
        Map<String, Object> queued = service.request(11, 7, 5);
        assertEquals("QUEUED", queued.get("status"));
        assertEquals(queued.get("documentId"), service.request(11, 7, 5).get("documentId"));

        service.processNext();
        Map<String, Object> preview = service.status(11, 7, 5);
        assertEquals("READY", preview.get("status"));
        assertEquals(2, ((Number) preview.get("segmentCount")).intValue());
        assertEquals(0, jdbc.queryForObject("select count(*) from courseware_documents where status='CONFIRMED'", Integer.class));

        Map<String, Object> confirmed = service.confirm(11, 7, 5, (String) preview.get("documentId"), (String) preview.get("versionHash"));
        assertEquals("CONFIRMED", confirmed.get("status"));
        assertEquals("教师摘要", jdbc.queryForObject("select content_text from lesson_assets where id=5", String.class));
    }

    @Test
    void expiredLeaseIsRecoveredAfterRestartAndClaimedOnce() {
        String id = (String) service.request(11, 7, 5).get("documentId");
        jdbc.update("update courseware_documents set status='PROCESSING',attempt_count=1,worker_token='old-worker',lease_until=timestampadd(minute,-10,current_timestamp) where id=?", id);

        service.processNext();

        assertEquals("READY", service.status(11, 7, 5).get("status"));
        assertEquals(2, jdbc.queryForObject("select attempt_count from courseware_documents where id=?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("select count(*) from courseware_chunks where document_id=?", Integer.class, id));
        service.processNext();
        assertEquals(2, jdbc.queryForObject("select count(*) from courseware_chunks where document_id=?", Integer.class, id));
    }

    @Test
    void threeInterruptedAttemptsFailClearlyRatherThanSpinForever() {
        String id = (String) service.request(11, 7, 5).get("documentId");
        jdbc.update("update courseware_documents set status='PROCESSING',attempt_count=3,worker_token='old-worker',lease_until=timestampadd(minute,-10,current_timestamp) where id=?", id);

        service.processNext();

        Map<String, Object> failed = service.status(11, 7, 5);
        assertEquals("FAILED", failed.get("status"));
        assertTrue(String.valueOf(failed.get("error")).contains("中断"));
        assertEquals(0, jdbc.queryForObject("select count(*) from courseware_chunks", Integer.class));
        assertThrows(BusinessException.class, () -> service.confirm(11, 7, 5, id, "fake"));
    }

    @Test
    void editedAssetCannotReuseConfirmedChunksAndGetsANewVersion() {
        service.request(11, 7, 5);
        service.processNext();
        Map<String, Object> preview = service.status(11, 7, 5);
        service.confirm(11, 7, 5, (String) preview.get("documentId"), (String) preview.get("versionHash"));
        jdbc.update("update lesson_assets set content_text='重新编辑的摘要' where id=5");

        Map<String, Object> stale = service.status(11, 7, 5);
        assertEquals("STALE", stale.get("status"));
        assertTrue(((java.util.List<?>) stale.get("segments")).isEmpty());
        assertThrows(BusinessException.class, () -> service.confirm(11, 7, 5,
            (String) preview.get("documentId"), (String) preview.get("versionHash")));
        assertNotEquals(preview.get("documentId"), service.request(11, 7, 5).get("documentId"));
    }

    @Test
    void changingTheLessonTypeDisablesTheOldDocumentWorkflow() {
        service.request(11, 7, 5);
        jdbc.update("update course_lessons set lesson_type='article' where id=7");
        assertThrows(BusinessException.class, () -> service.status(11, 7, 5));
        service.processNext();
        assertEquals("FAILED", jdbc.queryForObject("select status from courseware_documents", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from courseware_chunks", Integer.class));
    }
}
