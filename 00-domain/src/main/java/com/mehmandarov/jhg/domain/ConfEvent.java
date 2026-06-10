/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * One conference event ingested from a feed (Sessionize, Papercall, conference
 * RSS, …). The shape the access policy filters on (gem #2), the SSE broadcaster
 * fans out (gem #4), and the LLM analyses (gem #5).
 *
 * @param id           stable, source-prefixed identifier (e.g. {@code sessionize:jfokus-2027})
 * @param name         conference name (e.g. {@code "JFokus 2027"})
 * @param location     city + country (e.g. {@code "Stockholm, Sweden"})
 * @param eventDate    when the conference happens
 * @param cfpDeadline  when CFP closes; the speaker has to apply before this
 * @param tracks       {@link Track#id() Track ids} this conference covers
 * @param languages    ISO 639-1 codes accepted in submissions (e.g. {@code en}, {@code es})
 * @param websiteUrl   public landing page
 * @param cfpUrl       the URL to actually submit to (when {@code APPLY} is clicked)
 * @param tags         free-form tags (region, theme, …); the LLM may suggest more in gem #5
 * @param ingestedAt   timestamp of ingest (used for dedupe + UI ordering)
 */
public record ConfEvent(String id,
                        String name,
                        String location,
                        LocalDate eventDate,
                        LocalDate cfpDeadline,
                        Set<String> tracks,
                        Set<String> languages,
                        String websiteUrl,
                        String cfpUrl,
                        Set<String> tags,
                        Instant ingestedAt) {

    public ConfEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ConfEvent.id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ConfEvent.name must not be blank");
        }
        tracks    = tracks    == null ? Set.of() : Set.copyOf(tracks);
        languages = languages == null ? Set.of() : Set.copyOf(languages);
        tags      = tags      == null ? Set.of() : Set.copyOf(tags);
    }
}

