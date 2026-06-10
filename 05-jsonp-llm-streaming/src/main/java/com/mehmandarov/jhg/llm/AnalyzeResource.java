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
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.io.StringReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.security.Principal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    private static final Logger LOG = System.getLogger(AnalyzeResource.class.getName());

    @Inject EventStore events;
    @Inject SpeakerRegistry speakers;
    @Inject OllamaClient ollama;
    @Inject StubAnalyzer stub;
    @Inject PromptBuilder prompts;

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

        runAsync(sse, sink, event, speaker);
    }

    /**
     * Off the request thread via Jakarta Concurrency {@code @Asynchronous}
     * (Web Profile, portable). The JAX-RS resource method returns immediately;
     * {@code SseEventSink} keeps working until we call {@link SseEventSink#close()}.
     */
    @Asynchronous
    public void runAsync(Sse sse, SseEventSink sink, ConfEvent event, Speaker speaker) {
        Set<String> validTitles = speaker.portfolio() == null ? Set.of() :
                speaker.portfolio().stream()
                        .map(t -> t.title() == null ? "" : t.title())
                        .collect(Collectors.toCollection(HashSet::new));

        boolean useStub = "stub".equalsIgnoreCase(System.getenv("JHG_LLM_MODE"))
                || !ollama.reachable();

        try {
            if (useStub) {
                LOG.log(Level.INFO, "[analyze] stub mode for event={0}", event.id());
                stub.streamAnalysis(event, speaker, line -> dispatch(sse, sink, line, validTitles));
            } else {
                LOG.log(Level.INFO, "[analyze] live LLM for event={0}", event.id());
                ollama.streamAnalysis(prompts.system(), prompts.user(event, speaker),
                        line -> dispatch(sse, sink, line, validTitles));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[analyze] failed: {0}", e.getMessage());
            sink.send(sse.newEventBuilder()
                    .name("warn")
                    .data(String.class, "analysis failed: " + e.getMessage())
                    .build());
        } finally {
            sink.send(sse.newEventBuilder().name("done").data(String.class, "ok").build());
            closeQuietly(sink);
        }
    }

    private static void closeQuietly(SseEventSink sink) {
        try { sink.close(); } catch (Exception ignored) { }
    }

    /**
     * Validate the inner JSON line with {@link JsonParser}, route to the
     * right SSE event name, and downgrade unknown / hallucinated topics to
     * {@code warn} so the talk's "grounded vs slop" callback stays honest.
     */
    private void dispatch(Sse sse, SseEventSink sink, String jsonLine, Set<String> validTitles) {
        JsonObject obj;
        try (JsonParser p = Json.createParser(new StringReader(jsonLine))) {
            // Advance to the first START_OBJECT event.
            while (p.hasNext()) {
                if (p.next() == JsonParser.Event.START_OBJECT) break;
            }
            obj = p.getObject();
        } catch (RuntimeException e) {
            sink.send(sse.newEventBuilder()
                    .name("warn")
                    .data(String.class, "non-JSON line: " + jsonLine)
                    .build());
            return;
        }

        String type = Optional.ofNullable(obj.getString("type", null)).orElse("");
        switch (type) {
            case "tldr"  -> emit(sse, sink, "tldr",  obj.getString("value", ""));
            case "tag"   -> emit(sse, sink, "tag",   obj.getString("value", ""));
            case "topic" -> {
                String title = obj.getString("title", "");
                String why   = obj.getString("whyItFits", "");
                if (!validTitles.contains(title)) {
                    sink.send(sse.newEventBuilder()
                            .name("warn")
                            .data(String.class, "model invented title: " + title)
                            .build());
                    return;
                }
                sink.send(sse.newEventBuilder()
                        .name("topic")
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .data(String.class,
                                Json.createObjectBuilder()
                                        .add("title", title)
                                        .add("whyItFits", why)
                                        .build()
                                        .toString())
                        .build());
            }
            case "done"  -> { /* terminal — finally-block sends the canonical done */ }
            default      -> emit(sse, sink, "warn", "unknown type: " + type);
        }
    }

    private static void emit(Sse sse, SseEventSink sink, String name, String value) {
        sink.send(sse.newEventBuilder().name(name).data(String.class, value).build());
    }
}

