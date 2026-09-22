package com.learning.platform.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PageControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"/home", "/course/1", "/learn/1/2"})
    void directPageLinksServeTheFrontend(String path) throws Exception {
        MockMvcBuilders.standaloneSetup(new PageController()).build()
                .perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
    }
}
