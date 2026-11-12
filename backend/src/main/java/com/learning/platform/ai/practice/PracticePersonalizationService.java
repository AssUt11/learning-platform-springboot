package com.learning.platform.ai.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Selects material from this student's actual study and submitted answers, before invoking the model. */
@Service
public class PracticePersonalizationService {
    private static final List<String> LEVELS = List.of("基础", "巩固", "进阶");
    private static final int HISTORY_LIMIT = 30;
    private static final int RECENT_QUESTIONS = 4;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PracticePersonalizationService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Plan plan(long userId, long courseId, Long fixedLessonId, boolean studiedOnly, String requestedLevel) {
        var rows = jdbc.queryForList("""
            select l.id lessonId,l.title lessonTitle,l.lesson_type lessonType,
              coalesce(p.progress_percent,0) progressPercent,coalesce(p.completed,0) completed,a.content_text contentText
            from course_lessons l join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id
            join resources r on r.id=c.legacy_resource_id
            left join lesson_progress p on p.lesson_id=l.id and p.user_id=?
            join lesson_assets a on a.lesson_id=l.id and a.status=1 and a.transcoding_status='ready'
            where c.legacy_resource_id=? and r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
              and ch.status='published' and l.status='published' and l.lesson_type<>'exercise'
              and a.content_text is not null and a.content_text<>''
            order by ch.sort_order,ch.id,l.sort_order,l.id,a.id
            """, userId, courseId);
        Map<Long, Source> sources = new LinkedHashMap<>();
        for (var row : rows) {
            String text = clean(row.get("contentText"));
            if (text.length() < 40) continue;
            long id = number(row.get("lessonId"));
            Source source = sources.computeIfAbsent(id, ignored -> new Source(id, String.valueOf(row.get("lessonTitle")), clamp(number(row.get("progressPercent"))), truth(row.get("completed")) && number(row.get("progressPercent")) >= 100));
            if (!source.text.contains(text)) source.text = (source.text + "\n" + text).strip();
        }
        var aliases = jdbc.queryForList("""
            select kt.lesson_id lessonId,kt.topic_title topic from lesson_knowledge_topics kt
            join course_lessons l on l.id=kt.lesson_id join course_chapters ch on ch.id=l.chapter_id
            join courses c on c.id=ch.course_id where c.legacy_resource_id=? order by kt.lesson_id,kt.topic_title
            """, courseId);
        for (var alias : aliases) {
            Source source = sources.get(number(alias.get("lessonId")));
            if (source != null && !source.title.equals(alias.get("topic"))) source.topics.add(String.valueOf(alias.get("topic")));
        }
        var history = jdbc.queryForList("""
            select id,lesson_id,practice_scope,difficulty,questions_json,answers_json,score
            from ai_practice_sessions where user_id=? and course_id=? and submitted_at is not null
            order by id desc limit 30
            """, userId, courseId);
        List<String> recentStems = new ArrayList<>();
        for (var attempt : history) {
            JsonNode questions = json(attempt.get("questions_json"));
            JsonNode answers = json(attempt.get("answers_json"));
            if (!questions.isArray() || !answers.isArray() || questions.size() != answers.size()) continue;
            for (int index = 0; index < questions.size(); index++) {
                JsonNode question = questions.get(index), answer = answers.get(index);
                if (!question.path("answer").isInt() || !answer.isInt() || answer.asInt() < 0 || answer.asInt() > 3
                    || question.path("answer").asInt() < 0 || question.path("answer").asInt() > 3) continue;
                String stem = question.path("stem").asText();
                if (!stem.isBlank() && recentStems.size() < 20) recentStems.add(stem);
                Source source = historicalSource(question, attempt, sources);
                if (source == null) continue;
                boolean correct = question.path("answer").asInt() == answer.asInt();
                source.results.add(new Feedback(correct, String.valueOf(attempt.get("difficulty"))));
                String reference = question.path("reference").asText();
                if (!correct && source.wrongReference.isEmpty() && !reference.isBlank() && normalize(source.text).contains(normalize(reference))) source.wrongReference = reference;
            }
        }
        List<Source> candidates = sources.values().stream()
            .filter(source -> fixedLessonId == null || source.id == fixedLessonId)
            .filter(source -> !studiedOnly || source.progress > 0 || source.completed || !source.results.isEmpty())
            .sorted(Comparator.comparingInt(Source::priority).reversed().thenComparingInt(source -> source.results.size())
                .thenComparing(Comparator.comparingInt((Source source) -> source.progress).reversed()).thenComparingLong(source -> source.id))
            .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("eligible", !candidates.isEmpty());
        summary.put("totalTextLessons", sources.size());
        summary.put("studiedTextLessons", sources.values().stream().filter(source -> source.progress > 0 || source.completed).count());
        summary.put("historySessionLimit", HISTORY_LIMIT);
        summary.put("weakTopics", sources.values().stream().filter(source -> source.accuracy() != null && source.accuracy() < 60)
            .sorted(Comparator.comparingInt(source -> source.accuracy())).limit(3)
            .map(source -> Map.of("lessonId", source.id, "title", source.title, "accuracy", source.accuracy(), "questionCount", source.recentCount())).toList());
        if (candidates.isEmpty()) {
            summary.put("reason", sources.isEmpty() ? "本课程还没有可用于出题的文字讲义。" : "先学习本课程的图文讲义，再开始按学习情况出题。");
            summary.put("difficulty", "基础");
            return new Plan(summary, null, List.copyOf(sources.values()), "", "", null, recentStems);
        }
        Source selected = candidates.get(0);
        String difficulty = requestedLevel == null ? selected.difficulty() : requestedLevel;
        Integer accuracy = selected.accuracy();
        String strategy = accuracy == null ? "FIRST_ASSESSMENT" : accuracy < 60 ? "REVIEW_WRONG" : accuracy < 80 ? "CONSOLIDATE" : "ADVANCE";
        String reason = accuracy == null
            ? "“" + selected.title + "”尚无有效答题记录，先用" + difficulty + "题检查理解。"
            : "你在“" + selected.title + "”最近 " + selected.recentCount() + " 题的正确率为 " + accuracy + "%，本轮"
                + (accuracy < 60 ? "优先复习易错内容。" : accuracy < 80 ? "继续巩固应用方法。" : "尝试" + difficulty + "题并检查迁移应用。");
        summary.put("strategy", strategy);
        summary.put("reason", reason);
        summary.put("difficulty", difficulty);
        summary.put("sourceLessonId", selected.id);
        summary.put("sourceLessonTitle", selected.title);
        summary.put("knowledgePoints", selected.knowledgePoints());
        summary.put("lessonProgressPercent", selected.progress);
        summary.put("recentAccuracy", accuracy);
        summary.put("recentQuestionCount", selected.recentCount());
        summary.put("answeredQuestions", selected.results.size());
        String evidence = excerpt(selected, requestedLevel == null ? 1600 : 1800);
        if (requestedLevel != null) {
            // A course test may cover the whole course while putting the student's weaker material first.
            for (Source source : candidates) if (source != selected && evidence.length() < 2700)
                evidence += "\n" + excerpt(source, Math.min(600, 2800 - evidence.length()));
        }
        String focus = focus(selected.text, difficulty, recentStems);
        Target target = target(selected, difficulty, focus, recentStems);
        if (target != null && !normalize(evidence).contains(normalize(target.reference())))
            evidence = "【" + selected.title + "】\n" + target.reference() + "\n" + evidence;
        return new Plan(summary, selected, List.copyOf(sources.values()), evidence, focus, target, recentStems);
    }

