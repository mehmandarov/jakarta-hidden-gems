/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.capstone;

import jakarta.annotation.security.DeclareRoles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.auth.LoginConfig;

/**
 * JAX-RS application root. All resources live under {@code /api}.
 *
 * <p>Authentication is <strong>MicroProfile JWT</strong> — portable across Open
 * Liberty, Helidon and Quarkus. Callers present a {@code Bearer} token
 * whose {@code groups} claim drives {@code @RolesAllowed}; the ABAC filter (gem
 * #2) + EL (gem #3) then do row-level authorization. Mint a demo token at
 * {@code GET /api/dev/token?user=rustam} (see {@code DevTokenResource}).
 *
 * <p>{@code @ApplicationScoped} makes this a CDI bean so every runtime — including
 * Helidon MP and Quarkus, which discover JAX-RS applications through CDI — honors
 * the {@code /api} {@code @ApplicationPath} instead of falling back to a synthetic
 * root application.
 */
@ApplicationPath("/api")
@ApplicationScoped
@LoginConfig(authMethod = "MP-JWT")
@DeclareRoles({"SPEAKER", "ADMIN"})
public class ConfSpeakerHubApp extends Application {
}

