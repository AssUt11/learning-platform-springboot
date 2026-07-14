package com.learning.platform.pattern;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConsoleLearningRecordObserver implements LearningRecordObserver {
    @Override
    public void onRecordCreated(Map<String, Object> user, Map<String, Object> record) {
        System.out.println("Learning record created. student=" + user.get("id") + ", subject=" + record.get("subject"));
    }
}
