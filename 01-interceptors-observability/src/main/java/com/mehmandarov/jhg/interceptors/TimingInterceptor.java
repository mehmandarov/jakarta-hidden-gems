/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.interceptors;

import io.opentelemetry.api.trace.Span;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Implements {@link Timed}: logs wall-clock duration in ms and, if a span is
 * active in the current OTel context (typically because {@link TracingInterceptor}
 * has already opened one), attaches it as the {@code duration.ms} attribute.
 *
 * <p>Priority is {@code APPLICATION + 20} — fires <em>after</em>
 * {@link TracingInterceptor} so the timing happens inside the span scope.
 */
@Timed
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 20)
public class TimingInterceptor {

    private static final Logger LOG = System.getLogger(TimingInterceptor.class.getName());

    @AroundInvoke
    public Object time(InvocationContext ctx) throws Exception {
        long startNanos = System.nanoTime();
        try {
            return ctx.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            String label = ctx.getMethod().getDeclaringClass().getSimpleName()
                           + "." + ctx.getMethod().getName();

            LOG.log(Level.INFO, "[timed] {0} took {1} ms", label, elapsedMs);

            // Best-effort: tag the active span if one exists.
            Span current = Span.current();
            if (current.getSpanContext().isValid()) {
                current.setAttribute("duration.ms", elapsedMs);
            }
        }
    }
}

