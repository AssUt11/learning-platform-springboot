package com.learning.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String uploadDir;
    private final String reportDir;

    public WebConfig(@Value("${app.upload-dir}") String uploadDir,
                     @Value("${app.report-dir}") String reportDir) {
        this.uploadDir = uploadDir;
        this.reportDir = reportDir;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173", "http://127.0.0.1:5173",
                        "http://localhost:8080", "http://127.0.0.1:8080",
                        "http://localhost:8081", "http://127.0.0.1:8081")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(fileLocation(uploadDir));
        registry.addResourceHandler("/reports/**").addResourceLocations(fileLocation(reportDir));
    }

    private String fileLocation(String path) {
        String location = new File(path).toURI().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
