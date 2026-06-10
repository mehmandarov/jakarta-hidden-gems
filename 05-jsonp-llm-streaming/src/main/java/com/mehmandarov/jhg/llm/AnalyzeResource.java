/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import com.mehmandarov.jhg.auth.SpeakerRegistry;
import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import com.mehmandarov.jhg.events.EventStore;
import com.mehmandarov.jhg.interceptors.Timed;
import com.mehmandarov.jhg.interceptors.Traced;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.security.Principal;

/**
 * {@code GET /api/events/{id}/analyze} — streams the LLM analysis to the browser as SSE.
 * Streams the LLM (or the stub fallback) analysis to the browser as Server-Sent
 * Events: {@code tldr}, {@code tag}, {@code topic}, {@code done}, plus
 * {@code warn} for any topic the model invents that isn't in the speaker's
 * portfolio.
 *
 * <p>Mode selection:
 * <ul>
 *   <li>{@code JHG_LLM_MODE=stub} — always use {@link StubAnalyzer}.</li>
 *   <li>{@code JHG_LLM_MODE=live} (default) — try {@link OllamaClient}; if
 *       Ollama is unreachable, fall back to the stub so the demo never bricks.</li>
 * </ul>
 */
@Path("/events/{id}/analyze")
@ApplicationScoped
@Traced
@Timed
public class AnalyzeResource {

    @Inject EventStore events;
    @Inject SpeakerRegistry speakers;
    @Inject AnalysisRunner runner;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void analyze(@PathParam("id") String eventId,
                        @Context Sse sse,
                        @Context SseEventSink sink,
                        @Context SecurityContext security) {

        ConfEvent event = events.byId(eventId)
                .orElseThrow(() -> new NotFoundException("No event with id=" + eventId));

        Principal p = security == null ? null : security.getUserPrincipal();
        Speaker speaker = speakers.byUsername(p == null ? "rustam" : p.getName())
                .orElseGet(() -> speakers.byUsername("rustam").orElse(null));

        if (speaker == null) {
            sink.send(sse.newEventBuilder()
                    .name("warn")
                    .data(String.class, "no speaker on file")
                    .build());
            closeQuietly(sink);
            return;
        }

        runner.run(sse, sink, event, speaker);
    }

    private static void closeQuietly(SseEventSink sink) {
        try { sink.close(); } catch (Exception ignored) { }
    }
}

