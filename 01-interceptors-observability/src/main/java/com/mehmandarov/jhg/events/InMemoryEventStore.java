/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.events;

import com.mehmandarov.jhg.domain.ConfEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads {@code feeds.json} from the classpath at startup and serves the
 * resulting {@link ConfEvent}s in-memory. No database required for the demo.
 */
@ApplicationScoped
public class InMemoryEventStore implements EventStore {

    private static final Logger LOG = System.getLogger(InMemoryEventStore.class.getName());
    private static final String CLASSPATH_RESOURCE = "feeds.json";

    private Map<String, ConfEvent> byId = Map.of();
    private List<ConfEvent> ordered = List.of();

    @PostConstruct
    void load() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                LOG.log(Level.WARNING, "[events] {0} not found on classpath; store is empty",
                        CLASSPATH_RESOURCE);
                return;
            }
            try (JsonReader reader = Json.createReader(in)) {
                JsonObject root = reader.readObject();
                JsonArray events = root.getJsonArray("events");

                Instant baseIngest = Instant.now();
                List<ConfEvent> parsed = new java.util.ArrayList<>(events.size());
                for (int i = 0; i < events.size(); i++) {
                    parsed.add(parse(events.getJsonObject(i),
                            baseIngest.minusSeconds(i)));   // stable, deterministic ordering
                }
                this.ordered = parsed.stream()
                        .sorted(Comparator.comparing(ConfEvent::ingestedAt).reversed())
                        .toList();
                this.byId = ordered.stream()
                        .collect(Collectors.toUnmodifiableMap(ConfEvent::id, e -> e));

                LOG.log(Level.INFO, "[events] loaded {0} events from {1}",
                        ordered.size(), CLASSPATH_RESOURCE);
            }
        } catch (IOException e) {
            LOG.log(Level.ERROR, "[events] failed to read " + CLASSPATH_RESOURCE, e);
        }
    }

    @Override
    public List<ConfEvent> all() {
        return ordered;
    }

    @Override
    public Optional<ConfEvent> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    // ----- parsing helpers ----------------------------------------------------

    private static ConfEvent parse(JsonObject o, Instant ingestedAt) {
        return new ConfEvent(
                o.getString("id"),
                o.getString("name"),
                o.getString("location", ""),
                LocalDate.parse(o.getString("eventDate")),
                LocalDate.parse(o.getString("cfpDeadline")),
                stringSet(o, "tracks"),
                stringSet(o, "languages"),
                o.getString("websiteUrl", ""),
                o.getString("cfpUrl", ""),
                stringSet(o, "tags"),
                ingestedAt
        );
    }

    private static Set<String> stringSet(JsonObject o, String key) {
        if (!o.containsKey(key) || o.isNull(key)) return Set.of();
        return o.getJsonArray(key).stream()
                .filter(v -> v.getValueType() == JsonValue.ValueType.STRING)
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .collect(Collectors.toUnmodifiableSet());
    }
}

