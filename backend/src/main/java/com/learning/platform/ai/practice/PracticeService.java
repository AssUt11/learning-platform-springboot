package com.learning.platform.ai.practice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.*;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CourseContentValidationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PracticeService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AiModelProvider provider;
    private final AiProperties properties;
    private final AiUserRateLimiter limiter;
    private final CourseContentValidationService validation;
    private final Map<Long, Long> generating = new ConcurrentHashMap<>();

    public PracticeService(JdbcTemplate jdbc, ObjectMapper mapper, AiModelProvider provider,
                           AiProperties properties, AiUserRateLimiter limiter, CourseContentValidationService validation) {
        this.jdbc = jdbc; this.mapper = mapper; this.provider = provider;
        this.properties = properties; this.limiter = limiter; this.validation = validation;
    }

    public Map<String, Object> latest(long userId, long courseId, long lessonId) {
        lesson(courseId, lessonId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from ai_practice_sessions where user_id=? and course_id=? and lesson_id=? order by id desc limit 1",
                userId, courseId, lessonId);
        Map<String, Object> result = rows.isEmpty() ? new LinkedHashMap<>(Map.of("available", false)) : view(rows.get(0));
        result.put("generating", Objects.equals(generating.get(userId), lessonId));
        return result;
    }

    public Map<String, Object> generate(long userId, long courseId, long lessonId) {
        lesson(courseId, lessonId);
        if (!properties.isEnabled()) throw new BusinessException(503, "AI 功能尚未启用。");
        if (generating.putIfAbsent(userId, lessonId) != null) throw new BusinessException(409, "已有练习正在生成，请稍候返回对应课时查看。");
        try {
            if (!limiter.tryAcquire(userId)) throw new BusinessException(429, "AI 请求过于频繁，请稍后再试。");
            List<Map<String, Object>> scores = jdbc.queryForList(
                    "select score from ai_practice_sessions where user_id=? and lesson_id=? and submitted_at is not null order by id desc limit 1",
                    userId, lessonId);
            int score = scores.isEmpty() ? -1 : ((Number) scores.get(0).get("score")).intValue();
            String difficulty = score < 60 ? "基础" : score >= 80 ? "进阶" : "巩固";
            List<Map<String, Object>> assets = jdbc.queryForList(
                    "select content_text from lesson_assets where lesson_id=? and status=1 and transcoding_status='ready' and content_text is not null order by id desc limit 3",
                    lessonId);
            StringBuilder source = new StringBuilder();
            for (Map<String, Object> asset : assets) source.append(asset.get("content_text")).append('\n');
            String evidence = source.toString().replaceAll("<[^>]*>", " ").trim();
            // 不把视频/PPT 文件名冒充讲义。教师可在资料课时中补充文本摘要供出题使用。
            if (evidence.length() < 40) throw new BusinessException(409, "本课时缺少足够的文字资料，请教师补充讲义或文本摘要后再出题。");
            evidence = evidence.substring(0, Math.min(1600, evidence.length()));
            // 本机微调模型容易照抄带占位符的JSON示例，使用字段说明而非示例题干。
            String system = "你是授课教师。为下面教材出一道" + difficulty + "单项选择题。必须写出具体知识点，不能输出模板、占位符。"
                    + "正确选项使用教材中的原文短语。只返回JSON对象，含questions数组，数组内一个对象。"
                    + "对象字段：stem为题干；options为四个不同选项的字符串数组；answer为正确选项的索引整数，从0开始；"
                    + "explanation为简短解析；reference为教材中逐字摘录的依据原句，必须包含正确选项。不要输出Markdown。";
            String prompt = "请根据教材出一道" + difficulty + "题。教材：" + evidence;
            List<Map<String, Object>> messages = new ArrayList<>(List.of(Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", prompt)));
            List<Map<String, Object>> questions;
            var response = provider.chatTurn(messages, List.of(), true, new AiCancellation());
            try {
                questions = PracticeQuestionParser.parse(response.path("message").path("content").asText(""), mapper);
                PracticeQuestionParser.requireGrounding(questions, evidence);
            } catch (BusinessException invalidFormat) {
                // 只修正一次结构；不放宽答案/选项校验，不把无效题目写入数据库。
                messages.add(Map.of("role", "user", "content", "上次输出未通过结构或原文依据校验。请重新生成一道题的完整JSON，必须有questions数组；题目含stem、4个不重复的options、整数answer(0到3)、explanation和reference。reference逐字摘录资料，正确选项逐字取自该reference，不能改写。只输出JSON。"));
                response = provider.chatTurn(messages, List.of(), true, new AiCancellation());
                questions = PracticeQuestionParser.parse(response.path("message").path("content").asText(""), mapper);
                PracticeQuestionParser.requireGrounding(questions, evidence);
            }
            // 生成期间课程可能被教师撤回，落库前再次检查可见性。
            lesson(courseId, lessonId);
            String quizId = UUID.randomUUID().toString();
            jdbc.update("insert into ai_practice_sessions(quiz_id,user_id,course_id,lesson_id,difficulty,questions_json,provider_code,model_code) values(?,?,?,?,?,?,?,?)",
                    quizId, userId, courseId, lessonId, difficulty, encode(questions), provider.providerCode(), provider.modelCode());
            return view(owned(userId, quizId));
        } catch (BusinessException error) { throw error; }
        catch (Exception error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException) throw new BusinessException(503, "无法连接模型服务，请先启动推理服务。");
                if (cause instanceof HttpTimeoutException) throw new BusinessException(504, "练习生成超时，请稍后重试。");
            }
            throw new BusinessException(502, "练习生成失败，请稍后重试。");
        } finally { generating.remove(userId); }
    }

    @Transactional
    public Map<String, Object> submit(long userId, String quizId, List<Integer> answers) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from ai_practice_sessions where quiz_id=? and user_id=? for update", quizId, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "练习不存在。");
        Map<String, Object> row = rows.get(0);
        lesson(((Number) row.get("course_id")).longValue(), ((Number) row.get("lesson_id")).longValue());
        if (row.get("submitted_at") != null) return view(row); // 重复提交返回已保存的成绩，不能刷改答案。
        List<Map<String, Object>> questions = decode(String.valueOf(row.get("questions_json")));
        if (answers == null || answers.size() != questions.size()
                || answers.stream().anyMatch(value -> value == null || value < 0 || value > 3))
            throw new BusinessException(400, "请完成每道题，每题选择一个有效答案。");
        int correct = 0;
        for (int i = 0; i < questions.size(); i++)
            if (answers.get(i).intValue() == ((Number) questions.get(i).get("answer")).intValue()) correct++;
        int score = correct * 100 / questions.size();
        jdbc.update("update ai_practice_sessions set answers_json=?,score=?,submitted_at=current_timestamp(3) where quiz_id=? and user_id=? and submitted_at is null",
                encode(answers), score, quizId, userId);
        return view(owned(userId, quizId));
    }

    private Map<String, Object> lesson(long courseId, long lessonId) {
        if (!validation.validatePublic(courseId).isContentComplete()) throw new BusinessException(404, "课程不存在或尚未发布。");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select l.title from course_lessons l join course_chapters ch on ch.id=l.chapter_id
            join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id
            where r.id=? and l.id=? and r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
            and ch.status='published' and l.status='published'
            """, courseId, lessonId);
        if (rows.isEmpty()) throw new BusinessException(404, "课时不存在或尚未发布。");
        return rows.get(0);
    }

    private Map<String, Object> owned(long userId, String quizId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from ai_practice_sessions where quiz_id=? and user_id=?", quizId, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "练习不存在。");
        return rows.get(0);
    }

    private Map<String, Object> view(Map<String, Object> row) {
        boolean submitted = row.get("submitted_at") != null;
        List<Map<String, Object>> questions = decode(String.valueOf(row.get("questions_json")));
        if (!submitted) for (Map<String, Object> question : questions) {
            question.remove("answer"); question.remove("explanation"); question.remove("reference");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true); result.put("quizId", row.get("quiz_id"));
        result.put("lessonId", row.get("lesson_id")); result.put("courseId", row.get("course_id"));
        result.put("difficulty", row.get("difficulty")); result.put("questions", questions);
        result.put("submitted", submitted); result.put("score", row.get("score"));
        result.put("answers", submitted ? decodeAnswers(String.valueOf(row.get("answers_json"))) : List.of());
        result.put("model", row.get("model_code")); result.put("provider", row.get("provider_code"));
        return result;
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new BusinessException(500, "练习保存失败。"); }
    }
    private List<Map<String, Object>> decode(String value) {
        try { return mapper.readValue(value, new TypeReference<List<Map<String, Object>>>() { }); }
        catch (Exception error) { throw new BusinessException(500, "练习读取失败。"); }
    }
    private List<Integer> decodeAnswers(String value) {
        try { return mapper.readValue(value, new TypeReference<List<Integer>>() { }); }
        catch (Exception error) { throw new BusinessException(500, "答题记录读取失败。"); }
    }
}
