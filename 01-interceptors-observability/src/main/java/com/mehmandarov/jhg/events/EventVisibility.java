/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.events;

import com.mehmandarov.jhg.domain.ConfEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.Principal;

/**
 * Per-row visibility seam. Module 01 ships a no-op default that lets
 * everything through; module 02 replaces it with a globally-enabled CDI
 * {@code @Alternative} that runs the rule's predicate against
 * {@code (speaker, event)}.
 *
 * <p>Designed so {@link EventsResource} doesn't need to know whether a
 * security policy is in effect — it just calls {@link #canSee} on every row.
 */
@ApplicationScoped
public class EventVisibility {

    /** Default: allow everything. */
    public boolean canSee(Principal principal, ConfEvent event) {
        return true;
    }
}

