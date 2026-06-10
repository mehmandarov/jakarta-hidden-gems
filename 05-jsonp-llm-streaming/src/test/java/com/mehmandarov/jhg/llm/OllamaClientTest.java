/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientTest {

    @Test
    void parseOllamaChunk_extractsResponseField() {
        String wire = "{\"model\":\"qwen2.5:7b\",\"response\":\"hello\",\"done\":false}";
        assertThat(OllamaClient.parseOllamaChunk(wire)).isEqualTo("hello");
    }

    @Test
    void parseOllamaChunk_handlesEscapes() {
        String wire = "{\"response\":\"line1\\nline2\",\"done\":false}";
        assertThat(OllamaClient.parseOllamaChunk(wire)).isEqualTo("line1\nline2");
    }

    @Test
    void parseOllamaChunk_returnsNullOnGarbage() {
        assertThat(OllamaClient.parseOllamaChunk("not json at all")).isNull();
    }

    @Test
    void drainCompletedLines_emitsOnePerNewline_keepsTail() {
        StringBuilder buf = new StringBuilder("{\"a\":1}\n{\"b\":2}\n{\"c\":");
        List<String> emitted = new ArrayList<>();
        OllamaClient.drainCompletedLines(buf, emitted::add);

        assertThat(emitted).containsExactly("{\"a\":1}", "{\"b\":2}");
        assertThat(buf.toString()).isEqualTo("{\"c\":");
    }

    @Test
    void drainCompletedLines_emptyBuffer_isNoOp() {
        StringBuilder buf = new StringBuilder();
        List<String> emitted = new ArrayList<>();
        OllamaClient.drainCompletedLines(buf, emitted::add);
        assertThat(emitted).isEmpty();
    }
}

