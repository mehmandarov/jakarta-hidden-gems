/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

/**
 * CDI event fired by the JCA module ({@code EventConsumerMDB}) once a new
 * {@link ConfEvent} has been successfully cached. The SSE module's
 * {@code EventBroadcaster} observes it and pushes the row to all subscribed
 * browsers.
 *
 * <p>Lives in {@code 00-domain} so neither the producer ({@code 03-jca}) nor
 * the observer ({@code 04-sse}) needs to depend on the other — keeping the
 * gem boundaries clean and the talk's "one click, five gems" trace honest.
 *
 * @param event the freshly-cached {@link ConfEvent}; never {@code null}.
 */
public record EventCachedEvent(ConfEvent event) { }

