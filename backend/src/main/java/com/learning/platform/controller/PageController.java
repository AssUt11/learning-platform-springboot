package com.learning.platform.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/login", "/home", "/interests", "/preferences", "/catalog", "/graph", "/learning", "/agents", "/teacher"})
    public String page() {
        return "forward:/index.html";
    }
}
