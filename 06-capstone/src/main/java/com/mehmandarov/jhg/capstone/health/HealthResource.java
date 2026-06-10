/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.capstone.health;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;

/**
 * Phase 0 liveness probe. Deliberately minimal — no DB ping, no Kafka ping.
 * Per-gem modules will replace/extend this with their own dependency-aware
 * health checks (the JCA module adds Kafka, the JSON-P module adds Ollama).
 *
 * <p>{@code @PermitAll} keeps the probe public even though the application
 * declares MicroProfile JWT: some runtimes (e.g. Helidon MP) default-deny any
 * endpoint that carries no explicit access annotation once security is enabled.
 */
@Path("/health")
@ApplicationScoped
@PermitAll
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        var body = Map.of(
                "status",   "UP",
                "service",  "confspeakerhub",
                "phase",    "1-gems-1-5",
                "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime(),
                "now",      Instant.now().toString()
        );
        return Response.ok(body).build();
    }
}

