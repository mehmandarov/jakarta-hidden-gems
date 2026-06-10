/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.capstone;

import com.mehmandarov.jhg.auth.SpeakerRegistry;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;
import java.util.Set;

/**
 * <strong>Dev only.</strong> Mints a MicroProfile JWT for the demo so you don't
 * need an external IdP. The token's {@code groups} claim carries the speaker's
 * roles (from {@code speakers.json}); it's signed with the bundled private key
 * and verified against the bundled public key.
 *
 * <pre>
 *   TOKEN=$(curl -s 'http://localhost:9080/api/dev/token?user=rustam')
 *   curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/api/events
 * </pre>
 *
 * In production this endpoint would not exist — tokens come from a real issuer
 * (Keycloak, Auth0, …). It's open ({@code @PermitAll}) purely for the demo.
 */
@Path("/dev/token")
@ApplicationScoped
@PermitAll
public class DevTokenResource {

    private static final String ISSUER = "https://confspeakerhub.example";

    @Inject
    SpeakerRegistry registry;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String token(@QueryParam("user") @DefaultValue("rustam") String user) {
        Set<String> roles = registry.rolesOf(user);
        if (roles.isEmpty()) {
            roles = Set.of("SPEAKER");
        }
        return Jwt.issuer(ISSUER)
                .upn(user)
                .subject(user)
                .groups(roles)
                .expiresIn(Duration.ofHours(8))
                .sign();
    }
}

