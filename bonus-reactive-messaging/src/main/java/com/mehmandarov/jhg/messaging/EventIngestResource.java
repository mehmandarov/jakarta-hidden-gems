/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.messaging;

import com.mehmandarov.jhg.domain.ConfEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * {@code POST /api/ingest} — webhook landing site. Publishes the event JSON
 * onto the {@code confs-events-out} channel, which the Kafka connector routes
 * to the {@code confs.events.incoming} topic on Redpanda.
 *
 * <p>The portable counterpart to the JCA module's {@code IngestResource}: no
 * {@code JMSContext}, no resource adapter — just an {@link Emitter}. Runs on
 * Open Liberty, Helidon and Quarkus.
 */
@Path("/ingest")
@ApplicationScoped
public class EventIngestResource {

    private static final Logger LOG = System.getLogger(EventIngestResource.class.getName());
    private final Jsonb jsonb = JsonbBuilder.create();

    @Inject
    @Channel(EventChannels.EVENTS_OUT)
    Emitter<String> emitter;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response publish(ConfEvent ev) {
        if (ev == null || ev.id() == null || ev.id().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"id is required\"}").build();
        }
        emitter.send(jsonb.toJson(ev));
        LOG.log(Level.INFO, "[ingest] published {0}", ev.id());
        return Response.accepted().entity("{\"queued\":\"" + ev.id() + "\"}").build();
    }
}

