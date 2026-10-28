package com.learning.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.application.LearningAgentAiService;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LearningPlanningService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogService catalog;
    private final LearningAgentAiService ai;
    private final StudentLearningPathService paths;
    private final AdaptiveLearningService adaptive;
    private final AiUserRateLimiter limiter;
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();
    public LearningPlanningService(JdbcTemplate jdbc, ObjectMapper mapper, CatalogService catalog,
                                  LearningAgentAiService ai, StudentLearningPathService paths,
                                  AdaptiveLearningService adaptive, AiUserRateLimiter limiter) {
        this.jdbc=jdbc; this.mapper=mapper; this.catalog=catalog; this.ai=ai; this.paths=paths; this.adaptive=adaptive; this.limiter=limiter;
    }

    public Map<String, Object> profile(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from student_learning_profiles where user_id=?", userId);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        Map<String, Object> answers = row.isEmpty() ? new LinkedHashMap<>() : decode(String.valueOf(row.get("answers_json")));
        if (row.isEmpty()) {
            List<Map<String, Object>> preferences = jdbc.queryForList("select subject from student_preferences where student_id=? order by id limit 1", userId);
            if (!preferences.isEmpty()) answers.put("subject", preferences.get(0).get("subject"));
        }
        String status = Objects.toString(row.get("status"), "draft");
        Object pathId = row.get("active_path_id");
        result.put("answers", answers); result.put("status", status);
        result.put("version", number(row.get("version"))); result.put("activePathId", pathId);
        result.put("needsPlanning", "draft".equals(status) && pathId == null);
        result.put("generating", generating.contains(userId));
        if (!row.isEmpty()) {
            List<Map<String, Object>> previews = jdbc.queryForList("select preview_id,result_json from learning_plan_previews where user_id=? and profile_version=? order by created_at desc,preview_id desc limit 1", userId, row.get("version"));
            if (!previews.isEmpty()) result.put("preview", previewView(String.valueOf(previews.get(0).get("preview_id")), decode(String.valueOf(previews.get(0).get("result_json")))));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> save(long userId, Map<String, Object> body) {
        Map<String, Object> answers = validate(body, false);
        jdbc.update("insert into student_learning_profiles(user_id,answers_json) values(?,?) on duplicate key update answers_json=values(answers_json),version=version+1", userId, encode(answers));
        return profile(userId);
    }

    public Map<String,Object> studyIntent(long userId) {
        List<Map<String,Object>> rows=jdbc.queryForList("select answers_json from student_learning_profiles where user_id=?",userId);
        return rows.isEmpty()?Map.of():decode(String.valueOf(rows.get(0).get("answers_json")));
    }

    @Transactional
    public Map<String, Object> defer(long userId) {
        jdbc.update("insert into student_learning_profiles(user_id,answers_json,status) values(?,'{}','deferred') on duplicate key update status=case when active_path_id is null then 'deferred' else status end", userId);
        return profile(userId);
    }

    public Map<String, Object> preview(Map<String, Object> user, int expectedVersion) {
        long userId = ((Number)user.get("id")).longValue();
        if (!generating.add(userId)) throw new BusinessException(409, "学习路径正在生成，请稍候查看结果。");
        try {
            if (!limiter.tryAcquire(userId)) throw new BusinessException(429, "规划请求较多，请稍后再试。");
            Map<String, Object> profile = profile(userId);
            if (number(profile.get("version")) != expectedVersion) throw new BusinessException(409, "回答已更新，请确认最新回答后重新生成。");
            @SuppressWarnings("unchecked") Map<String,Object> answers = validate((Map<String,Object>)profile.get("answers"), true);
            String subject = String.valueOf(answers.get("subject"));
            List<Map<String, Object>> candidates = catalog.courses(userId, subject, null, null, null, null, 1, 50).getList();
            List<Map<String,Object>> selected = new ArrayList<>();
            // Keep candidates from each stage within the model's eight-course evidence budget.
            for (int index=0; index<3; index++) for (String stage : LearningStages.CODES) {
                int rank=index;
                candidates.stream().filter(course -> stage.equals(course.get("learningStage"))).skip(rank).findFirst().ifPresent(course -> {
                    if (selected.size()<8) selected.add(course);
                });
            }
            RecommendationContext context = new RecommendationContext.Builder().studentId(userId)
                    .studentName(String.valueOf(user.get("name"))).subject(subject).studyIntent(answers)
                    .resources(selected).learningEvidence(adaptive.evidence(userId))
                    .weakNodes(List.of()).recentRecords(List.of()).favoriteResources(List.of()).build();
            String id = UUID.randomUUID().toString();
            LearningAgentResult generated = ai.execute("path", context, id);
            if (number(profile(userId).get("version")) != expectedVersion) throw new BusinessException(409, "生成期间回答已更新，请按最新回答重新生成。");
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("path", generated.getLearningPath()); result.put("content", generated.getContent());
            result.put("toolsUsed", generated.getToolsUsed()); result.put("sources", generated.getSources());
            result.put("graphUsed", generated.isGraphUsed()); result.put("provider", ai.providerCode()); result.put("model", ai.modelCode());
            jdbc.update("insert into learning_plan_previews(preview_id,user_id,profile_version,result_json) values(?,?,?,?)", id,userId,expectedVersion,encode(result));
            return previewView(id,result);
        } finally { generating.remove(userId); }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String,Object> adopt(long userId, String previewId) {
        if (previewId == null || previewId.length()>64) throw new BusinessException(400,"请先生成并选择学习路径。");
        List<Map<String,Object>> profiles=jdbc.queryForList("select * from student_learning_profiles where user_id=? for update",userId);
        List<Map<String,Object>> previews=jdbc.queryForList("select * from learning_plan_previews where preview_id=? and user_id=?",previewId,userId);
        if (profiles.isEmpty() || previews.isEmpty()) throw new BusinessException(404,"学习路径预览不存在。");
        Map<String,Object> profile=profiles.get(0), preview=previews.get(0);
        if (previewId.equals(profile.get("active_path_id"))) return paths.get(userId,previewId);
        if (number(profile.get("version"))!=number(preview.get("profile_version"))) throw new BusinessException(409,"回答已变更，请重新生成路径后采用。");
        Map<String,Object> data=decode(String.valueOf(preview.get("result_json")));
        Map<String,Object> path=(Map<String,Object>)data.get("path");
        for (Map<String,Object> node:(List<Map<String,Object>>)path.get("nodes")) {
            if (node.get("courseId") instanceof Number) catalog.courseDetail(Map.of("id",userId,"role","student"),userId,((Number)node.get("courseId")).longValue());
        }
        LearningAgentResult result=new LearningAgentResult(previewId,String.valueOf(data.get("content")),
                (List<String>)data.get("toolsUsed"),(List<String>)data.get("sources"),Boolean.TRUE.equals(data.get("graphUsed")),path);
        Map<String,Object> saved=paths.save(userId,result);
        jdbc.update("update student_learning_profiles set status='completed',active_path_id=? where user_id=?",previewId,userId);
        Map<String,Object> answers=decode(String.valueOf(profile.get("answers_json")));
        jdbc.update("delete from student_preferences where student_id=?",userId);
        jdbc.update("insert into student_preferences(student_id,subject) values(?,?)",userId,answers.get("subject"));
        return saved;
    }

    public Map<String,Object> current(long userId) {
        Map<String,Object> profile=profile(userId);
        Object id=profile.get("activePathId");
        Map<String,Object> result=new LinkedHashMap<>(); result.put("profile",profile);
        result.put("path",id==null?Map.of("available",false):paths.get(userId,String.valueOf(id)));
        return result;
    }

    private Map<String,Object> previewView(String id,Map<String,Object> result) {
        Map<String,Object> view=new LinkedHashMap<>(result); view.put("previewId",id); return view;
    }

    public static Map<String,Object> validate(Map<String,Object> body, boolean complete) {
        if (body==null) throw new BusinessException(400,"请填写学习规划回答。");
        Map<String,Object> result=new LinkedHashMap<>();
        for (String key:List.of("subject","background","goal","targetStage","notes")) {
            String value=Objects.toString(body.get(key),"").trim();
            if (value.length()>(key.equals("notes")?600:60)) throw new BusinessException(400,"学习规划回答过长。");
            if (!value.isEmpty()) result.put(key,value);
        }
        Map<String,List<String>> allowed=Map.of("background",List.of("none","basics","projects"),
                "goal",List.of("interest","exam","project","job"),"targetStage",List.of("auto","foundation","intermediate","advanced"));
        allowed.forEach((key,values)->{ if (result.containsKey(key)&&!values.contains(result.get(key))) throw new BusinessException(400,"请选择有效的学习规划选项。"); });
        Object minutes=body.get("dailyMinutes");
        if (minutes!=null&&!String.valueOf(minutes).isBlank()) {
            int value;
            try { value=new java.math.BigDecimal(String.valueOf(minutes)).intValueExact(); }
            catch(Exception error) { throw new BusinessException(400,"每天学习时间应为整数分钟。"); }
            if(value<1||value>480) throw new BusinessException(400,"每天学习时间应为1至480分钟。");
            result.put("dailyMinutes",value);
        }
        if(body.get("knownTopics") instanceof List<?> topics) {
            if(topics.size()>20) throw new BusinessException(400,"已学知识点最多选择20项。");
            List<String> safe=new ArrayList<>();
            for(Object topic:topics) { String value=Objects.toString(topic,"").trim(); if(value.length()>120) throw new BusinessException(400,"知识点名称过长。"); if(!value.isEmpty()) safe.add(value); }
            result.put("knownTopics",safe);
        }
        if(complete) for(String key:List.of("subject","background","goal","targetStage","dailyMinutes"))
            if(!result.containsKey(key)) throw new BusinessException(400,"请完成五个学习规划问题。");
        return result;
    }
    private int number(Object value) { return value instanceof Number?((Number)value).intValue():0; }
    private String encode(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception error){throw new BusinessException(500,"学习规划保存失败。");} }
    private Map<String,Object> decode(String value) { try{return mapper.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception error){throw new BusinessException(500,"学习规划读取失败。");} }
}
