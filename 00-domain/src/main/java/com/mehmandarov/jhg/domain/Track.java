/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

/**
 * A conference track/topic, e.g. {@code java}, {@code cloud-native}, {@code ai}, {@code devex}.
 *
 * @param id   stable identifier used in policy predicates (lowercase, dash-separated)
 * @param name human-readable label for UI
 */
public record Track(String id, String name) {
    public Track {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Track id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Track name must not be blank");
        }
    }
}

