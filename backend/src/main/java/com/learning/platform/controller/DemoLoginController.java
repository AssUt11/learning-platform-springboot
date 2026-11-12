package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.DemoLoginService;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/demo-accounts")
public class DemoLoginController {
    private final DemoLoginService demos;
    public DemoLoginController(DemoLoginService demos) { this.demos = demos; }

    @GetMapping
    public ResponseEntity<Result<Map<String, Object>>> available(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(demos.available(request)));
    }

    @PostMapping("/{role}")
    public ResponseEntity<Result<Map<String, String>>> credentials(HttpServletRequest request, @PathVariable String role) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(demos.credentials(request, role)));
    }
}
