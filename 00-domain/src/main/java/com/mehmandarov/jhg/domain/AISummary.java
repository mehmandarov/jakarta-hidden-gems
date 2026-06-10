/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

import java.util.List;

/**
 * The streaming output of gem #5 — emitted chunk-by-chunk from an
 * {@link java.io.Reader} via {@code jakarta.json.stream.JsonParser}.
 *
 * <p>Each in-flight summary may have any subset of fields populated;
 * {@code done = true} signals the final chunk.
 *
 * @param eventId          {@link ConfEvent#id() event id} this summary describes
 * @param tldr             one-sentence summary; grows token-by-token
 * @param suggestedTags    LLM-proposed extra tags (often a superset of {@link ConfEvent#tags()})
 * @param suggestedTopics  three picks from the speaker's portfolio that fit
 * @param done             whether this is the final chunk
 */
public record AISummary(String eventId,
                        String tldr,
                        List<String> suggestedTags,
                        List<TopicSuggestion> suggestedTopics,
                        boolean done) {

    public AISummary {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("AISummary.eventId must not be blank");
        }
        tldr             = tldr             == null ? ""        : tldr;
        suggestedTags    = suggestedTags    == null ? List.of() : List.copyOf(suggestedTags);
        suggestedTopics  = suggestedTopics  == null ? List.of() : List.copyOf(suggestedTopics);
    }
}

