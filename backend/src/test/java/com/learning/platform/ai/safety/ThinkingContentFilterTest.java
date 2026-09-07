package com.learning.platform.ai.safety;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThinkingContentFilterTest {
    @Test
    void leavesNormalContentVisible() {
        ThinkingContentFilter filter = new ThinkingContentFilter();
        assertEquals("正常回答", filter.accept("正常回答"));
        assertEquals("", filter.finish());
    }

    @Test
    void removesThinkingBlockFromOneChunk() {
        ThinkingContentFilter filter = new ThinkingContentFilter();
        assertEquals("答案开始答案结束", filter.accept("答案开始<think>内部推理</think>答案结束"));
        assertEquals("", filter.finish());
    }

    @Test
    void removesThinkingTagsSplitAcrossChunks() {
        ThinkingContentFilter filter = new ThinkingContentFilter();
        assertEquals("可见", filter.accept("可见<thi"));
        assertEquals("", filter.accept("nk>不得泄漏</th"));
        assertEquals("继续回答", filter.accept("ink>继续回答"));
        assertEquals("", filter.finish());
    }

    @Test
    void discardsUnclosedThinkingContentAtEnd() {
        ThinkingContentFilter filter = new ThinkingContentFilter();
        assertEquals("回答", filter.accept("回答<THINK>未闭合推理"));
        assertEquals("", filter.finish());
    }
}
