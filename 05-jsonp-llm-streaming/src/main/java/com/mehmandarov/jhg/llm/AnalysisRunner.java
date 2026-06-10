/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.io.StringReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the LLM analysis off the request thread. Lives in a separate bean so
 * that {@link Asynchronous} fires through the CDI proxy — a direct
 * {@code this.method()} call inside {@link AnalyzeResource} would bypass the
 * proxy and run inline.
 */
@ApplicationScoped
public class AnalysisRunner {

    private static final Logger LOG = System.getLogger(AnalysisRunner.class.getName());

    @Inject OllamaClient ollama;
    @Inject StubAnalyzer stub;
    @Inject PromptBuilder prompts;

    /**
     * Streams the LLM (or stub) analysis as SSE frames. The JAX-RS resource
     * method returns immediately; {@code SseEventSink} stays open until we
     * call {@link SseEventSink#close()}.
     */
    @Asynchronous
    public void run(Sse sse, SseEventSink sink, ConfEvent event, Speaker speaker) {
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

    /**
     * Validate one inner JSON line, route to the right SSE event name, and
     * downgrade unknown / hallucinated topics to {@code warn}.
     */
    private void dispatch(Sse sse, SseEventSink sink, String jsonLine, Set<String> validTitles) {
        JsonObject obj;
        try (JsonParser p = Json.createParser(new StringReader(jsonLine))) {
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

    private static void closeQuietly(SseEventSink sink) {
        try { sink.close(); } catch (Exception ignored) { }
    }
}
