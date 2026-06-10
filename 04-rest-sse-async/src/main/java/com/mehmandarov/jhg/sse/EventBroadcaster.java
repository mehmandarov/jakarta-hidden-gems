/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.sse;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.EventCachedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application-scoped fan-out for the {@code /api/events/stream} endpoint.
 * Holds the singleton {@link SseBroadcaster} and observes
 * {@link EventCachedEvent} from the JCA module — both threads of life
 * (subscribe + broadcast) flow through here.
 *
 * <p>Two seams worth pointing at on stage:
 * <ol>
 *   <li><strong>Decoupled producer.</strong> The JCA MDB doesn't know SSE
 *       exists; it fires a CDI event and walks away. Drop this module from
 *       the WAR and the MDB still works (zero observers = silent drop).</li>
 *   <li><strong>One {@link SseBroadcaster}, N sinks.</strong> Every browser
 *       tab is just another {@link SseEventSink} registered with the same
 *       broadcaster. The "three tabs light up at once" demo is mechanical
 *       once the broadcaster exists.</li>
 * </ol>
 */
@ApplicationScoped
public class EventBroadcaster {

    private static final Logger LOG = System.getLogger(EventBroadcaster.class.getName());

    /** Lazy because {@link Sse} is only injectable inside a JAX-RS resource. */
    private final AtomicReference<SseBroadcaster> broadcaster = new AtomicReference<>();
    private final AtomicReference<Sse> sseRef = new AtomicReference<>();

    /**
     * Called from {@code EventStreamResource} on the first subscription so we
     * can lazily create the broadcaster from the resource's {@link Sse}
     * factory. Idempotent.
     */
    public SseBroadcaster ensureBroadcaster(Sse sse) {
        sseRef.compareAndSet(null, sse);
        SseBroadcaster current = broadcaster.get();
        if (current != null) return current;
        SseBroadcaster created = sse.newBroadcaster();
        if (broadcaster.compareAndSet(null, created)) {
            created.onError((sink, ex) ->
                    LOG.log(Level.WARNING, "[sse] sink error: {0}", ex.getMessage()));
            created.onClose(sink ->
                    LOG.log(Level.DEBUG, "[sse] sink closed"));
            return created;
        }
        return broadcaster.get();
    }

    public void register(SseEventSink sink, Sse sse) {
        ensureBroadcaster(sse).register(sink);
    }

    /**
     * CDI observer fired by the JCA MDB once a fresh {@link ConfEvent} is in
     * the cache. Builds an {@code OutboundSseEvent} named {@code "event"}
     * with a JSON-B serialised body and broadcasts it to every registered
     * sink. Drops the broadcast silently if no one is subscribed yet.
     */
    public void onEventCached(@Observes EventCachedEvent ev) {
        SseBroadcaster b = broadcaster.get();
        Sse sse = sseRef.get();
        if (b == null || sse == null) return;   // no subscribers yet

        ConfEvent c = ev.event();
        OutboundSseEvent out = sse.newEventBuilder()
                .id(c.id())
                .name("event")
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(ConfEvent.class, c)
                .build();
        b.broadcast(out);
        LOG.log(Level.INFO, "[sse] broadcast {0}", c.id());
    }

    @PreDestroy
    void shutdown() {
        SseBroadcaster b = broadcaster.get();
        if (b != null) b.close();
    }
}

