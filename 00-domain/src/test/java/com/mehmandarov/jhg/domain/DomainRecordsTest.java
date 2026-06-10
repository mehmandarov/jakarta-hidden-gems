/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainRecordsTest {

    @Test
    void speaker_normalises_collections_to_immutable_copies() {
        var portfolio = List.of(new TalkTitle("Hidden Gems of Jakarta EE",
                "Five overlooked specs that already do what you reach for elsewhere.",
                Set.of("java", "cloud-native")));

        var rustam = new Speaker("rustam",
                Set.of("en", "nb"),
                Set.of("EU", "NORDICS", "GLOBAL"),
                Set.of("java", "cloud-native", "ai", "devex"),
                portfolio,
                Set.of("SPEAKER"));

        assertThat(rustam.languages()).containsExactlyInAnyOrder("en", "nb");
        assertThatThrownBy(() -> rustam.languages().add("es"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void confEvent_rejects_blank_id() {
        assertThatThrownBy(() -> new ConfEvent("", "JFokus 2027", "Stockholm",
                LocalDate.of(2027, 2, 2), LocalDate.of(2026, 11, 1),
                Set.of("java"), Set.of("en"),
                "https://jfokus.se", "https://sessionize.com/jfokus-2027",
                Set.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aiSummary_defaults_nulls_to_empty_collections() {
        var s = new AISummary("sessionize:jfokus-2027", null, null, null, false);
        assertThat(s.tldr()).isEmpty();
        assertThat(s.suggestedTags()).isEmpty();
        assertThat(s.suggestedTopics()).isEmpty();
        assertThat(s.done()).isFalse();
    }

    @Test
    void speakerAction_requires_kind_and_at() {
        assertThatThrownBy(() -> new SpeakerAction("evt-1", "rustam", null, "", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeakerAction("evt-1", "rustam", ActionKind.APPLIED, "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

