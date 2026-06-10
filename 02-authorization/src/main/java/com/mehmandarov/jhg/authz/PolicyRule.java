/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

/**
 * One ABAC policy rule, parsed straight off the JSON file.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code subject} — Jakarta Security role assertion in {@code role:NAME} form
 *       (e.g. {@code role:SPEAKER}). Wildcards not supported on purpose.</li>
 *   <li>{@code action} — HTTP verb ({@code GET}, {@code POST}, …) or {@code "*"}.</li>
 *   <li>{@code resource} — JAX-RS path with optional trailing {@code /*} wildcard
 *       (e.g. {@code /api/events/*}).</li>
 *   <li>{@code predicate} — see {@link PredicateEvaluator}; empty == allow.</li>
 * </ul>
 */
public record PolicyRule(String subject,
                         String action,
                         String resource,
                         String predicate) {

    public PolicyRule {
        if (subject  == null || subject.isBlank())  throw new IllegalArgumentException("subject required");
        if (action   == null || action.isBlank())   throw new IllegalArgumentException("action required");
        if (resource == null || resource.isBlank()) throw new IllegalArgumentException("resource required");
        predicate = predicate == null ? "" : predicate;
    }

    /** True if this rule's role assertion matches a role granted to the subject. */
    public boolean subjectMatches(java.util.Set<String> roles) {
        if (subject.startsWith("role:")) {
            return roles.contains(subject.substring("role:".length()));
        }
        return false;
    }

    /** True if {@code action} matches this rule's verb (or rule allows {@code *}). */
    public boolean actionMatches(String verb) {
        return "*".equals(action) || action.equalsIgnoreCase(verb);
    }

    /** True if {@code path} matches the rule's resource pattern. */
    public boolean resourceMatches(String path) {
        if (resource.endsWith("/*")) {
            String prefix = resource.substring(0, resource.length() - 2);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return resource.equals(path);
    }
}

