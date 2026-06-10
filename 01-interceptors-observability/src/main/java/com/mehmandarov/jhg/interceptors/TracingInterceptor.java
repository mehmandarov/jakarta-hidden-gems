/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.interceptors;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Implements {@link Traced}: opens a span, propagates context, records
 * exceptions, and ends the span on return.
 *
 * <p>Priority is {@code APPLICATION + 10} — fires <em>before</em>
 * {@link TimingInterceptor} so the timing measurement is captured
 * <em>inside</em> the span (and can be attached as an attribute).
 *
 * <p>The whole point of gem #1: <strong>no business code touches the OTel
 * API.</strong> Add {@code @Traced}, redeploy, see the span in Jaeger.
 */
@Traced
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
public class TracingInterceptor {

    @Inject
    Tracer tracer;

    @AroundInvoke
    public Object trace(InvocationContext ctx) throws Exception {
        String spanName = ctx.getMethod().getDeclaringClass().getSimpleName()
                          + "." + ctx.getMethod().getName();

        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return ctx.proceed();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
}

