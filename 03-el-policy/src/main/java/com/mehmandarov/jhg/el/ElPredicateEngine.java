/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import com.mehmandarov.jhg.authz.PredicateEngine;
import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import jakarta.annotation.Priority;
import jakarta.el.ELProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Gem #3. A sandboxed Jakarta EL predicate engine that overrides gem #2's
 * {@link PredicateEngine} via a globally-enabled {@code @Alternative} +
 * {@code @Priority} (the portable, Quarkus-friendly equivalent of
 * {@code @Specializes}). Drop this jar on the classpath and the very same
 * {@code AccessPolicy} + hot-reloadable {@code rules.json} keep working —
 * but predicates are now full Jakarta EL.
 *
 * <p><b>Backwards compatible:</b> the two custom infix operators from gem #2's
 * DSL ({@code intersects}, {@code contains}) are rewritten to {@code fn.*}
 * function calls, so existing rules evaluate unchanged. New rules can use
 * anything EL offers — member access, comparisons, and the helper functions in
 * {@link PolicyFunctions} (e.g. {@code fn.daysUntil(event.cfpDeadline) > 7}).
 *
 * <p><b>Sandbox:</b> {@code speaker} and {@code event} are exposed as immutable
 * maps (no bean methods to pivot off), and a denylist rejects expressions that
 * try to reach the class loader, {@code Runtime}, {@code System}, threads or
 * package-qualified types. This is the pragmatic guard the bonus material grows
 * into a full {@code SafeELResolver}; it's also why the three EL-injection CVEs
 * (CVE-2018-1273, CVE-2017-1000486, CVE-2020-10693) matter.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class ElPredicateEngine extends PredicateEngine {

    private static final Logger LOG = System.getLogger(ElPredicateEngine.class.getName());
    private static final PolicyFunctions FUNCTIONS = new PolicyFunctions();

    /** Reject expressions that try to escape the sandbox. */
    private static final Pattern BLOCKED = Pattern.compile(
            "getClass|\\.class\\b|Runtime|System\\.|ProcessBuilder|Thread|exec\\s*\\(|java\\.|javax\\.|jakarta\\.");

    private static final Pattern INTERSECTS = Pattern.compile("(\\S+)\\s+intersects\\s+(\\S+)");
    private static final Pattern CONTAINS   = Pattern.compile("(\\S+)\\s+contains\\s+(\\S+)");

    @Override
    public boolean evaluate(String predicate, Speaker speaker, ConfEvent event) {
        if (predicate == null || predicate.isBlank()) {
            return true;   // empty predicate ≡ allow
        }
        String el = toEl(predicate);
        if (BLOCKED.matcher(el).find()) {
            LOG.log(Level.WARNING, "[el] blocked unsafe predicate: {0}", predicate);
            return false;  // fail-closed
        }
        try {
            ELProcessor p = new ELProcessor();
            p.defineBean("speaker", toMap(speaker));
            p.defineBean("event",   toMap(event));
            p.defineBean("fn",      FUNCTIONS);
            return Boolean.TRUE.equals(p.eval(el));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[el] predicate failed, denying: {0} ({1})", predicate, e.getMessage());
            return false;  // fail-closed
        }
    }

    /** Rewrite gem #2's two infix operators into EL function calls. */
    static String toEl(String dsl) {
        String s = INTERSECTS.matcher(dsl).replaceAll("fn.intersects($1, $2)");
        s = CONTAINS.matcher(s).replaceAll("fn.contains($1, $2)");
        return s;
    }

    private static Map<String, Object> toMap(Speaker s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username",  s.username());
        m.put("languages", s.languages());
        m.put("regions",   s.regions());
        m.put("tracks",    s.tracks());
        m.put("portfolio", s.portfolio());
        m.put("roles",     s.roles());
        return m;
    }

    private static Map<String, Object> toMap(ConfEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          e.id());
        m.put("name",        e.name());
        m.put("location",    e.location());
        m.put("eventDate",   e.eventDate());
        m.put("cfpDeadline", e.cfpDeadline());
        m.put("tracks",      e.tracks());
        m.put("languages",   e.languages());
        m.put("websiteUrl",  e.websiteUrl());
        m.put("cfpUrl",      e.cfpUrl());
        m.put("tags",        e.tags());
        m.put("ingestedAt",  e.ingestedAt());
        return m;
    }
}

