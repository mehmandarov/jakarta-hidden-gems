/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.auth;

import com.mehmandarov.jhg.domain.Speaker;
import com.mehmandarov.jhg.domain.TalkTitle;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads {@code speakers.json} once at startup. Both Jakarta Security
 * (in-memory {@code IdentityStore}) and the ABAC visibility filter look up
 * speakers through here.
 *
 * <p>Demo passwords intentionally simple:
 * {@code rustam:hiddengems}, {@code maria:flutter}.
 */
@ApplicationScoped
public class SpeakerRegistry {

    private static final Logger LOG = System.getLogger(SpeakerRegistry.class.getName());
    private static final String CLASSPATH_RESOURCE = "speakers.json";

    /** Demo passwords — clearly fake, easy to mention on a slide. */
    private static final Map<String, String> PASSWORDS = Map.of(
            "rustam", "hiddengems",
            "maria",  "flutter"
    );

    private Map<String, Speaker> byUsername = Map.of();

    @PostConstruct
    void load() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                LOG.log(Level.WARNING, "[auth] {0} not on classpath; registry empty", CLASSPATH_RESOURCE);
                return;
            }
            try (JsonReader reader = Json.createReader(in)) {
                JsonObject root = reader.readObject();
                JsonArray speakers = root.getJsonArray("speakers");
                List<Speaker> parsed = new ArrayList<>(speakers.size());
                for (int i = 0; i < speakers.size(); i++) {
                    parsed.add(parseSpeaker(speakers.getJsonObject(i)));
                }
                byUsername = parsed.stream()
                        .collect(Collectors.toUnmodifiableMap(Speaker::username, s -> s));
                LOG.log(Level.INFO, "[auth] loaded {0} speakers: {1}",
                        byUsername.size(), byUsername.keySet());
            }
        } catch (Exception e) {
            LOG.log(Level.ERROR, "[auth] failed to load " + CLASSPATH_RESOURCE, e);
        }
    }

    public Optional<Speaker> byUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    public Optional<Speaker> byPrincipal(Principal principal) {
        return principal == null ? Optional.empty() : byUsername(principal.getName());
    }

    /** True if {@code (username, password)} is a known seed credential. */
    public boolean validate(String username, String password) {
        String expected = PASSWORDS.get(username);
        return expected != null && expected.equals(password) && byUsername.containsKey(username);
    }

    public Set<String> rolesOf(String username) {
        return byUsername(username).map(Speaker::roles).orElse(Set.of());
    }

    // ---- parsing ------------------------------------------------------------

    private static Speaker parseSpeaker(JsonObject o) {
        return new Speaker(
                o.getString("username"),
                stringSet(o, "languages"),
                stringSet(o, "regions"),
                stringSet(o, "tracks"),
                parsePortfolio(o.getJsonArray("portfolio")),
                stringSet(o, "roles")
        );
    }

    private static List<TalkTitle> parsePortfolio(JsonArray arr) {
        if (arr == null) return List.of();
        List<TalkTitle> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonObject t = arr.getJsonObject(i);
            out.add(new TalkTitle(
                    t.getString("title"),
                    t.getString("abstractText", ""),
                    stringSet(t, "tags")
            ));
        }
        return List.copyOf(out);
    }

    private static Set<String> stringSet(JsonObject o, String key) {
        if (!o.containsKey(key) || o.isNull(key)) return Set.of();
        return o.getJsonArray(key).stream()
                .filter(v -> v.getValueType() == JsonValue.ValueType.STRING)
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .collect(Collectors.toUnmodifiableSet());
    }
}

