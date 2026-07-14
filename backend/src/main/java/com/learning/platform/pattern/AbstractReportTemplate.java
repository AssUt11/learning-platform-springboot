package com.learning.platform.pattern;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public abstract class AbstractReportTemplate {
    public final File generate(Map<String, Object> user, RecommendationContext context, File reportDir) throws IOException {
        ensureDirectory(reportDir);
        File target = createTargetFile(user, reportDir);
        writeReport(user, context, target);
        return target;
    }

    protected void ensureDirectory(File reportDir) {
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
    }

    protected File createTargetFile(Map<String, Object> user, File reportDir) {
        return new File(reportDir, "learning-analysis-" + user.get("id") + "-" + System.currentTimeMillis() + ".docx");
    }

    protected abstract void writeReport(Map<String, Object> user, RecommendationContext context, File target) throws IOException;
}
