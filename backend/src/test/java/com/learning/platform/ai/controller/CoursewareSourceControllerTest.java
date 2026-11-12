package com.learning.platform.ai.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.learning.platform.ai.context.CoursewareRetrievalService;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.LessonPlaybackService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoursewareSourceControllerTest {
    @Test
    void requiresAnAuthenticatedAccountBeforeLookingUpPrivateMetadata() {
        CurrentUserService users = mock(CurrentUserService.class);
        LessonPlaybackService playback = mock(LessonPlaybackService.class);
        CoursewareRetrievalService retrieval = mock(CoursewareRetrievalService.class);
        when(users.requireLogin(null)).thenThrow(new BusinessException(401, "请先登录"));
        CoursewareSourceController controller = new CoursewareSourceController(users, playback, retrieval);
        assertEquals(401, assertThrows(BusinessException.class, () -> controller.source(null, 8, 2, 3)).getCode());
        verifyNoInteractions(playback, retrieval);
    }

    @Test
    void publishedLessonAccessMustPassBeforeReturningAnyChunkText() {
        CurrentUserService users = mock(CurrentUserService.class);
        LessonPlaybackService playback = mock(LessonPlaybackService.class);
        CoursewareRetrievalService retrieval = mock(CoursewareRetrievalService.class);
        Map<String, Object> student = Map.of("id", 1L, "role", "student");
        when(users.requireLogin("Bearer test")).thenReturn(student);
        when(playback.playback(student, 2, 3)).thenThrow(new BusinessException(403, "课程未上架"));
        CoursewareSourceController controller = new CoursewareSourceController(users, playback, retrieval);
        assertEquals(403, assertThrows(BusinessException.class, () -> controller.source("Bearer test", 8, 2, 3)).getCode());
        verifyNoInteractions(retrieval);
    }
}