    /** Give small models a source-backed answer target, rather than asking them to paraphrase a fact. */
    private Target target(Source source, String difficulty, String authoredFocus, List<String> recentStems) {
        String[] sections = source.text.split("(?m)(?=^## )");
        String heading = "进阶".equals(difficulty) ? "## 参考" : "巩固".equals(difficulty) ? "## 案例" : "## 核心讲解";
        String material = Arrays.stream(sections).filter(section -> section.startsWith(heading)).findFirst()
            .orElseGet(() -> Arrays.stream(sections).filter(section -> section.startsWith("## 深入讲解") || section.startsWith("## 核心讲解")).findFirst().orElse(source.text));
        if ("基础".equals(difficulty) && !source.wrongReference.isEmpty()) material = source.wrongReference;
        List<String> sentences = Arrays.stream(material.split("(?<=[。！？!?])|\\n+"))
            .map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#") && line.length() <= 150).toList();
        if (sentences.isEmpty()) return null;
        if ("基础".equals(difficulty) || "巩固".equals(difficulty)) {
            // Only ask about an explicit relation in the source; the answer is never invented.
            for (String verb : List.of("可能指向", "指向", "用于", "用来", "保存", "表示", "描述", "表达", "约束", "需要", "决定", "是")) {
                for (String sentence : sentences) {
                    String[] clauses = sentence.split("[，；。]");
                    for (int clauseIndex = 0; clauseIndex < clauses.length; clauseIndex++) {
                        String clause = clauses[clauseIndex];
                        int position = clause.indexOf(verb);
                        if (position < 2) continue;
                        String subject = clause.substring(0, position).trim(), answer = clause.substring(position + verb.length()).trim();
                        if (subject.length() > 35 || subject.endsWith("可") || subject.endsWith("不") || answer.length() < 2 || answer.length() > 45) continue;
                        String context = "巩固".equals(difficulty) && clauseIndex > 0 ? clauses[0] + "时，" : "";
                        String question = context + subject + verb + "什么？";
                        if (recentStems.stream().limit(3).anyMatch(stem -> normalize(stem).equals(normalize(question)))) continue;
                        return new Target(question, answer, sentence);
                    }
                }
            }
        }
        String question = !authoredFocus.isBlank() && (authoredFocus.endsWith("？") || authoredFocus.endsWith("?"))
            ? authoredFocus : "根据本节讲义，关于“" + source.title + "”哪种做法正确？";
        String reference = sentences.get(0);
        String correct = reference.split("[，；。]", 2)[0].trim();
        if (correct.length() < 4) correct = reference;
        return new Target(question, correct, reference);
    }

