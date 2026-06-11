/*
 * Copyright 2026 Rustam Mehmandarov.
 */
/**
 * Portable, in-process authorization for ConfSpeakerHub (gem #2).
 *
 * <p>Headlines:
 * <ul>
 *   <li>{@link com.mehmandarov.jhg.authz.PredicateEvaluator} — 4-operator,
 *       no-nesting predicate DSL (gem #3 replaces it with sandboxed Jakarta EL).</li>
 *   <li>{@link com.mehmandarov.jhg.authz.AccessPolicy} — plain CDI bean holding
 *       a hot-reloadable rule set; decisions via {@code @RolesAllowed} + a JAX-RS
 *       authorization filter. Runs on every Jakarta runtime (Liberty,
 *       Helidon, Quarkus). Jakarta Authorization (JACC) 3.0 is the full-profile
 *       escalation, covered as a bonus.</li>
 *   <li>{@link com.mehmandarov.jhg.authz.PolicyEventVisibility} — a
 *       globally-enabled CDI {@code @Alternative} that wires the policy into
 *       gem #1's {@code EventVisibility} seam.</li>
 * </ul>
 */
package com.mehmandarov.jhg.authz;

