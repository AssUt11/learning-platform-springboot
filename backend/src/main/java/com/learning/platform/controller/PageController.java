package com.learning.platform.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletResponse;

@Controller
public class PageController {
    @GetMapping({"/", "/login", "/home", "/planning", "/interests", "/preferences", "/catalog", "/graph", "/learning", "/agents", "/messages", "/chat", "/teacher", "/admin", "/course/{courseId}", "/learn/{courseId}/{lessonId}"})
    public String page(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        return "forward:/index.html";
    }
}
