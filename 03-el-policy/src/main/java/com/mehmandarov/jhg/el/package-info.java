/*
 * Copyright 2026 Rustam Mehmandarov.
 */
/**
 * Gem #3 — Jakarta Expression Language (EL) as a sandboxed, hot-reloadable
 * policy engine for ConfSpeakerHub.
 *
 * <ul>
 *   <li>{@link com.mehmandarov.jhg.el.ElPredicateEngine} — a globally-enabled
 *       CDI {@code @Alternative} (portable equivalent of {@code @Specializes})
 *       that overrides gem #2's {@code PredicateEngine} with an
 *       {@code ELProcessor}; the same {@code rules.json} keeps working and gains
 *       full EL power. This is the running default — a regex <em>denylist</em>
 *       over the predicate text, backward-compatible with gem #2's
 *       {@code intersects}/{@code contains} infix operators.</li>
 *   <li>{@link com.mehmandarov.jhg.el.PolicyFunctions} — the {@code fn} helper
 *       bean exposed to predicates.</li>
 *   <li>{@link com.mehmandarov.jhg.el.SafeELEvaluator} — the hardened companion
 *       (EL-sandbox material): pairs {@link com.mehmandarov.jhg.el.SafeELResolver}
 *       with a per-call timeout so reflection, {@code Runtime}, {@code System},
 *       {@code ProcessBuilder} and threads are unreachable.</li>
 *   <li>{@link com.mehmandarov.jhg.el.SafeELResolver} — a fail-closed
 *       {@code ELResolver} that <em>whitelists types</em> (and a few static
 *       classes) rather than denylisting strings, vetoing anything else.</li>
 *   <li>{@link com.mehmandarov.jhg.el.Timeouts} — a tiny wall-clock timeout
 *       helper that gives every {@code SafeELEvaluator} call an execution
 *       budget, so a pathological predicate cannot block a request thread.</li>
 * </ul>
 *
 * <p>The Jakarta EL RI (Expressly) is bundled, so the gem runs on every Jakarta
 * runtime — Open Liberty, Helidon and Quarkus (JVM mode).
 */
package com.mehmandarov.jhg.el;

