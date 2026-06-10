/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.llm;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import com.mehmandarov.jhg.domain.TalkTitle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StubAnalyzerTest {

    private final StubAnalyzer stub = new StubAnalyzer();

    @Test
    void emitsTldrThenTagsThenThreeTopicsThenDone() {
        Speaker rustam = new Speaker(
                "rustam", Set.of("en", "nb"), Set.of("EU"),
                Set.of("java", "cloud-native", "ai"),
                List.of(
                        new TalkTitle("Hidden Gems",      "Five overlooked specs.", Set.of("java", "cloud-native")),
                        new TalkTitle("Grounded LLMs",    "RAG without slop.",      Set.of("ai")),
                        new TalkTitle("OpenJ9 Tuning",    "Footprint on a budget.", Set.of("java")),
                        new TalkTitle("Container Idioms", "Image hygiene.",         Set.of("cloud-native"))
                ),
                Set.of("SPEAKER"));
        ConfEvent ev = new ConfEvent(
                "sessionize:probe", "Probe Conf 2027", "Oslo",
                LocalDate.of(2027, 3, 1), LocalDate.of(2026, 12, 1),
                Set.of("java", "cloud-native"), Set.of("en"),
                "https://x", "https://x/cfp",
                Set.of("nordics", "java"), Instant.now());

        List<String> lines = new ArrayList<>();
        stub.streamAnalysis(ev, rustam, lines::add);

        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).contains("\"type\":\"tldr\"").contains("Probe Conf 2027");

        long topics = lines.stream().filter(l -> l.contains("\"type\":\"topic\"")).count();
        assertThat(topics).as("exactly 3 topic lines").isEqualTo(3);

        assertThat(lines.getLast()).contains("\"type\":\"done\"");

        // Topics must come from the speaker's actual portfolio.
        Set<String> portfolioTitles = Set.of(
                "Hidden Gems", "Grounded LLMs", "OpenJ9 Tuning", "Container Idioms");
        lines.stream()
                .filter(l -> l.contains("\"type\":\"topic\""))
                .forEach(l -> assertThat(portfolioTitles).anySatisfy(title ->
                        assertThat(l).contains("\"title\":\"" + title + "\"")));
    }
}

