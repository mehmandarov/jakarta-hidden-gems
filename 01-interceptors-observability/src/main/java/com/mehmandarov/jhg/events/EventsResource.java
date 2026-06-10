/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.events;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.interceptors.Timed;
import com.mehmandarov.jhg.interceptors.Traced;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;
import java.util.List;

/**
 * Phase-1 read API for ConfSpeakerHub events. Annotated with {@link Traced}
 * and {@link Timed} so every call produces a Jaeger span and a duration log
 * line — without a single line of OTel code in this class.
 *
 * <p>Per-row visibility goes through the {@link EventVisibility} seam:
 * gem #1 ships an allow-all default, gem #2 ({@code @RolesAllowed} + ABAC
 * filter) replaces it with a CDI alternative that runs the access-policy
 * predicate against {@code (speaker, event)}.
 *
 * <p>Live demo for scene 1 of the talk: open the dashboard, watch the span
 * appear in Jaeger; live-add {@code @Traced} to a fresh method and watch a
 * second span appear after redeploy.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Traced
@Timed
public class EventsResource {

    @Inject
    EventStore store;

    @Inject
    EventVisibility visibility;

    @GET
    public List<ConfEvent> list(@Context SecurityContext security) {
        Principal principal = security == null ? null : security.getUserPrincipal();
        return store.all().stream()
                .filter(e -> visibility.canSee(principal, e))
                .toList();
    }

    @GET
    @Path("/{id}")
    public ConfEvent get(@PathParam("id") String id, @Context SecurityContext security) {
        Principal principal = security == null ? null : security.getUserPrincipal();
        return store.byId(id)
                .filter(e -> visibility.canSee(principal, e))
                .orElseThrow(() -> new NotFoundException("No event with id=" + id));
    }
}

