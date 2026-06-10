/*
 * Copyright 2026 Rustam Mehmandarov.
 */
/**
 * Bonus — portable Kafka messaging via MicroProfile Reactive Messaging.
 *
 * <ul>
 *   <li>{@link com.mehmandarov.jhg.messaging.EventIngestResource} — {@code @Outgoing}
 *       producer ({@code Emitter}) behind {@code POST /api/ingest}.</li>
 *   <li>{@link com.mehmandarov.jhg.messaging.EventConsumer} — {@code @Incoming}
 *       consumer with idempotent dedupe; fires the SSE CDI event.</li>
 * </ul>
 *
 * <p>Runs on Open Liberty, Helidon and Quarkus. The full-profile
 * Jakarta Connectors (JCA) module is the escalation when you need JTA-atomic
 * "DB write + publish in one transaction".
 */
package com.mehmandarov.jhg.messaging;

