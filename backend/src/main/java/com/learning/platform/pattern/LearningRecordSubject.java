package com.learning.platform.pattern;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LearningRecordSubject {
    private final List<LearningRecordObserver> observers;

    public LearningRecordSubject(List<LearningRecordObserver> observers) {
        this.observers = observers;
    }

    public void notifyCreated(Map<String, Object> user, Map<String, Object> record) {
        for (LearningRecordObserver observer : observers) {
            observer.onRecordCreated(user, record);
        }
    }
}
