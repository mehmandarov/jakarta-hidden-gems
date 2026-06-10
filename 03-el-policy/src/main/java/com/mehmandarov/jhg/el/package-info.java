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
 *       full EL power.</li>
 *   <li>{@link com.mehmandarov.jhg.el.PolicyFunctions} — the {@code fn} helper
 *       bean exposed to predicates.</li>
 * </ul>
 *
 * <p>The Jakarta EL RI (Expressly) is bundled, so the gem runs on every Jakarta
 * runtime — Open Liberty, Helidon and Quarkus (JVM mode).
 */
package com.mehmandarov.jhg.el;

