/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.events;

import com.mehmandarov.jhg.domain.ConfEvent;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the conferences ConfSpeakerHub knows about.
 *
 * <p>{@link InMemoryEventStore} is the in-memory implementation seeded from
 * {@code feeds.json}. The bonus persistence layer can swap in a JPA-backed
 * implementation transparently to callers — that's the whole point of the
 * interface.
 */
public interface EventStore {

    /** All known events, newest-ingested first. */
    List<ConfEvent> all();

    /** Lookup by {@link ConfEvent#id() event id}. */
    Optional<ConfEvent> byId(String id);
}

