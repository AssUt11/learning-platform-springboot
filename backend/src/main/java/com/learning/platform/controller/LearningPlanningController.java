package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.LearningPlanningService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningPlanningController {
    private final CurrentUserService users;
    private final LearningPlanningService planning;
    public LearningPlanningController(CurrentUserService users,LearningPlanningService planning) {this.users=users;this.planning=planning;}
    private long id(String auth){return ((Number)users.requireLearner(auth).get("id")).longValue();}
    @GetMapping("/profile") public Result<Map<String,Object>> profile(@RequestHeader(value="Authorization",required=false)String auth){return Result.success(planning.profile(id(auth)));}
    @PutMapping("/profile") public Result<Map<String,Object>> save(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> body){return Result.success(planning.save(id(auth),body));}
    @PostMapping("/profile/defer") public Result<Map<String,Object>> defer(@RequestHeader(value="Authorization",required=false)String auth){return Result.success(planning.defer(id(auth)));}
    @PostMapping("/planning/preview") public Result<Map<String,Object>> preview(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> body){
        Object version=body.get("version");
        if(!(version instanceof Number))throw new com.learning.platform.exception.BusinessException(400,"请先保存学习规划回答。");
        return Result.success(planning.preview(users.requireLearner(auth),((Number)version).intValue()));
    }
    @PostMapping("/planning/adopt") public Result<Map<String,Object>> adopt(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> body){return Result.success(planning.adopt(id(auth),java.util.Objects.toString(body.get("previewId"),null)));}
    @GetMapping("/planning/current") public Result<Map<String,Object>> current(@RequestHeader(value="Authorization",required=false)String auth){return Result.success(planning.current(id(auth)));}
}
