package com.learning.platform.ai.domain;

import java.util.Map;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;

public interface AiModelProvider {
    /** Some fine-tuned models need deterministic evidence collection before generation. */
    default boolean requiresPrefetchedEvidence() { return false; }
    default JsonNode chatTurn(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                              boolean jsonOutput, AiCancellation cancellation) throws Exception {
        throw new UnsupportedOperationException("Provider does not support tool calling");
    }
    String providerCode();
    String modelCode();
    Map<String, Object> health();
    AiUsage chatStream(AiProviderRequest request, Consumer<String> chunkConsumer,
                       AiCancellation cancellation) throws Exception;
}
