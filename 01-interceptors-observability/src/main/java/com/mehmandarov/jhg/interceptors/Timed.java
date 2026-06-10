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
 * Logs the wall-clock duration of the annotated method and, if a span is
 * active, attaches the duration as a {@code duration.ms} span attribute.
 *
 * <p>Cheap, side-effect free, no SDK dependency — works with or without
 * {@link Traced}.
 *
 * @see TimingInterceptor
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Timed {
}

