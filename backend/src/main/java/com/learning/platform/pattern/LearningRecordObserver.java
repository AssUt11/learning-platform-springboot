package com.learning.platform.pattern;

import java.util.Map;

public interface LearningRecordObserver {
    void onRecordCreated(Map<String, Object> user, Map<String, Object> record);
}
