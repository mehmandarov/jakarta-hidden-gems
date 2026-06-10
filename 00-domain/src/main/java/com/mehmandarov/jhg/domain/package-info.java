/*
 * Copyright 2026 Rustam Mehmandarov.
 */
/**
 * ConfSpeakerHub domain records — pure Java, no Jakarta dependencies.
 *
 * <p>These types are the lingua franca between every gem module. They are
 * intentionally tiny ({@link java.lang.Record}s where possible), immutable,
 * and self-validating. JPA entities (bonus persistence layer) and SSE serialization (gem #4) are
 * adapters built <em>around</em> these — never the other way around.
 */
package com.mehmandarov.jhg.domain;

