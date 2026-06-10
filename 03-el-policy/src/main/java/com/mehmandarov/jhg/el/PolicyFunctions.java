/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Helper functions exposed to policy predicates as the EL bean {@code fn}.
 *
 * <p>The first two back-fill gem #2's custom infix operators
 * ({@code intersects}, {@code contains}); the rest are the kind of thing the
 * hand-rolled DSL could never do and EL gets for free.
 */
public final class PolicyFunctions {

    /** True if the two collections share at least one element. */
    public boolean intersects(Object a, Object b) {
        if (!(a instanceof Collection<?> ca) || !(b instanceof Collection<?> cb)) {
            return false;
        }
        for (Object o : ca) {
            if (cb.contains(o)) return true;
        }
        return false;
    }

    /** True if {@code coll} contains {@code item}. */
    public boolean contains(Object coll, Object item) {
        return coll instanceof Collection<?> c && c.contains(item);
    }

    /** Days from today until {@code date} (negative if already past). */
    public long daysUntil(Object date) {
        if (date instanceof LocalDate d) {
            return ChronoUnit.DAYS.between(LocalDate.now(), d);
        }
        return Long.MIN_VALUE;
    }

    /** Number of elements in a collection (or 0 for anything else). */
    public int size(Object coll) {
        return coll instanceof Collection<?> c ? c.size() : 0;
    }
}

