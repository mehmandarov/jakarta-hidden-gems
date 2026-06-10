/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRuleSetTest {

    private static final String JSON = """
        {
          "rules": [
            { "subject": "role:SPEAKER",
              "action":  "GET",
              "resource": "/api/events/*",
              "predicate": "speaker.languages intersects event.languages" },
            { "subject": "role:ADMIN",
              "action":  "*",
              "resource": "/api/admin/*",
              "predicate": "" }
          ]
        }
        """;

    @Test
    void parses_two_rules() {
        var rules = PolicyRuleSet.parse(new StringReader(JSON));
        assertThat(rules.rules()).hasSize(2);
    }

    @Test
    void resource_wildcard_matches_subpaths_only() {
        var rule = PolicyRuleSet.parse(new StringReader(JSON)).rules().getFirst();
        assertThat(rule.resourceMatches("/api/events/abc")).isTrue();
        assertThat(rule.resourceMatches("/api/events"   )).isTrue();   // bare prefix
        assertThat(rule.resourceMatches("/api/eventsX"  )).isFalse();  // no slash boundary
        assertThat(rule.resourceMatches("/api/health"   )).isFalse();
    }

    @Test
    void subject_role_match_is_case_sensitive_and_namespaced() {
        var rule = PolicyRuleSet.parse(new StringReader(JSON)).rules().getFirst();
        assertThat(rule.subjectMatches(Set.of("SPEAKER"))).isTrue();
        assertThat(rule.subjectMatches(Set.of("speaker"))).isFalse();
        assertThat(rule.subjectMatches(Set.of("ADMIN"  ))).isFalse();
    }

    @Test
    void wildcard_action_matches_anything() {
        var rule = PolicyRuleSet.parse(new StringReader(JSON)).rules().get(1);
        assertThat(rule.actionMatches("GET" )).isTrue();
        assertThat(rule.actionMatches("POST")).isTrue();
        assertThat(rule.actionMatches("WAT" )).isTrue();
    }
}

