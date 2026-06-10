/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PredicateEvaluatorTest {

    private final Speaker rustam = new Speaker("rustam",
            Set.of("en", "nb"),
            Set.of("EU", "NORDICS", "GLOBAL"),
            Set.of("java", "cloud-native", "ai", "devex"),
            List.of(),
            Set.of("SPEAKER", "ADMIN"));

    private final Speaker maria = new Speaker("maria",
            Set.of("en", "es"),
            Set.of("EU", "GLOBAL"),
            Set.of("mobile", "ai"),
            List.of(),
            Set.of("SPEAKER"));

    private final ConfEvent jfokus = new ConfEvent(
            "sessionize:jfokus-2027", "JFokus 2027", "Stockholm",
            LocalDate.of(2027, 2, 8), LocalDate.of(2026, 10, 15),
            Set.of("java", "cloud-native", "devex"),
            Set.of("en"),
            "https://jfokus.se", "https://sessionize.com/jfokus-2027",
            Set.of("nordics", "java"),
            Instant.now());

    private final ConfEvent flutterES = new ConfEvent(
            "sessionize:flutterconf-es-2026", "FlutterConf España", "Madrid",
            LocalDate.of(2026, 11, 12), LocalDate.of(2026, 7, 1),
            Set.of("mobile"),
            Set.of("es"),
            "https://flutterconf.es", "https://sessionize.com/flutterconf-es-2026",
            Set.of("eu", "mobile"),
            Instant.now());

    private static final String CANONICAL =
            "speaker.languages intersects event.languages && speaker.tracks intersects event.tracks";

    @Test
    void rustam_can_see_jfokus() {
        assertThat(PredicateEvaluator.evaluate(CANONICAL, rustam, jfokus)).isTrue();
    }

    @Test
    void rustam_cannot_see_spanish_flutter_conf() {
        assertThat(PredicateEvaluator.evaluate(CANONICAL, rustam, flutterES)).isFalse();
    }

    @Test
    void maria_can_see_spanish_flutter_conf() {
        assertThat(PredicateEvaluator.evaluate(CANONICAL, maria, flutterES)).isTrue();
    }

    @Test
    void maria_cannot_see_jfokus() {
        assertThat(PredicateEvaluator.evaluate(CANONICAL, maria, jfokus)).isFalse();
    }

    @Test
    void empty_predicate_allows() {
        assertThat(PredicateEvaluator.evaluate("", rustam, jfokus)).isTrue();
    }

    @Test
    void contains_against_string_literal() {
        assertThat(PredicateEvaluator.evaluate("event.tags contains \"nordics\"", rustam, jfokus)).isTrue();
        assertThat(PredicateEvaluator.evaluate("event.tags contains \"asia\"",    rustam, jfokus)).isFalse();
    }

    @Test
    void equality_on_id_path() {
        assertThat(PredicateEvaluator.evaluate("event.id == \"sessionize:jfokus-2027\"", rustam, jfokus)).isTrue();
        assertThat(PredicateEvaluator.evaluate("event.id != \"sessionize:jfokus-2027\"", rustam, jfokus)).isFalse();
    }

    @Test
    void malformed_predicate_fails_closed() {
        assertThat(PredicateEvaluator.evaluate("speaker.languages WAT event.languages", rustam, jfokus)).isFalse();
    }
}

