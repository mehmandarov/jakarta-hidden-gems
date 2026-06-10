/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import com.mehmandarov.jhg.domain.TalkTitle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Deterministic, ground-truth-only fallback for {@code AnalyzeResource}.
 * Used when {@code JHG_LLM_MODE=stub} or when {@link OllamaClient#reachable()}
 * returns {@code false}, so the talk's scene 5 always lands — even when the
 * conference Wi-Fi has eaten Ollama.
 *
 * <p>Picks the three highest-overlap talks from the speaker's portfolio
 * using {@code |talk.tags ∩ event.tracks ∪ event.tags|} as the score.
 * Emits them as the same NDJSON shape Ollama would have produced — so the
 * caller's parser stays the same.
 */
@ApplicationScoped
public class StubAnalyzer {

    public void streamAnalysis(ConfEvent event, Speaker speaker, Consumer<String> onLine) {
        emit(onLine, "tldr",
                event.name() + " (" + event.eventDate() + ", " + nz(event.location())
                        + ") — CFP closes " + event.cfpDeadline() + ".");

        // Suggested tags = intersection of event tags+tracks with the speaker's tracks.
        Set<String> tagPool = new HashSet<>();
        if (event.tags() != null) tagPool.addAll(event.tags());
        if (event.tracks() != null) tagPool.addAll(event.tracks());
        if (speaker.tracks() != null) tagPool.retainAll(speaker.tracks());
        if (tagPool.isEmpty() && event.tracks() != null) tagPool.addAll(event.tracks());
        tagPool.stream().limit(5).forEach(t -> emit(onLine, "tag", t));

        // Topic suggestions = top 3 portfolio talks by tag overlap.
        Set<String> targetTags = new HashSet<>();
        if (event.tags() != null) targetTags.addAll(event.tags());
        if (event.tracks() != null) targetTags.addAll(event.tracks());

        List<TalkTitle> portfolio = speaker.portfolio() == null ? List.of() : speaker.portfolio();
        portfolio.stream()
                .sorted(Comparator.comparingLong((TalkTitle t) -> score(t, targetTags)).reversed())
                .limit(3)
                .forEach(t -> emitTopic(onLine, t.title(),
                        "Tag overlap with " + event.name() + ": "
                                + intersection(t.tags(), targetTags)));

        emit(onLine, "done", "");
    }

    private static long score(TalkTitle t, Set<String> target) {
        if (t.tags() == null) return 0;
        return t.tags().stream().filter(target::contains).count();
    }

    private static String intersection(Set<String> a, Set<String> b) {
        if (a == null) return "";
        String result = a.stream().filter(b::contains).collect(Collectors.joining(", "));
        return result.isEmpty() ? "(none)" : result;
    }

    private static void emit(Consumer<String> onLine, String type, String value) {
        onLine.accept(Json.createObjectBuilder()
                .add("type", type)
                .add("value", value == null ? "" : value)
                .build()
                .toString());
    }

    private static void emitTopic(Consumer<String> onLine, String title, String why) {
        onLine.accept(Json.createObjectBuilder()
                .add("type", "topic")
                .add("title", nz(title))
                .add("whyItFits", nz(why))
                .build()
                .toString());
    }

    private static String nz(String s) { return s == null ? "" : s; }
}

