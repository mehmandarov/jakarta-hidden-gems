/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.sse;

import com.mehmandarov.jhg.interceptors.Timed;
import com.mehmandarov.jhg.interceptors.Traced;
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
    SnapshotSender snapshotSender;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context Sse sse,
                          @Context SseEventSink sink,
                          @Context SecurityContext security) {
        broadcaster.register(sink, sse);

        Principal principal = security == null ? null : security.getUserPrincipal();
        snapshotSender.send(sse, sink, principal);
    }
}

