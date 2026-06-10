/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.capstone.it;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runtime smoke test for the portable capstone. Boots the pre-built
 * {@code confspeakerhub/capstone:dev} image (Open Liberty + the five portable
 * gems + MP-JWT) and exercises:
 *
 * <ol>
 *   <li>Health endpoint is reachable.</li>
 *   <li>{@code GET /api/events/{id}/analyze} streams the gem #5 SSE analysis
 *       (tldr / topic / done) against a seeded event from {@code feeds.json}.</li>
 * </ol>
 *
 * <p>The image must be built before running this test:
 * <pre>
 *   docker build -t confspeakerhub/capstone:dev .
 * </pre>
 *
 * <p>The live-ingest / dedupe / action flow now lives in the
 * {@code bonus-reactive-messaging} module (verified separately against Redpanda),
 * so it is no longer part of the portable capstone smoke test.
 *
 * <p>Skipped automatically if Docker is not available on the host. Set
 * {@code JHG_SKIP_IT=1} to skip even when Docker is present (useful in CI matrices).
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "JHG_SKIP_IT", matches = "^(?!1$).*", disabledReason = "JHG_SKIP_IT=1")
class CapstoneIT {

    private static final String IMAGE_NAME = "confspeakerhub/capstone:dev";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static GenericContainer<?> app;
    private static String baseUrl;
    private static String bearerToken;   // lazily fetched on first use

    @BeforeAll
    static void boot() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available — skipping capstone IT");

        app = new GenericContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withExposedPorts(9080)
                .withEnv("JHG_LLM_MODE", "stub")   // gem #5 deterministic in CI
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("csh-app")))
                // Liberty's "smarter planet" line means features are loaded and apps deployed.
                .waitingFor(Wait.forLogMessage(".*CWWKF0011I.*", 1))
                .withStartupTimeout(Duration.ofMinutes(3));

        app.start();
        baseUrl = "http://" + app.getHost() + ":" + app.getMappedPort(9080);
    }

    @AfterAll
    static void shutdown() {
        if (app != null) app.stop();
    }

    // ---- tests --------------------------------------------------------------

    @Test
    void health_returnsUp() throws Exception {
        HttpResponse<String> resp = get("/api/health");
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("\"status\":\"UP\"");
    }


    @Test
    void analyzeStream_yieldsTldrTagsAndPortfolioTopics() throws Exception {
        // Pick a seeded event id from feeds.json. Stub mode (JHG_LLM_MODE=stub
        // set on the container) makes this deterministic without Ollama.
        String eventId = "sessionize:jfokus-2027";
        BlockingQueue<String> liveLines = new ArrayBlockingQueue<>(128);

        CompletableFuture<Void> subscriber = HTTP.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/events/"
                                + java.net.URLEncoder.encode(eventId, java.nio.charset.StandardCharsets.UTF_8)
                                + "/analyze"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + bearerToken())
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofLines()
        ).thenAccept(resp -> resp.body().forEach(line -> {
            if (!line.isEmpty()) liveLines.offer(line);
        }));

        boolean sawTldr = false, sawTopic = false, sawDone = false;
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && !(sawTldr && sawTopic && sawDone)) {
            String line = liveLines.poll(500, TimeUnit.MILLISECONDS);
            if (line == null) continue;
            if (line.startsWith("event: tldr"))  sawTldr  = true;
            if (line.startsWith("event: topic")) sawTopic = true;
            if (line.startsWith("event: done"))  sawDone  = true;
        }
        subscriber.cancel(true);

        assertThat(sawTldr).as("tldr SSE event").isTrue();
        assertThat(sawTopic).as("at least one portfolio topic").isTrue();
        assertThat(sawDone).as("terminal done event").isTrue();
    }

    // ---- helpers ------------------------------------------------------------

    private static synchronized String bearerToken() throws Exception {
        if (bearerToken == null) {
            HttpResponse<String> resp = get("/api/dev/token?user=rustam");
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("dev token endpoint returned " + resp.statusCode());
            }
            bearerToken = resp.body().trim();
        }
        return bearerToken;
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}

