/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.interceptors.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Initialises the OpenTelemetry SDK once, application-wide, and exposes the
 * resulting {@link OpenTelemetry} and {@link Tracer} as CDI beans.
 *
 * <p><strong>When does this kick in?</strong> Only on runtimes that do <em>not</em>
 * already publish a {@code @Default Tracer}. On Open Liberty 26.x with
 * {@code microProfile-7.1}, the {@code mpTelemetry-2.1} feature ships its own
 * {@code @Produces Tracer} — so this class is marked {@link Alternative} and is
 * <em>disabled by default</em>. The gem #1 pedagogy stays intact: this is the
 * 30-line producer you'd write by hand on a runtime without mpTelemetry. To
 * activate it, list this class in {@code beans.xml}'s {@code <alternatives>} or
 * remove the {@code microProfile-7.1} feature from {@code server.xml}.
 *
 * <p>Configuration is read from the standard OTel environment variables
 * ({@code OTEL_EXPORTER_OTLP_ENDPOINT}, {@code OTEL_SERVICE_NAME},
 * {@code OTEL_TRACES_EXPORTER}, …) — see the docker-compose stack for the
 * default wiring.
 *
 * <p>If the SDK fails to initialise (no collector reachable in dev mode, etc.)
 * the producer falls back to {@link OpenTelemetry#noop()} so the application
 * still starts and serves requests — spans simply go nowhere.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class OpenTelemetryProducer {

    private static final Logger LOG = System.getLogger(OpenTelemetryProducer.class.getName());
    private static final String INSTRUMENTATION_NAME = "com.mehmandarov.jhg";
    private static final String INSTRUMENTATION_VERSION = "0.1.0";

    private OpenTelemetry openTelemetry;

    @PostConstruct
    void init() {
        try {
            this.openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize()
                    .getOpenTelemetrySdk();
            LOG.log(Level.INFO,
                    "[otel] SDK initialised; endpoint={0}, service={1}",
                    System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "(default)"),
                    System.getenv().getOrDefault("OTEL_SERVICE_NAME", "(unset)"));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING,
                    "[otel] SDK init failed ({0}); falling back to OpenTelemetry.noop()",
                    e.getMessage());
            this.openTelemetry = OpenTelemetry.noop();
        }
    }

    @Produces
    @ApplicationScoped
    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    @Produces
    @ApplicationScoped
    public Tracer tracer() {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }
}

