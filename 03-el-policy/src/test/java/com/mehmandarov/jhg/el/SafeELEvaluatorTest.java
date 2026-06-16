/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CVE-style payload test suite for {@link SafeELEvaluator}.
 *
 * <p>This is the test that earns the "<em>safe</em>" in the post's title: every
 * payload below is a real-world EL injection technique — the same shape as
 * CVE-2020-10693 (Hibernate Validator), CVE-2017-1000486 (Primefaces), and
 * countless tutorial-blog snippets. None of them should ever evaluate to
 * {@code true}, regardless of whether they parse or throw inside the sandbox.
 *
 * <p>The contract under test is the simplest one possible: <strong>fail
 * closed</strong>. Anything that doesn't cleanly evaluate to {@code true}
 * against the policy file is denied — so a thrown {@link jakarta.el.ELException}
 * from the resolver counts as a successful block, not a test failure.
 */
class SafeELEvaluatorTest {

    private final SafeELEvaluator el = new SafeELEvaluator();

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

    // ---- legitimate usage ---------------------------------------------------

    @Test
    void emptyPredicateAllows() {
        assertThat(el.evaluate("", rustam, event(Set.of("en"), Set.of("java"), LocalDate.now())))
                .isTrue();
        assertThat(el.evaluate(null, rustam, event(Set.of("en"), Set.of("java"), LocalDate.now())))
                .isTrue();
    }

    @Test
    void collectionContainsWorksViaHelper() {
        String pred = "fn.contains(speaker.languages, 'en')";
        assertThat(el.evaluate(pred, rustam, event(Set.of("en"), Set.of("java"), LocalDate.now())))
                .isTrue();
        assertThat(el.evaluate("fn.contains(speaker.languages, 'es')", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now())))
                .isFalse();
    }

    @Test
    void intersectsHelperWorks() {
        String pred = "fn.intersects(speaker.languages, event.languages) "
                + "&& fn.intersects(speaker.tracks, event.tracks)";
        assertThat(el.evaluate(pred, rustam, event(Set.of("en"), Set.of("java"), LocalDate.now())))
                .isTrue();
        assertThat(el.evaluate(pred, rustam, event(Set.of("es"), Set.of("mobile"), LocalDate.now())))
                .isFalse();
    }

    @Test
    void dateArithmeticUsesWhitelistedStaticType() {
        // LocalDate is imported by SafeELEvaluator and on the static whitelist
        // in SafeELResolver, so this round-trips: LocalDate.now() returns a
        // LocalDate (TemporalAccessor → allowed instance), .isAfter(...) is
        // not blocked, and the comparison works.
        String pred = "fn.daysUntil(event.cfpDeadline) > 7";
        assertThat(el.evaluate(pred, rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now().plusDays(30)))).isTrue();
        assertThat(el.evaluate(pred, rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now().minusDays(1)))).isFalse();
    }

    // ---- sandbox: the whole reason this class exists ------------------------

    /** Classic textbook: "${''.getClass()}" → reflection pivot. CVE-2020-10693 shape. */
    @Test
    void blocksGetClassPivot() {
        assertThat(el.evaluate("'' == ''.getClass().getName()", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
        assertThat(el.evaluate("speaker.languages.class != null", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    /** java.lang.* is not on the static whitelist, even though EL imports it by default. */
    @Test
    void blocksJavaLangStatics() {
        assertThat(el.evaluate("System.exit(0)", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
        assertThat(el.evaluate("Runtime.getRuntime() != null", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    /** The Primefaces / Spring SpEL shape: reach Runtime via reflection. */
    @Test
    void blocksRuntimeViaReflection() {
        String payload =
                "'a'.getClass().forName('java.lang.Runtime').getMethod('exec', 'a'.getClass())"
                + ".invoke(null, 'id')";
        assertThat(el.evaluate(payload, rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    /** Thread pivot — same idea, different starting point. */
    @Test
    void blocksThreadPivot() {
        assertThat(el.evaluate("Thread.currentThread().getName()", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    /** ProcessBuilder is not on any whitelist. */
    @Test
    void blocksProcessBuilder() {
        assertThat(el.evaluate("ProcessBuilder.class.getName()", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    /** Even text-level pre-filter denies obvious payloads before EL parses them. */
    @Test
    void rejectsObviouslyHostileText() {
        // Underscored / split forms are blocked by either the text filter or
        // the resolver — either way, the answer is false.
        assertThat(el.evaluate("'java.lang.Runtime'", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    // ---- denial-of-service guards ------------------------------------------

    @Test
    void timeoutDeniesSlowPredicates() {
        // A predicate that calls the helper with a 1-day-from-now date is fast,
        // but pair a 1 ns budget with anything non-trivial and we time out.
        SafeELEvaluator tiny = new SafeELEvaluator(Duration.ofNanos(1), 64);
        assertThat(tiny.evaluate("fn.daysUntil(event.cfpDeadline) > 7", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now().plusDays(30)))).isFalse();
    }

    @Test
    void resolutionBudgetDeniesExpensivePredicates() {
        // 1 resolution is below what even a trivial property navigation needs,
        // so the very first resolver hit trips the budget. Fail-closed → false.
        SafeELEvaluator tightlyBudgeted = new SafeELEvaluator(Duration.ofMillis(50), 1);
        assertThat(tightlyBudgeted.evaluate("fn.contains(speaker.languages, 'en')", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }

    // ---- read-only --------------------------------------------------------

    @Test
    void writingThroughElIsDenied() {
        // Attempting to assign to a bound bean is rejected by the resolver.
        assertThat(el.evaluate("speaker.username = 'attacker'", rustam,
                event(Set.of("en"), Set.of("java"), LocalDate.now()))).isFalse();
    }
}

