/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of the parsed policy file. Replaced atomically by
 * {@link AccessPolicy} on hot reload.
 */
public record PolicyRuleSet(List<PolicyRule> rules) {

    public PolicyRuleSet {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /** Empty rule set ≡ deny everything. */
    public static PolicyRuleSet empty() { return new PolicyRuleSet(List.of()); }

    public static PolicyRuleSet parse(InputStream in) {
        try (JsonReader r = Json.createReader(in)) {
            return parse(r.readObject());
        }
    }

    public static PolicyRuleSet parse(Reader in) {
        try (JsonReader r = Json.createReader(in)) {
            return parse(r.readObject());
        }
    }

    private static PolicyRuleSet parse(JsonObject root) {
        JsonArray array = root.getJsonArray("rules");
        if (array == null) return empty();
        List<PolicyRule> parsed = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonObject o = array.getJsonObject(i);
            parsed.add(new PolicyRule(
                    o.getString("subject"),
                    o.getString("action"),
                    o.getString("resource"),
                    o.getString("predicate", "")
            ));
        }
        return new PolicyRuleSet(parsed);
    }
}