    private Source historicalSource(JsonNode question, Map<String, Object> attempt, Map<Long, Source> sources) {
        long explicit = question.path("sourceLessonId").asLong(0);
        if (explicit > 0) return sources.get(explicit);
        if ("lesson".equals(Objects.toString(attempt.get("practice_scope"), "lesson"))) return sources.get(number(attempt.get("lesson_id")));
        String reference = question.path("reference").asText();
        if (reference.isBlank()) return null;
        var matches = sources.values().stream().filter(source -> normalize(source.text).contains(normalize(reference))).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String excerpt(Source source, int budget) {
        if (budget <= 30) return "";
        StringBuilder output = new StringBuilder("【" + source.title + "】\n");
        if (!source.wrongReference.isEmpty() && source.wrongReference.length() + output.length() < budget / 2) output.append(source.wrongReference).append('\n');
        String[] sections = source.text.split("(?m)(?=^## )");
        Arrays.sort(sections, Comparator.comparingInt(PracticePersonalizationService::sectionPriority));
        for (String section : sections) {
            // Whole source sentences stay intact for server-side grounding. Long introductions come last.
            for (String paragraph : section.split("\\n+")) {
                if (output.length() + paragraph.length() + 1 <= budget) output.append(paragraph).append('\n');
                else for (String sentence : paragraph.split("(?<=[。！？!?])"))
                    if (output.length() + sentence.length() + 1 <= budget) output.append(sentence).append('\n');
            }
        }
        return output.toString();
    }

    private static int sectionPriority(String section) {
        if (section.startsWith("## 深入讲解") || section.startsWith("## 核心讲解")) return 0;
        if (section.startsWith("## 从基础到进阶") || section.startsWith("## 练习与思考")) return 1;
        if (section.startsWith("## 分步案例") || section.startsWith("## 案例推演")) return 2;
        if (section.startsWith("## 参考")) return 3;
        return 4;
    }

    private String focus(String text, String level, List<String> stems) {
        var matcher = Pattern.compile(Pattern.quote(level) + "：(.+?)(?=巩固：|进阶：|\\n|$)").matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (stems.stream().noneMatch(stem -> normalize(stem).equals(normalize(candidate)))) return candidate;
        }
        return "";
    }

