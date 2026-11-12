package com.learning.platform.ai.context;

import com.learning.platform.courseware.CoursewareAssetVersion;
import com.learning.platform.courseware.CoursewareFileParser;
import java.io.IOException;
import com.learning.platform.exception.BusinessException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Bounded keyword retrieval over confirmed, current documents in this lesson only. */
@Service
public class CoursewareRetrievalService {

    private static final int MAX_TERMS = 24;
    private static final int MAX_CANDIDATES = 120;
    private static final int MAX_VERSION_CHECKS = 16;
    private static final int MAX_RESULTS = 4;
    private static final Pattern WORDS = Pattern.compile("[a-z][a-z0-9+#.]{1,30}|[\\p{IsHan}]{2,}");
    private static final Set<String> STOP_WORDS = Set.of("what", "why", "how", "the", "this", "that", "with", "about");
    private static final String FIELDS =
        "select ck.id chunkId,ck.document_id documentId,ck.location_type locationType," +
        "ck.location_index locationIndex,ck.text_content textContent,d.public_course_id courseId," +
        "d.lesson_id lessonId,d.file_name fileName,d.version_hash versionHash,d.asset_version documentAssetVersion," +
        "d.status documentStatus,a.id assetId,a.file_url fileUrl,a.file_size fileSize," +
        "a.asset_type assetType,a.updated_at assetUpdatedAt,a.status assetStatus," +
        "a.transcoding_status assetReadiness,coalesce(a.content_text,'') contentText ";
    private static final String JOIN =
        "from courseware_chunks ck join courseware_documents d on d.id=ck.document_id " +
        "join lesson_assets a on a.id=d.asset_id and a.lesson_id=d.lesson_id " +
        "join course_lessons l on l.id=d.lesson_id join course_chapters ch on ch.id=l.chapter_id " +
        "join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id ";
    private static final String PUBLIC_SCOPE =
        "d.public_course_id=? and d.lesson_id=? and r.id=d.public_course_id " +
        "and r.status=1 and r.review_status='approved' and r.lifecycle_status='active' " +
        "and l.status='published' and ch.status='published' and l.lesson_type='document' ";
    private final JdbcTemplate jdbc;
    private final CoursewareFileParser files;

    public CoursewareRetrievalService(JdbcTemplate jdbc, CoursewareFileParser files) {
        this.jdbc = jdbc;
        this.files = files;
    }

    public List<CoursewareSnippet> retrieve(long courseId, long lessonId, String question) {
        List<String> terms = terms(question);
        if (terms.isEmpty()) return Collections.emptyList();
        List<Object> arguments = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        for (String term : terms) {
            scores.add("case when lower(ck.text_content) like ? escape '!' then 1 else 0 end");
            arguments.add("%" + escapeLike(term) + "%");
        }
        arguments.add(courseId);
        arguments.add(lessonId);
        String sql = FIELDS + ",(" + String.join("+", scores) + ") relevanceScore " + JOIN
            + "where " + PUBLIC_SCOPE
            + "and d.status='CONFIRMED' and a.status=1 and a.asset_type='document' and a.transcoding_status='ready' "
            + "order by relevanceScore desc,d.confirmed_at desc,ck.chunk_index,ck.id limit " + MAX_CANDIDATES;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, arguments.toArray());
        List<Map<String, Object>> ranked = new ArrayList<>();
        Map<Long, String> fileHashes = new HashMap<>();
        for (Map<String, Object> row : rows) {
            if (score(text(row.get("textContent")), terms) > 0 && metadataCurrent(row)) ranked.add(row);
        }
        ranked.sort(Comparator.<Map<String, Object>>comparingInt(row -> score(text(row.get("textContent")), terms)).reversed());
        List<CoursewareSnippet> selected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int checked = 0;
        for (Map<String, Object> row : ranked) {
            String content = text(row.get("textContent"));
            if (content.isEmpty() || seen.contains(content)) continue;
            if (++checked > MAX_VERSION_CHECKS) break;
            if (!current(row, fileHashes)) continue;
            seen.add(content);
            selected.add(snippet(row));
            if (selected.size() == MAX_RESULTS) break;
        }
        return selected;
    }

    /** Callers must run the same published-course access check as the lesson player. */
    public Map<String, Object> source(long courseId, long lessonId, long chunkId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            FIELDS + JOIN + "where " + PUBLIC_SCOPE + "and ck.id=?", courseId, lessonId, chunkId);
        if (rows.isEmpty()) throw new BusinessException(404, "这份参考资料不存在或不属于当前课时。");
        Map<String, Object> row = rows.get(0);
        if (!current(row, new HashMap<>())) throw new BusinessException(410, "这份课件已更新、撤回或删除，请重新提问获取当前资料。");
        CoursewareSnippet snippet = snippet(row);
        Map<String, Object> result = snippet.citation();
        result.put("text", snippet.getContent());
        return result;
    }

    private boolean current(Map<String, Object> row, Map<Long, String> fileHashes) {
        return metadataCurrent(row)
            && text(row.get("versionHash")).equals(fileHashes.computeIfAbsent(number(row.get("assetId")), ignored -> fileHash(row)));
    }

    private boolean metadataCurrent(Map<String, Object> row) {
        return "CONFIRMED".equals(text(row.get("documentStatus")))
            && number(row.get("assetStatus")) == 1
            && "document".equals(text(row.get("assetType")))
            && "ready".equals(text(row.get("assetReadiness")))
            && !text(row.get("versionHash")).isEmpty()
            && text(row.get("documentAssetVersion")).equals(CoursewareAssetVersion.of(row));
    }

    private String fileHash(Map<String, Object> row) {
        try {
            return files.hash(files.resolve(text(row.get("fileUrl"))));
        } catch (IOException | BusinessException error) {
            return "";
        }
    }

    private CoursewareSnippet snippet(Map<String, Object> row) {
        return new CoursewareSnippet(number(row.get("chunkId")), text(row.get("documentId")),
            number(row.get("assetId")), number(row.get("courseId")), number(row.get("lessonId")),
            text(row.get("versionHash")), text(row.get("fileName")), text(row.get("locationType")),
            (int) number(row.get("locationIndex")), text(row.get("textContent")));
    }

    static List<String> terms(String question) {
        String normalized = Normalizer.normalize(question == null ? "" : question, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("是什么|为什么|如何|怎么|什么|哪些|请问|解释|一下|介绍|当前|课程|课时|能否|可以", " ");
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = WORDS.matcher(normalized);
        while (matcher.find() && tokens.size() < MAX_TERMS) {
            String word = matcher.group();
            if (STOP_WORDS.contains(word)) continue;
            if (Character.UnicodeScript.of(word.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = word.codePoints().toArray();
                if (points.length <= 16) tokens.add(word);
                for (int i = 0; i < points.length - 1 && tokens.size() < MAX_TERMS; i++) {
                    tokens.add(new String(points, i, 2));
                }
            } else tokens.add(word);
        }
        return new ArrayList<>(tokens).subList(0, Math.min(tokens.size(), MAX_TERMS));
    }

    private int score(String content, List<String> terms) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) if (normalized.contains(term)) score++;
        return score;
    }

    private String escapeLike(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0L; }
}
