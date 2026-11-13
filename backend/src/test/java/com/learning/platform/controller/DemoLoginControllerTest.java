package com.learning.platform.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.learning.platform.service.DemoLoginService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DemoLoginControllerTest {
    @Test void credentialResponsesCannotBeCachedAndDoNotBypassTheNormalLogin() throws Exception {
        var service = mock(DemoLoginService.class);
        when(service.credentials(any(), eq("student"))).thenReturn(Map.of("email", "feature-demo-weak@zhidu.local", "password", "test-demo-password"));
        var mvc = MockMvcBuilders.standaloneSetup(new DemoLoginController(service)).build();
        mvc.perform(post("/api/auth/demo-accounts/student")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.email").value("feature-demo-weak@zhidu.local"))
            .andExpect(jsonPath("$.data.token").doesNotExist());
    }
}
