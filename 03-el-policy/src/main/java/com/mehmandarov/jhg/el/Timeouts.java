/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A tiny wall-clock timeout helper for predicate evaluation.
 *
 * <p>Jakarta EL has no built-in execution budget, so a single pathological
 * predicate could, in theory, run for a long time. {@link SafeELEvaluator}
 * wraps every evaluation in {@link #callWithTimeout} so a slow or stuck
 * predicate is cancelled and denied rather than blocking the request thread.
 *
 * <p>The pool uses daemon threads so it never keeps the JVM alive.
 */
final class Timeouts {

    private Timeouts() {
    }

    private static final ExecutorService POOL =
            Executors.newCachedThreadPool(daemonFactory());

    /**
     * Run {@code task}, returning its result if it completes within
     * {@code timeout}.
     *
     * @throws EvaluationTimeoutException if the task does not finish in time
     *                                    (or is interrupted)
     * @throws RuntimeException           the task's own unchecked exception, if it threw one
     * @throws Error                      the task's own error, if it threw one
     */
    static <T> T callWithTimeout(Duration timeout, Callable<T> task) {
        Future<T> future = POOL.submit(task);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new EvaluationTimeoutException(
                    "evaluation exceeded " + timeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new EvaluationTimeoutException("evaluation failed: " + cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new EvaluationTimeoutException("evaluation interrupted");
        }
    }

    private static ThreadFactory daemonFactory() {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, "safe-el-eval-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Thrown when a predicate exceeds its time budget. Unchecked → fail closed. */
    static final class EvaluationTimeoutException extends RuntimeException {
        EvaluationTimeoutException(String message) {
            super(message);
        }
    }
}