    private JsonNode json(Object value) {
        try { var parsed = mapper.readTree(Objects.toString(value, "[]")); return parsed == null ? mapper.createArrayNode() : parsed; }
        catch (Exception malformed) { return mapper.createArrayNode(); }
    }
    private static String clean(Object value) { return Objects.toString(value, "").replaceAll("<[^>]*>", " ").strip(); }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    private static int clamp(long value) { return (int) Math.max(0, Math.min(100, value)); }
    private static boolean truth(Object value) { return Boolean.TRUE.equals(value) || number(value) == 1; }
    private static String normalize(String text) { return text.replaceAll("[\\s\\p{P}]+", ""); }

    private record Feedback(boolean correct, String level) {}
    private static final class Source {
        final long id;
        final String title;
        final int progress;
        final boolean completed;
        String text = "", wrongReference = "";
        final Set<String> topics = new LinkedHashSet<>();
        final List<Feedback> results = new ArrayList<>();
        Source(long id, String title, int progress, boolean completed) { this.id = id; this.title = title; this.progress = progress; this.completed = completed; }
        int recentCount() { return Math.min(RECENT_QUESTIONS, results.size()); }
        Integer accuracy() { return results.isEmpty() ? null : (int) Math.round(results.stream().limit(RECENT_QUESTIONS).filter(Feedback::correct).count() * 100.0 / recentCount()); }
        int priority() { Integer accuracy = accuracy(); return accuracy == null ? (completed ? 60 : 50) : accuracy < 60 ? 100 : accuracy < 80 ? 80 : 20; }
        List<String> knowledgePoints() { return topics.isEmpty() ? List.of(title) : topics.stream().limit(3).toList(); }
        String difficulty() {
            Integer accuracy = accuracy();
            if (accuracy == null || accuracy < 60 || progress < 50) return "基础";
            if (accuracy < 80) return "巩固";
            String level = "基础";
            for (int index = 0; index < 2; index++) {
                String current = LEVELS.get(index);
                var feedback = results.stream().filter(result -> current.equals(result.level())).limit(RECENT_QUESTIONS).toList();
                if (feedback.size() < 2 || feedback.stream().filter(Feedback::correct).count() * 100.0 / feedback.size() < 80) break;
                level = LEVELS.get(index + 1);
            }
            return progress < 80 && "进阶".equals(level) ? "巩固" : level;
        }
    }

    public record Target(String question, String correctPhrase, String reference) {}

    public record Plan(Map<String, Object> summary, Source selected, List<Source> sources, String evidence, String focus, Target target, List<String> recentStems) {
        public boolean eligible() { return selected != null; }
        public long lessonId() { return selected == null ? 0 : selected.id; }
        public String difficulty() { return String.valueOf(summary.get("difficulty")); }
        public String promptContext() {
            return "学习情况：已开始学习 " + summary.get("studiedTextLessons") + "/" + summary.get("totalTextLessons") + " 个有文字资料的课时。"
                + summary.get("reason") + "本次重点：" + summary.get("sourceLessonTitle") + "。";
        }
        public void requireFresh(List<Map<String, Object>> questions) {
            for (var question : questions) if (recentStems.stream().limit(3).anyMatch(stem -> normalize(stem).equals(normalize(String.valueOf(question.get("stem"))))))
                throw new BusinessException(502, "本次题目与近期练习重复，请重新生成。");
        }
        public List<Map<String, Object>> annotate(List<Map<String, Object>> questions) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (var question : questions) {
                String reference = normalize(String.valueOf(question.get("reference")));
                Source source = selected != null && normalize(selected.text).contains(reference) ? selected
                    : sources.stream().filter(item -> normalize(item.text).contains(reference)).findFirst().orElse(null);
                if (source == null) throw new BusinessException(502, "题目没有匹配到本课程的有效资料，请重新生成。");
                Map<String, Object> tagged = new LinkedHashMap<>(question);
                tagged.put("sourceLessonId", source.id);
                tagged.put("sourceLessonTitle", source.title);
                tagged.put("knowledgePoints", source.knowledgePoints());
                tagged.put("personalization", new LinkedHashMap<>(summary));
                result.add(tagged);
            }
            return result;
        }
    }
}
