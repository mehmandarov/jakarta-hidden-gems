/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.interceptors;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wraps the annotated method (or all methods of the annotated class) in an
 * OpenTelemetry span. The span name is {@code SimpleClassName.methodName};
 * exceptions are recorded and the span status is set to ERROR.
 *
 * <p>Pairs naturally with {@link Timed} — both can sit on the same method;
 * the timing is also added as a {@code duration.ms} attribute on the span.
 *
 * @see TracingInterceptor
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Traced {
}

