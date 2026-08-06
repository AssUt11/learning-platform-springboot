package com.learning.platform.service;

import com.learning.platform.dto.CreateChapterRequest;
import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherCourseStructureServiceTest {
    @Test
    void teacherCannotCreateChapterForAnotherTeachersCourse() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        LegacyCourseMigrationService migrationService = mock(LegacyCourseMigrationService.class);
        TeacherCourseStructureService service = new TeacherCourseStructureService(
                jdbcTemplate, fileStorageService, migrationService);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong()))
                .thenReturn(new ArrayList<>());
        CreateChapterRequest request = new CreateChapterRequest();
        request.setTitle("越权章节");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createChapter(3L, 7L, request));

        assertEquals(404, error.getCode());
        verify(jdbcTemplate, never()).update(anyString(), anyLong());
    }
}
