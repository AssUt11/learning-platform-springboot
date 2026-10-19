package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CatalogGraphSyncService;
import com.learning.platform.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/catalog/graph")
public class CatalogGraphController {
    private final CurrentUserService users;
    private final CatalogGraphSyncService sync;
    public CatalogGraphController(CurrentUserService users,CatalogGraphSyncService sync) { this.users=users; this.sync=sync; }
    @GetMapping("/status") public Result<Map<String,Object>> status(@RequestHeader(value="Authorization",required=false) String auth) {
        users.requireAdmin(auth); return Result.success(sync.status());
    }
    @PostMapping("/sync") public Result<Map<String,Object>> sync(@RequestHeader(value="Authorization",required=false) String auth) {
        users.requireAdmin(auth); return Result.success(sync.sync(true));
    }
}
