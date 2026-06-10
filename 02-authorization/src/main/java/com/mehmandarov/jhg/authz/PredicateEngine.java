/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Pluggable predicate engine — the seam between gem #2 and gem #3.
 *
 * <p>Gem #2 ships this default, which delegates to the hand-rolled
 * {@link PredicateEvaluator} (5 operators, no nesting). Gem #3 puts a sandboxed
 * Jakarta EL engine on the classpath that overrides this bean via a
 * globally-enabled {@code @Alternative} (the portable equivalent of
 * {@code @Specializes}), so the very same {@link AccessPolicy} suddenly
 * understands full EL predicates — date arithmetic, member access, functions —
 * with zero changes to gem #2.
 */
@ApplicationScoped
public class PredicateEngine {

    /** Evaluate {@code predicate} against {@code (speaker, event)}; never throws. */
    public boolean evaluate(String predicate, Speaker speaker, ConfEvent event) {
        return PredicateEvaluator.evaluate(predicate, speaker, event);
    }
}

