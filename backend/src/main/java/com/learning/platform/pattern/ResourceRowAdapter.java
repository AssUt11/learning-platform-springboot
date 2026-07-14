package com.learning.platform.pattern;

import java.util.HashMap;
import java.util.Map;

public class ResourceRowAdapter {
    private final Map<String, Object> row;

    public ResourceRowAdapter(Map<String, Object> row) {
        this.row = row;
    }

    public Map<String, Object> toViewModel() {
        Map<String, Object> view = new HashMap<String, Object>(row);
        if (!view.containsKey("teacher") || view.get("teacher") == null) {
            view.put("teacher", "Platform Teacher");
        }
        if (!view.containsKey("minutes") || view.get("minutes") == null) {
            view.put("minutes", 45);
        }
        return view;
    }
}
