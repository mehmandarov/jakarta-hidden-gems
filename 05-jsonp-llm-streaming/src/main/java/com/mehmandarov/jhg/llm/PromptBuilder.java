/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import com.mehmandarov.jhg.domain.TalkTitle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Builds the grounded analysis prompt fed to Ollama. The whole point of
 * scene 5 of the talk: <strong>the LLM only ever matches against the
 * speaker's existing portfolio.</strong> No talk titles get generated;
 * they're picked from a closed set.
 *
 * <p>Output schema (one JSON object per line — strict NDJSON, no markdown,
 * no preamble):
 * <pre>
 * {"type":"tldr","value":"&lt;one-sentence summary&gt;"}
 * {"type":"tag","value":"&lt;tag&gt;"}                    (3-5 lines)
 * {"type":"topic","title":"&lt;exact portfolio title&gt;","whyItFits":"…"} (exactly 3)
 * {"type":"done"}
 * </pre>
 *
 * <p>The {@code title} field MUST come verbatim from {@link Speaker#portfolio()}
 * — the {@code AnalyzeResource} will downgrade any topic that doesn't match
 * to a warning event so the audience can see when a model misbehaves.
 */
@ApplicationScoped
public class PromptBuilder {

    /** System prompt — pinned model behaviour, structured output, no slop. */
    public String system() {
        return """
                You analyze conference events for a single speaker.
                Decide whether the speaker should pitch a talk to this conference,
                and if so which existing talks from their portfolio fit best.

                Output rules — read carefully and follow exactly:
                1. Output ONLY newline-delimited JSON (NDJSON). No markdown, no prose.
                2. Each line is one complete JSON object, terminated by a newline.
                3. Allowed object shapes:
                   {"type":"tldr","value":"<one sentence>"}
                   {"type":"tag","value":"<lowercase tag>"}
                   {"type":"topic","title":"<EXACT title from portfolio>","whyItFits":"<one sentence>"}
                   {"type":"done"}
                4. Emit exactly one tldr, then 3-5 tags, then EXACTLY 3 topics, then done.
                5. The "title" field MUST be copied verbatim from speaker.portfolio[].title.
                   Never invent talks. Never paraphrase.
                """;
    }

    /** User prompt — the grounded payload: the conference + the speaker's talks. */
    public String user(ConfEvent event, Speaker speaker) {
        JsonObject payload = Json.createObjectBuilder()
                .add("event", eventJson(event))
                .add("speaker", speakerJson(speaker))
                .build();
        return "Analyze this event for this speaker:\n" + payload.toString();
    }

    private static JsonObject eventJson(ConfEvent e) {
        return Json.createObjectBuilder()
                .add("id", e.id())
                .add("name", e.name())
                .add("location", nz(e.location()))
                .add("eventDate", e.eventDate().toString())
                .add("cfpDeadline", e.cfpDeadline().toString())
                .add("tracks", arrayOf(e.tracks()))
                .add("languages", arrayOf(e.languages()))
                .add("tags", arrayOf(e.tags()))
                .add("websiteUrl", nz(e.websiteUrl()))
                .add("cfpUrl", nz(e.cfpUrl()))
                .build();
    }

    private static JsonObject speakerJson(Speaker s) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("username", s.username())
                .add("languages", arrayOf(s.languages()))
                .add("regions", arrayOf(s.regions()))
                .add("tracks", arrayOf(s.tracks()));

        JsonArrayBuilder portfolio = Json.createArrayBuilder();
        for (TalkTitle t : s.portfolio()) {
            portfolio.add(Json.createObjectBuilder()
                    .add("title", nz(t.title()))
                    .add("abstract", nz(t.abstractText()))
                    .add("tags", arrayOf(t.tags())));
        }
        b.add("portfolio", portfolio);
        return b.build();
    }

    private static jakarta.json.JsonArray arrayOf(java.util.Collection<String> values) {
        JsonArrayBuilder b = Json.createArrayBuilder();
        if (values != null) values.forEach(b::add);
        return b.build();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}

