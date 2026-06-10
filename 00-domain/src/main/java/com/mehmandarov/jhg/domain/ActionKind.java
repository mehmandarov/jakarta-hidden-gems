/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.domain;

/**
 * What a speaker decided to do with a {@link ConfEvent}.
 *
 * <p>Each transition produces a {@link SpeakerAction} that:
 * <ol>
 *   <li>is persisted to embedded H2 in one JTA transaction with the Kafka publish (bonus),</li>
 *   <li>is published to {@code confs.actions} (bonus),</li>
 *   <li>causes a markdown line to be appended to a GitHub gist (the speaker's CFP backlog),</li>
 *   <li>is broadcast over SSE to other open browser tabs (gem #4).</li>
 * </ol>
 */
public enum ActionKind {
    /** Speaker bookmarked the event for later review. */
    SAVED,
    /** Speaker decided this event isn't a fit. */
    DISMISSED,
    /** Speaker submitted (or intends to submit) a CFP. */
    APPLIED
}

