package com.learning.platform.ai.safety;

/**
 * Removes accidental Qwen-style thinking blocks even when tags are split across stream chunks.
 * Ollama's structured message.thinking field is discarded separately by the provider.
 */
public class ThinkingContentFilter {
    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";
    private final StringBuilder pending = new StringBuilder();
    private boolean thinking;

    public synchronized String accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) return "";
        pending.append(chunk);
        StringBuilder visible = new StringBuilder();
        while (pending.length() > 0) {
            String marker = thinking ? CLOSE : OPEN;
            int index = indexOfIgnoreCase(pending, marker);
            if (index >= 0) {
                if (!thinking) visible.append(pending, 0, index);
                pending.delete(0, index + marker.length());
                thinking = !thinking;
                continue;
            }
            int carry = longestMarkerPrefixSuffix(pending, marker);
            int consumable = pending.length() - carry;
            if (consumable <= 0) break;
            if (!thinking) visible.append(pending, 0, consumable);
            pending.delete(0, consumable);
        }
        return visible.toString();
    }

    public synchronized String finish() {
        String visible = thinking ? "" : pending.toString();
        pending.setLength(0);
        return visible;
    }

    private int indexOfIgnoreCase(CharSequence source, String target) {
        String lower = source.toString().toLowerCase();
        return lower.indexOf(target);
    }

    private int longestMarkerPrefixSuffix(CharSequence source, String marker) {
        int max = Math.min(source.length(), marker.length() - 1);
        for (int length = max; length > 0; length--) {
            String suffix = source.subSequence(source.length() - length, source.length()).toString();
            if (marker.regionMatches(true, 0, suffix, 0, length)) return length;
        }
        return 0;
    }
}
