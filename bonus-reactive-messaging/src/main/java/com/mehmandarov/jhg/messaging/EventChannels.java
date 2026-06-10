/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.messaging;

/**
 * Single source of truth for Kafka topic names and the MicroProfile Reactive
 * Messaging channel names that map to them (see
 * {@code META-INF/microprofile-config.properties}).
 */
public final class EventChannels {

    private EventChannels() { }

    /** Kafka topics on Redpanda. */
    public static final String EVENTS_TOPIC  = "confs.events.incoming";
    public static final String ACTIONS_TOPIC = "confs.actions";

    /** MP Reactive Messaging channel names. */
    public static final String EVENTS_OUT = "confs-events-out";
    public static final String EVENTS_IN  = "confs-events-in";
}

