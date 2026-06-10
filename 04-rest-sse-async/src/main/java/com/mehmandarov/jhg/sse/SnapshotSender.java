/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.sse;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.events.EventStore;
import com.mehmandarov.jhg.events.EventVisibility;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.security.Principal;

/**
 * Sends the initial event snapshot to a newly connected SSE client off the
 * request thread. Lives in a separate bean so that {@link Asynchronous} fires
 * through the CDI proxy — a direct {@code this.method()} call inside
 * {@link EventStreamResource} would bypass the proxy and run inline.
 */
@ApplicationScoped
public class SnapshotSender {

    @Inject
    EventStore store;

    @Inject
    EventVisibility visibility;

    /**
     * Streams all currently visible events as {@code "snapshot"} SSE frames,
     * then sends a {@code "snapshot-end"} frame. Runs off the request thread
     * via container-managed concurrency — no {@link java.util.concurrent.ExecutorService}
     * to manage.
     */
    @Asynchronous
    public void send(Sse sse, SseEventSink sink, Principal principal) {
        try {
            for (ConfEvent c : store.all()) {
                if (!visibility.canSee(principal, c)) continue;
                sink.send(sse.newEventBuilder()
                        .id(c.id())
                        .name("snapshot")
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .data(ConfEvent.class, c)
                        .build());
            }
            sink.send(sse.newEventBuilder()
                    .name("snapshot-end")
                    .data(String.class, "ready")
                    .build());
        } catch (RuntimeException ignored) {
            // Sink already closed (client navigated away). The broadcaster's
            // onError handler will clean up.
        }
    }
}
