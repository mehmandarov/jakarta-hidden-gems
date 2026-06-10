/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

import java.time.Instant;

/**
 * One audit-grade record of a speaker decision on a conference event.
 *
 * @param eventId   {@link ConfEvent#id() event id}
 * @param username  {@link Speaker#username() speaker username}
 * @param kind      what was decided
 * @param note      optional free-text note (may be empty, never {@code null})
 * @param at        when the decision was recorded (server time)
 */
public record SpeakerAction(String eventId,
                            String username,
                            ActionKind kind,
                            String note,
                            Instant at) {

    public SpeakerAction {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("SpeakerAction.eventId must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("SpeakerAction.username must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("SpeakerAction.kind must not be null");
        }
        if (at == null) {
            throw new IllegalArgumentException("SpeakerAction.at must not be null");
        }
        note = note == null ? "" : note;
    }
}

