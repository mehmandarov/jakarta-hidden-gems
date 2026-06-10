/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The store would normally be initialised via the CDI {@code @PostConstruct}
 * lifecycle. In a plain JUnit run we drive {@code load()} reflectively so we
 * can verify the {@code feeds.json} parsing without dragging in a CDI
 * container.
 */
class InMemoryEventStoreTest {

    private InMemoryEventStore store;

    @BeforeEach
    void boot() throws Exception {
        store = new InMemoryEventStore();
        Method load = InMemoryEventStore.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(store);
    }

    @Test
    void loads_all_seed_events_from_classpath() {
        assertThat(store.all()).hasSize(20);
    }

    @Test
    void parses_jfokus_correctly() {
        var jfokus = store.byId("sessionize:jfokus-2027").orElseThrow();
        assertThat(jfokus.name()).isEqualTo("JFokus 2027");
        assertThat(jfokus.location()).isEqualTo("Stockholm, Sweden");
        assertThat(jfokus.languages()).containsExactly("en");
        assertThat(jfokus.tracks()).containsExactlyInAnyOrder("java", "cloud-native", "devex");
    }

    @Test
    void unknown_id_returns_empty() {
        assertThat(store.byId("nope")).isEmpty();
    }

    @Test
    void ordering_is_newest_ingested_first() {
        var first  = store.all().get(0);
        var second = store.all().get(1);
        assertThat(first.ingestedAt()).isAfterOrEqualTo(second.ingestedAt());
    }
}

