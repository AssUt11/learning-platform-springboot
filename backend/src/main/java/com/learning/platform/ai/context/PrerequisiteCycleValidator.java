package com.learning.platform.ai.context;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PrerequisiteCycleValidator {
    public boolean createsCycle(List<Map<String, Object>> relations, String prerequisiteKey, String targetKey) {
        if (prerequisiteKey.equals(targetKey)) return true;
        Map<String, Set<String>> adjacency = new HashMap<String, Set<String>>();
        for (Map<String, Object> relation : relations) {
            String from = text(relation.get("prerequisiteKey"));
            String to = text(relation.get("targetKey"));
            if (!from.isEmpty() && !to.isEmpty()) {
                adjacency.computeIfAbsent(from, ignored -> new HashSet<String>()).add(to);
            }
        }
        adjacency.computeIfAbsent(prerequisiteKey, ignored -> new HashSet<String>()).add(targetKey);
        ArrayDeque<String> pending = new ArrayDeque<String>();
        Set<String> visited = new HashSet<String>();
        pending.add(targetKey);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (prerequisiteKey.equals(current)) return true;
            if (!visited.add(current)) continue;
            for (String next : adjacency.getOrDefault(current, java.util.Collections.emptySet())) {
                pending.addLast(next);
            }
        }
        return false;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
