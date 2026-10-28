package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import java.util.*;

public final class LearningStages {
    public static final List<String> CODES = List.of("foundation", "intermediate", "advanced");
    private LearningStages() { }
    public static String require(Object value) {
        String stage = Objects.toString(value, "").trim();
        if (!CODES.contains(stage)) throw new BusinessException(400, "请选择基础课程、中期课程或进阶课程。");
        return stage;
    }
    public static String label(String stage) {
        return switch (stage) { case "advanced" -> "进阶课程"; case "intermediate" -> "中期课程"; default -> "基础课程"; };
    }
    public static String infer(Object title, Object level) {
        String text = Objects.toString(title, "").toLowerCase(Locale.ROOT);
        if (text.matches(".*(高级|进阶|性能|调优|并发|多线程|jvm|微服务|分布式|kubernetes|深度学习|transformer).*")) return "advanced";
        if (text.matches(".*(实战|项目|框架|spring|jdbc|集合|io流|io 流|异常|flask|django|事务|索引|部署|spark).*")) return "intermediate";
        String difficulty = Objects.toString(level, "");
        if (difficulty.equals("项目实战")) return "advanced";
        if (difficulty.equals("进阶") || difficulty.equals("中级")) return "intermediate";
        return "foundation";
    }
    public static Map<String, Object> describe(String stage) { return Map.of("code", stage, "label", label(stage)); }
}
