/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.messaging;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.EventCachedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes {@code confs.events.incoming} from Redpanda via the
 * {@code confs-events-in} channel, dedupes by event id, and fires the
 * {@link EventCachedEvent} CDI event that gem #4 (SSE) observes.
 *
 * <p>The portable counterpart to the JCA module's {@code EventConsumerMDB}.
 * Dedupe here is an in-memory set (at-least-once delivery + idempotent
 * consumer); the JCA escalation uses a JTA-atomic JPA dedupe table when you
 * need exactly-once semantics with a transactional resource.
 */
@ApplicationScoped
public class EventConsumer {

    private static final Logger LOG = System.getLogger(EventConsumer.class.getName());

    private final Jsonb jsonb = JsonbBuilder.create();
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    @Inject
    Event<EventCachedEvent> cached;

    @Incoming(EventChannels.EVENTS_IN)
    public void onEvent(String json) {
        consume(json);
    }

    /**
     * Parse + dedupe + (optionally) fire the cached event. Returns {@code true}
     * if this was a fresh event. Package-private so it can be unit-tested
     * without a CDI container.
     */
    boolean consume(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        ConfEvent ev = jsonb.fromJson(json, ConfEvent.class);
        if (ev == null || ev.id() == null || ev.id().isBlank()) {
            return false;
        }
        boolean fresh = seen.add(ev.id());
        LOG.log(Level.INFO, "[reactive] {0} {1}", fresh ? "cached" : "duplicate-skipped", ev.id());
        if (fresh && cached != null) {
            cached.fire(new EventCachedEvent(ev));
        }
        return fresh;
    }
}

