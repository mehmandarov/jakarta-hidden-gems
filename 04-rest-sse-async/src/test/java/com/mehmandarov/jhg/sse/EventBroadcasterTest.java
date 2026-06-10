/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.sse;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.EventCachedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;

class EventBroadcasterTest {

    /**
     * Without any subscriber the observer must be a no-op — that's how we
     * keep the SSE module optional from the JCA module's point of view.
     */
    @Test
    void onEventCached_withoutSubscribers_isSilent() {
        var b = new EventBroadcaster();
        var ev = new ConfEvent(
                "it:probe", "Probe", "X",
                LocalDate.now(), LocalDate.now().plusDays(7),
                Set.of("java"), Set.of("en"),
                "https://x", "https://x/cfp",
                Set.of("t"), Instant.now());

        assertThatCode(() -> b.onEventCached(new EventCachedEvent(ev)))
                .doesNotThrowAnyException();
    }
}

