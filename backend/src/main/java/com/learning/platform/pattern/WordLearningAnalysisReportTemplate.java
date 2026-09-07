package com.learning.platform.pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class WordLearningAnalysisReportTemplate extends AbstractReportTemplate {
    public File generateWithAiAnalysis(Map<String, Object> user, RecommendationContext context,
                                       String aiAnalysis, File reportDir) throws IOException {
        ensureDirectory(reportDir);
        File target = createTargetFile(user, reportDir);
        writeAiReport(user, context, aiAnalysis, target);
        return target;
    }

    @Override
    protected void writeReport(Map<String, Object> user, RecommendationContext context, File target) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); FileOutputStream out = new FileOutputStream(target)) {
            addParagraph(doc, "Student Learning Analysis Report", true, 18);
            addParagraph(doc, "Student: " + user.get("name"), false, 12);
            addParagraph(doc, "1. Learning Data Summary", true, 14);
            addParagraph(doc, "The system recorded " + context.getRecordCount()
                    + " learning actions. Current preferred subject: " + context.getSubject()
                    + ". Average progress is " + context.getAverageProgress()
                    + "%, and recent 7-day activity count is " + context.getRecentRecordCount() + ".", false, 12);
            addParagraph(doc, "2. Problem Diagnosis", true, 14);
            addParagraph(doc, weakNodeSummary(context), false, 12);
            addParagraph(doc, "3. Improvement Advice", true, 14);
            addParagraph(doc, "Use the loop of foundation review -> example practice -> project implementation -> stage assessment. "
                    + resourceSummary(context), false, 12);
            doc.write(out);
        }
    }

    private void writeAiReport(Map<String, Object> user, RecommendationContext context,
                               String aiAnalysis, File target) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); FileOutputStream out = new FileOutputStream(target)) {
            addParagraph(doc, "知度课堂 AI 学习分析报告", true, 18);
            addParagraph(doc, "学生：" + user.get("name") + "    学习方向：" + context.getSubject(), false, 11);
            addParagraph(doc, "学习记录：" + context.getRecordCount() + " 条    近 7 天活动："
                    + context.getRecentRecordCount() + " 条    平均进度：" + context.getAverageProgress() + "%", false, 11);
            addParagraph(doc, "AI 个性化分析", true, 14);
            String[] paragraphs = (aiAnalysis == null ? "" : aiAnalysis).replace("\r", "").split("\n");
            for (String paragraph : paragraphs) {
                String text = paragraph.trim();
                if (!text.isEmpty()) addParagraph(doc, text, isHeading(text), 12);
            }
            doc.write(out);
        }
    }

    private boolean isHeading(String text) {
        return text.length() <= 30 && (text.matches("^[一二三四五六七八九十]+[、.．].*")
                || text.matches("^\\d+[、.．].*") || text.endsWith("："));
    }

    private String weakNodeSummary(RecommendationContext context) {
        List<Map<String, Object>> weakNodes = context.getWeakNodes();
        if (weakNodes == null || weakNodes.isEmpty()) {
            return "No obvious weak knowledge node was found. Continue learning steadily and keep progress records fresh.";
        }
        Map<String, Object> first = weakNodes.get(0);
        return "The most urgent weak area is " + first.get("node")
                + ", with average progress around " + first.get("avgProgress")
                + "%. Review this node before moving to harder project tasks.";
    }

    private String resourceSummary(RecommendationContext context) {
        List<Map<String, Object>> resources = context.getResources();
        if (resources == null || resources.isEmpty()) {
            return "Choose one foundation resource and finish it before expanding the learning path.";
        }
        StringBuilder builder = new StringBuilder("Recommended resources: ");
        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(resources.get(i).get("title"));
        }
        builder.append(".");
        return builder.toString();
    }

    private void addParagraph(XWPFDocument doc, String text, boolean bold, int size) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(size);
        run.setFontFamily("Microsoft YaHei");
    }
}
