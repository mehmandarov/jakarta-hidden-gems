/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import com.mehmandarov.jhg.auth.SpeakerRegistry;
import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.events.EventVisibility;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.security.Principal;

/**
 * Drop-in alternative that replaces gem #1's allow-all
 * {@link EventVisibility} with one that consults the {@link AccessPolicy}.
 *
 * <p>An {@code @Alternative} carrying a {@code @Priority} is <em>globally
 * enabled</em> (CDI 1.1+) and outranks the plain gem #1 bean whenever something
 * injects {@code EventVisibility} — no {@code beans.xml} selection needed. This
 * is the portable, CDI-Lite-compatible equivalent of {@code @Specializes}: it
 * works identically on Weld (Liberty/Helidon) and Quarkus Arc, which rejects
 * {@code @Specializes} outright.
 *
 * <p>Effect: as soon as the {@code 02-authorization} jar is on the
 * classpath, every {@code GET /api/events*} call is row-filtered through the
 * policy — no changes to {@code EventsResource}.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class PolicyEventVisibility extends EventVisibility {

    @Inject
    AccessPolicy policy;

    @Inject
    SpeakerRegistry registry;

    @Override
    public boolean canSee(Principal principal, ConfEvent event) {
        if (principal == null) return false;       // unauthenticated → see nothing
        return registry.byPrincipal(principal)
                .map(speaker -> policy.canSee(speaker, event))
                .orElse(false);
    }
}

