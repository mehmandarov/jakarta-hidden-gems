/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

/**
 * One LLM-suggested talk pulled from the speaker's existing portfolio (NOT
 * generated). Used by gem #5 to populate the "talks that fit this conference"
 * panel in the dashboard.
 *
 * <p>The {@code title} must already exist in {@link Speaker#portfolio()};
 * {@code whyItFits} is the LLM's one-sentence justification.
 */
public record TopicSuggestion(String title, String whyItFits) {
    public TopicSuggestion {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("TopicSuggestion.title must not be blank");
        }
        if (whyItFits == null) {
            whyItFits = "";
        }
    }
}

