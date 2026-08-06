package com.learning.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.learning.platform.common.PageResult;
import com.learning.platform.vo.CourseContentValidationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogServiceTest {
    @Test
    void loadsCategoriesAndDirectionsInTwoQueries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CourseContentValidationService validationService = mock(CourseContentValidationService.class);
        CatalogService service = new CatalogService(jdbcTemplate, validationService);
        List<Map<String, Object>> categories = new ArrayList<Map<String, Object>>();
        categories.add(row("id", 1L, "name", "软件工程"));
        categories.add(row("id", 2L, "name", "人工智能"));
        List<Map<String, Object>> directions = new ArrayList<Map<String, Object>>();
        Map<String, Object> java = row("id", 11L, "categoryId", 1L, "name", "Java");
        Map<String, Object> machineLearning = row("id", 21L, "categoryId", 2L, "name", "机器学习");
        directions.add(java);
        directions.add(machineLearning);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(categories, directions);

        List<Map<String, Object>> result = service.categories();

        assertEquals(2, result.size());
        assertSame(java, directionList(result.get(0)).get(0));
        assertSame(machineLearning, directionList(result.get(1)).get(0));
        verify(jdbcTemplate, times(2)).queryForList(anyString());
    }

    @Test
    void courseListReturnsOnlyCardSummaryWithoutContentOrVideoUrl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CourseContentValidationService validationService = mock(CourseContentValidationService.class);
        CourseContentValidationResult validation = mock(CourseContentValidationResult.class);
        when(validation.isContentComplete()).thenReturn(true);
        when(validation.getChapterCount()).thenReturn(2);
        when(validation.getPublishedLessonCount()).thenReturn(3);
        when(validation.getTotalDurationSeconds()).thenReturn(600);
        when(validation.getPlayableVideoCount()).thenReturn(2);
        when(validationService.validatePublic(7L)).thenReturn(validation);
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(java.util.Collections.singletonList(row("id", 7L, "title", "示例课程", "type", "video")));
        CatalogService service = new CatalogService(jdbcTemplate, validationService);

        PageResult<Map<String, Object>> result = service.courses(null, null, null, null,
                null, null, 1, 12);

        assertEquals(1, result.getTotal());
        assertFalse(result.getList().get(0).containsKey("content"));
        assertFalse(result.getList().get(0).containsKey("fileUrl"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> directionList(Map<String, Object> category) {
        return (List<Map<String, Object>>) category.get("directions");
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
