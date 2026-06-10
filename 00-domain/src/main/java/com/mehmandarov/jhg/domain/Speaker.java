/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

import java.util.List;
import java.util.Set;

/**
 * The user of ConfSpeakerHub. The talk's persona is Rustam Mehmandarov; the
 * demo seeds two speakers ({@code rustam} and {@code maria}) with disjoint
 * languages and tracks so the access policy in gem #2 has something to filter on.
 *
 * @param username   stable login name; also used in audit records and gist file names
 * @param languages  ISO 639-1 codes the speaker presents in (e.g. {@code en}, {@code nb})
 * @param regions    region codes the speaker travels to (e.g. {@code EU}, {@code NORDICS}, {@code GLOBAL})
 * @param tracks     {@link Track#id() Track ids} the speaker is interested in
 * @param portfolio  the speaker's existing talks; this is what the LLM matches against in gem #5
 * @param roles      Jakarta Security roles (e.g. {@code SPEAKER}, {@code ADMIN})
 */
public record Speaker(String username,
                      Set<String> languages,
                      Set<String> regions,
                      Set<String> tracks,
                      List<TalkTitle> portfolio,
                      Set<String> roles) {

    public Speaker {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Speaker.username must not be blank");
        }
        languages = languages == null ? Set.of() : Set.copyOf(languages);
        regions   = regions   == null ? Set.of() : Set.copyOf(regions);
        tracks    = tracks    == null ? Set.of() : Set.copyOf(tracks);
        portfolio = portfolio == null ? List.of() : List.copyOf(portfolio);
        roles     = roles     == null ? Set.of() : Set.copyOf(roles);
    }
}

