/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.sse;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.events.EventStore;
import com.mehmandarov.jhg.events.EventVisibility;
import com.mehmandarov.jhg.interceptors.Timed;
import com.mehmandarov.jhg.interceptors.Traced;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.security.Principal;

/**
 * {@code GET /api/events/stream} — long-lived {@code text/event-stream} that:
 * <ol>
 *   <li>Sends a one-shot snapshot of all currently visible events on connect
 *       (so reloading the page doesn't lose state).</li>
 *   <li>Stays open and receives every subsequent {@code "event"} broadcast
 *       from {@link EventBroadcaster}.</li>
 * </ol>
 *
 * <p>The snapshot is fired off the request thread via Jakarta Concurrency's
 * {@code @Asynchronous} (Web Profile — portable, not the full-profile EJB
 * variant) — the talk's "scene 4" hook into the second half of the gem name.
 * The client closes the connection (browser navigation away) and the
 * broadcaster removes the sink.
 */
@Path("/events/stream")
@ApplicationScoped
@Traced
@Timed
public class EventStreamResource {

    @Inject
    EventBroadcaster broadcaster;

    @Inject
    EventStore store;

    @Inject
    EventVisibility visibility;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context Sse sse,
                          @Context SseEventSink sink,
                          @Context SecurityContext security) {
        broadcaster.register(sink, sse);

        Principal principal = security == null ? null : security.getUserPrincipal();
        sendSnapshotAsync(sse, sink, principal);
    }

    /**
     * Fire the initial snapshot off the request thread — keeps the response
     * latency low and lets the broadcaster start delivering live events
     * immediately. Container-managed asynchrony, no {@link java.util.concurrent.ExecutorService}
     * to manage.
     */
    @Asynchronous
    public void sendSnapshotAsync(Sse sse, SseEventSink sink, Principal principal) {
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

