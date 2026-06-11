/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tiny, deliberately limited predicate language for ABAC rules. <strong>4
 * operators, no nesting, no functions</strong> — exactly what gem #2 needs
 * and no more. The EL post replaces it with a sandboxed {@code ELProcessor}.
 *
 * <p><b>Grammar:</b>
 * <pre>
 *   expr    ::= clause ( '&amp;&amp;' clause )*       // AND-join, no precedence beyond left-to-right
 *   clause  ::= ref op ref
 *   op      ::= 'intersects' | 'contains' | '==' | '!='
 *   ref     ::= path | string | number | 'true' | 'false' | 'null'
 *   path    ::= IDENT ( '.' IDENT )+              // e.g. speaker.languages, event.id
 *   string  ::= '"' ... '"'  |  '\'' ... '\''
 * </pre>
 *
 * <p>Roots are {@code speaker} and {@code event}. Sub-paths walk the JavaBean-
 * style accessors of {@link Speaker} and {@link ConfEvent} as record components.
 */
public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    /** Evaluate {@code predicate} against {@code (speaker, event)}; never throws. */
    public static boolean evaluate(String predicate, Speaker speaker, ConfEvent event) {
        if (predicate == null || predicate.isBlank()) {
            return true;   // empty predicate ≡ allow
        }
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("speaker", speaker);
        ctx.put("event",   event);
        try {
            for (String clause : splitTopLevelAnd(predicate)) {
                if (!evalClause(clause.trim(), ctx)) return false;
            }
            return true;
        } catch (RuntimeException e) {
            // Malformed rule → deny safely. The post calls this out as a
            // deliberate fail-closed posture.
            return false;
        }
    }

    // ---- top-level AND ------------------------------------------------------

    private static List<String> splitTopLevelAnd(String predicate) {
        // Naive split — strings cannot contain `&&` in this DSL. Good enough.
        return List.of(predicate.split("&&"));
    }

    // ---- single clause ------------------------------------------------------

    private static boolean evalClause(String clause, Map<String, Object> ctx) {
        Op op = Op.detect(clause);
        if (op == null) {
            throw new IllegalArgumentException("No operator in clause: " + clause);
        }
        String[] parts = op.split(clause);
        Object lhs = resolve(parts[0].trim(), ctx);
        Object rhs = resolve(parts[1].trim(), ctx);
        return op.apply(lhs, rhs);
    }

    // ---- ref resolution -----------------------------------------------------

    private static Object resolve(String ref, Map<String, Object> ctx) {
        if (ref.isEmpty()) return null;
        char c0 = ref.charAt(0);

        if (c0 == '"' || c0 == '\'') {
            return ref.substring(1, ref.length() - 1);
        }
        if (Character.isDigit(c0) || c0 == '-') {
            try { return Long.parseLong(ref); } catch (NumberFormatException ignored) {}
            try { return Double.parseDouble(ref); } catch (NumberFormatException ignored) {}
        }
        return switch (ref) {
            case "true"  -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            case "null"  -> null;
            default -> {
                // Path traversal: speaker.languages, event.id, …
                String[] segments = ref.split("\\.");
                Object cur = ctx.get(segments[0]);
                for (int i = 1; i < segments.length && cur != null; i++) {
                    cur = property(cur, segments[i]);
                }
                yield cur;
            }
        };
    }

    /**
     * Read a property by name from a record (or any JavaBean). For records the
     * accessor is the component name itself ({@code speaker.languages()}).
     */
    private static Object property(Object target, String name) {
        try {
            // Records: accessor is named after the component.
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException e1) {
            // Fall back to JavaBean getter.
            try {
                String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                return target.getClass().getMethod(getter).invoke(target);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalArgumentException(
                        "No property '" + name + "' on " + target.getClass().getSimpleName(), e2);
            }
        }
    }

    // ---- operators ----------------------------------------------------------

    private enum Op {
        INTERSECTS(" intersects ", (l, r) -> {
            if (!(l instanceof Collection<?> lc) || !(r instanceof Collection<?> rc)) return false;
            Set<Object> small = lc.size() <= rc.size() ? Set.copyOf((Collection<Object>) lc) : Set.copyOf((Collection<Object>) rc);
            Collection<?> big = lc.size() <= rc.size() ? rc : lc;
            for (Object o : big) if (small.contains(o)) return true;
            return false;
        }),
        CONTAINS(" contains ", (l, r) -> l instanceof Collection<?> lc && lc.contains(r)),
        EQ("==", java.util.Objects::equals),
        NEQ("!=", (l, r) -> !java.util.Objects.equals(l, r));

        private final String token;
        private final BinaryOp impl;

        Op(String token, BinaryOp impl) { this.token = token; this.impl = impl; }

        static Op detect(String clause) {
            // Order matters: check '!=' / '==' before substring scans pick up smaller tokens.
            for (Op op : new Op[]{INTERSECTS, CONTAINS, NEQ, EQ}) {
                if (clause.contains(op.token)) return op;
            }
            return null;
        }

        String[] split(String clause) {
            int idx = clause.indexOf(token);
            return new String[]{ clause.substring(0, idx), clause.substring(idx + token.length()) };
        }

        boolean apply(Object lhs, Object rhs) { return impl.apply(lhs, rhs); }
    }

    @FunctionalInterface
    private interface BinaryOp { boolean apply(Object lhs, Object rhs); }
}

