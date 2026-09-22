package com.learning.platform.controller;

import com.learning.platform.ai.context.KnowledgePrerequisiteGovernanceService;
import com.learning.platform.service.CurrentUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/knowledge/prerequisites")
public class KnowledgePrerequisiteAdminController {
    private final CurrentUserService currentUserService;
    private final KnowledgePrerequisiteGovernanceService governanceService;

    public KnowledgePrerequisiteAdminController(CurrentUserService currentUserService,
                                                KnowledgePrerequisiteGovernanceService governanceService) {
        this.currentUserService = currentUserService;
        this.governanceService = governanceService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        currentUserService.requireAdmin(authorization);
        return Map.of("relations", governanceService.list());
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        Map<String, Object> input = body == null ? java.util.Collections.emptyMap() : body;
        long id = governanceService.create(number(admin.get("id")), text(input.get("subject")),
                text(input.get("prerequisiteKey")), text(input.get("prerequisiteTitle")),
                text(input.get("targetKey")), text(input.get("targetTitle")),
                text(input.get("sourceReference")));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("sync", governanceService.syncAll());
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        governanceService.delete(number(admin.get("id")), id);
        return Map.of("success", true, "sync", governanceService.syncAll());
    }

    @PostMapping("/sync")
    public Map<String, Object> sync(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        currentUserService.requireAdmin(authorization);
        return governanceService.syncAll();
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
