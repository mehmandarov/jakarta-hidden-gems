/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit tests for {@link Timeouts}, the wall-clock budget helper that
 * {@link SafeELEvaluator} relies on. {@code SafeELEvaluatorTest} only exercises
 * the timeout and {@code RuntimeException}-propagation paths indirectly; this
 * suite pins down all four branches of {@link Timeouts#callWithTimeout}.
 */
class TimeoutsTest {

    @Test
    void returnsResultWhenTaskCompletesInTime() {
        String result = Timeouts.callWithTimeout(Duration.ofSeconds(1), () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void throwsTimeoutWhenTaskExceedsBudget() {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        assertThatThrownBy(() ->
                Timeouts.callWithTimeout(Duration.ofMillis(20), () -> {
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        throw e;
                    }
                    return "never";
                }))
                .isInstanceOf(Timeouts.EvaluationTimeoutException.class)
                .hasMessageContaining("exceeded");
    }

    @Test
    void propagatesTasksRuntimeException() {
        assertThatThrownBy(() ->
                Timeouts.callWithTimeout(Duration.ofSeconds(1), () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void propagatesTasksError() {
        assertThatThrownBy(() ->
                Timeouts.callWithTimeout(Duration.ofSeconds(1), () -> {
                    throw new AssertionError("fatal");
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessage("fatal");
    }
}

