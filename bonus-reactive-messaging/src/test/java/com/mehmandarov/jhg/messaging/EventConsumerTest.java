/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.messaging;

import com.mehmandarov.jhg.domain.ConfEvent;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventConsumerTest {

    private final EventConsumer consumer = new EventConsumer();
    private final Jsonb jsonb = JsonbBuilder.create();

    private ConfEvent sample(String id) {
        return new ConfEvent(id, "JFokus", "Stockholm",
                LocalDate.of(2027, 2, 1), LocalDate.of(2026, 9, 1),
                Set.of("java"), Set.of("en"),
                "https://jfokus.se", "https://jfokus.se/cfp",
                Set.of("conf"), Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void freshThenDuplicateIsDeduped() {
        String json = jsonb.toJson(sample("jfokus-2027"));
        assertThat(consumer.consume(json)).isTrue();    // first delivery → fresh
        assertThat(consumer.consume(json)).isFalse();   // redelivery → deduped
    }

    @Test
    void blankOrNullIsIgnored() {
        assertThat(consumer.consume("")).isFalse();
        assertThat(consumer.consume(null)).isFalse();
    }
}

