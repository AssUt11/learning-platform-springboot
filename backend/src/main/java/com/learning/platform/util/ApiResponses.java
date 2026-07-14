package com.learning.platform.util;

import java.util.HashMap;
import java.util.Map;

public final class ApiResponses {
    private ApiResponses() {
    }

    public static Map<String, Object> ok() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        return result;
    }

    public static Map<String, Object> of(String key, Object value) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put(key, value);
        return result;
    }
}
