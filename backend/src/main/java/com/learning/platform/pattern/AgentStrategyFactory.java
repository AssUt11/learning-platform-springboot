package com.learning.platform.pattern;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentStrategyFactory {
    private final Map<String, AgentStrategy> strategies = new HashMap<String, AgentStrategy>();

    public AgentStrategyFactory(List<AgentStrategy> strategyList) {
        for (AgentStrategy strategy : strategyList) {
            strategies.put(strategy.type(), strategy);
        }
    }

    public AgentStrategy getStrategy(String type) {
        AgentStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown agent type: " + type);
        }
        return strategy;
    }
}
