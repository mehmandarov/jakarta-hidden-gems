/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ElPredicateEngineTest {

    private final ElPredicateEngine el = new ElPredicateEngine();

    private final Speaker rustam = new Speaker(
            "rustam",
            Set.of("en", "nb"),
            Set.of("EU", "NORDICS"),
            Set.of("java", "cloud-native", "ai"),
            List.of(),
            Set.of("SPEAKER"));

    private ConfEvent event(Set<String> langs, Set<String> tracks, LocalDate cfp) {
        return new ConfEvent("jfokus-2027", "JFokus", "Stockholm",
                LocalDate.of(2027, 2, 1), cfp,
                tracks, langs, "https://jfokus.se", "https://jfokus.se/cfp",
                Set.of(), Instant.now());
    }

    @Test
    void emptyPredicateAllows() {
        assertThat(el.evaluate("", rustam, event(Set.of("en"), Set.of("java"), LocalDate.now()))).isTrue();
    }

    @Test
    void backwardCompatibleDslIntersectsStillWorks() {
        String dsl = "speaker.languages intersects event.languages && speaker.tracks intersects event.tracks";
        assertThat(el.evaluate(dsl, rustam, event(Set.of("en"), Set.of("java"), LocalDate.now()))).isTrue();
        assertThat(el.evaluate(dsl, rustam, event(Set.of("es"), Set.of("mobile"), LocalDate.now()))).isFalse();
    }

    @Test
    void fullElWithHelperFunctions() {
        // CFP closes in 30 days → allowed; already closed → denied.
        String pred = "fn.daysUntil(event.cfpDeadline) > 7";
        assertThat(el.evaluate(pred, rustam, event(Set.of("en"), Set.of("java"), LocalDate.now().plusDays(30)))).isTrue();
        assertThat(el.evaluate(pred, rustam, event(Set.of("en"), Set.of("java"), LocalDate.now().minusDays(1)))).isFalse();
    }

    @Test
    void sandboxBlocksClassLoaderEscape() {
        assertThat(el.evaluate("'' == ''.getClass().getName()", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
        assertThat(el.evaluate("speaker.languages.class != null", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    @Test
    void translationRewritesInfixOperators() {
        assertThat(ElPredicateEngine.toEl("a intersects b"))
                .isEqualTo("fn.intersects(a, b)");
        assertThat(ElPredicateEngine.toEl("a contains b && c intersects d"))
                .isEqualTo("fn.contains(a, b) && fn.intersects(c, d)");
    }
}

