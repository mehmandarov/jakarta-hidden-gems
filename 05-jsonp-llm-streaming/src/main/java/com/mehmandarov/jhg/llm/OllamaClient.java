/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.stream.JsonParser;

import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Tiny Ollama client. Uses {@link HttpClient} for the HTTP transport and
 * Jakarta {@link JsonParser} to incrementally consume the NDJSON stream
 * Ollama returns from {@code /api/generate?stream=true}.
 *
 * <p><strong>Two parsers, two layers:</strong>
 * <ol>
 *   <li><em>Outer</em> NDJSON from Ollama: each line is
 *       {@code {"response":"<chunk>","done":false,...}}. We accumulate the
 *       {@code response} field across lines into a buffer.</li>
 *   <li><em>Inner</em> NDJSON from the model itself: every time our buffer
 *       contains a {@code \n}, we hand the completed line to a
 *       {@link Consumer} as a string. {@code AnalyzeResource} then validates
 *       it again with {@link JsonParser} before pushing it to the SSE sink.</li>
 * </ol>
 *
 * <p>This is the "two parsers, no AI SDK" demo: Jakarta JSON-P does the
 * heavy lifting twice, on two different shapes, with the same eight-line
 * loop.
 */
@ApplicationScoped
public class OllamaClient {

    private static final System.Logger LOG = System.getLogger(OllamaClient.class.getName());

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * @return {@code true} if the configured Ollama URL responds to a HEAD
     *         within the default timeout. {@code AnalyzeResource} uses this
     *         to short-circuit to the stub when there's no model on the wire.
     */
    public boolean reachable() {
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl() + "/api/tags"))
                            .timeout(Duration.ofSeconds(2))
                            .GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stream a generate request to Ollama. Each completed inner JSON line
     * (one of {@code tldr / tag / topic / done}) is handed to {@code onLine}.
     *
     * @param systemPrompt model behaviour (rules + schema)
     * @param userPrompt   the grounded payload (event + speaker)
     * @param onLine       called with each completed inner JSON line
     */
    public void streamAnalysis(String systemPrompt, String userPrompt, Consumer<String> onLine)
            throws Exception {

        String requestBody = Json.createObjectBuilder()
                .add("model", model())
                .add("system", systemPrompt)
                .add("prompt", userPrompt)
                .add("stream", true)
                // Top-level `think` flag (Ollama ≥ 0.4): disables hybrid-thinking
                // models (Qwen 3, DeepSeek-R1 distillates, …) from emitting
                // <think>…</think> preambles before the JSON. Plain non-thinking
                // models like qwen2.5:7b or llama3.2:3b ignore it. Either way,
                // the JSON-P parser sees clean NDJSON from the first byte.
                .add("think", false)
                // Strict-ish decoding: low temperature so the schema rules stick.
                .add("options", Json.createObjectBuilder()
                        .add("temperature", 0.2)
                        .add("num_predict", 600)
                        .build())
                .build()
                .toString();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/generate"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Accept", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ollama HTTP " + resp.statusCode());
        }

        StringBuilder buffer = new StringBuilder();
        try (var in = new java.io.BufferedReader(
                new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {

            String wireLine;
            while ((wireLine = in.readLine()) != null) {
                if (wireLine.isBlank()) continue;
                String chunk = parseOllamaChunk(wireLine);
                if (chunk == null) continue;
                buffer.append(chunk);
                drainCompletedLines(buffer, onLine);
            }
        }
        // Flush any tail without trailing newline — best-effort.
        if (!buffer.isEmpty()) {
            String tail = buffer.toString().trim();
            if (tail.startsWith("{") && tail.endsWith("}")) {
                onLine.accept(tail);
            }
        }
    }

    /**
     * Parse one outer Ollama NDJSON line, return its {@code response} chunk
     * (may be empty). JSON-P does the parsing — no string surgery.
     */
    static String parseOllamaChunk(String wireLine) {
        try (JsonParser p = Json.createParser(new StringReader(wireLine))) {
            String key = null;
            while (p.hasNext()) {
                JsonParser.Event ev = p.next();
                if (ev == JsonParser.Event.KEY_NAME) {
                    key = p.getString();
                } else if (ev == JsonParser.Event.VALUE_STRING && "response".equals(key)) {
                    return p.getString();
                }
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "[ollama] could not parse wire line: {0}", e.getMessage());
        }
        return null;
    }

    /**
     * Walk the buffer, hand each {@code \n}-terminated non-blank line to
     * {@code onLine}, then truncate the buffer to the unfinished tail.
     */
    static void drainCompletedLines(StringBuilder buffer, Consumer<String> onLine) {
        int nl;
        while ((nl = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, nl).trim();
            buffer.delete(0, nl + 1);
            if (!line.isEmpty()) onLine.accept(line);
        }
    }

    // ---- config -------------------------------------------------------------

    static String baseUrl() {
        String env = System.getenv("OLLAMA_BASE_URL");
        return (env == null || env.isBlank()) ? "http://localhost:11434" : env;
    }

    static String model() {
        String env = System.getenv("OLLAMA_MODEL");
        // Default bumped 2026-05 from qwen2.5:7b → qwen3:8b. Qwen3 ships with
        // hybrid thinking on by default; we disable it via the per-request
        // option below (see OllamaClient.streamAnalysis), so the NDJSON
        // streaming demo stays clean. qwen2.5:7b stays as the documented
        // safe-fallback model — set OLLAMA_MODEL to override either way.
        return (env == null || env.isBlank()) ? "qwen3:8b" : env;
    }
}

