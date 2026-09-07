package com.learning.platform.ai.domain;

import java.util.Map;
import java.util.function.Consumer;

public interface AiModelProvider {
    String providerCode();
    String modelCode();
    Map<String, Object> health();
    AiUsage chatStream(AiProviderRequest request, Consumer<String> chunkConsumer,
                       AiCancellation cancellation) throws Exception;
}
