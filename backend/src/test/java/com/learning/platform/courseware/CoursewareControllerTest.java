package com.learning.platform.courseware;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.exception.GlobalExceptionHandler;
import com.learning.platform.service.CurrentUserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoursewareControllerTest {
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final CoursewareService service = mock(CoursewareService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CoursewareController(users, service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void anonymousAndStudentCannotStartDocumentExtraction() throws Exception {
        when(users.requireTeacher(null)).thenThrow(new BusinessException(401, "Please log in first."));
        when(users.requireTeacher("Bearer student")).thenThrow(new BusinessException(403, "Teacher only."));
        mvc.perform(post("/api/teacher/lessons/7/assets/5/courseware/extract")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/teacher/lessons/7/assets/5/courseware/extract").header("Authorization", "Bearer student"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void extractAndConfirmUseTheSignedTeacherRatherThanClientUserId() throws Exception {
        when(users.requireTeacher("Bearer teacher")).thenReturn(Map.of("id", 11L));
        when(service.request(11L, 7L, 5L)).thenReturn(Map.of("documentId", "doc-1", "status", "QUEUED"));
        when(service.confirm(11L, 7L, 5L, "doc-1", "file-hash")).thenReturn(Map.of("status", "CONFIRMED"));
        mvc.perform(post("/api/teacher/lessons/7/assets/5/courseware/extract")
            .header("Authorization", "Bearer teacher").param("teacherId", "999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUEUED"));
        mvc.perform(post("/api/teacher/lessons/7/assets/5/courseware/doc-1/confirm")
            .header("Authorization", "Bearer teacher").contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionHash\":\"file-hash\",\"teacherId\":\"999\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        verify(service).request(11L, 7L, 5L);
        verify(service).confirm(11L, 7L, 5L, "doc-1", "file-hash");
    }
}
