/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

import java.util.Set;

/**
 * One talk in a speaker's portfolio. Used by gem #5 (JSON-P + LLM): the prompt
 * is grounded in the speaker's actual portfolio so the model recommends real,
 * existing talks rather than generating new generic ones.
 *
 * <p>This is the integrity guarantee: matchmaking, not generation.
 *
 * @param title        public talk title (e.g. "Hidden Gems of Jakarta EE")
 * @param abstractText 2-3 sentence abstract; what the LLM matches against
 * @param tags         conference-track-style tags (e.g. {@code java, cloud-native, ai})
 */
public record TalkTitle(String title, String abstractText, Set<String> tags) {
    public TalkTitle {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("TalkTitle.title must not be blank");
        }
        if (abstractText == null) {
            throw new IllegalArgumentException("TalkTitle.abstractText must not be null");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}

