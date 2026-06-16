/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import jakarta.el.ELProcessor;
import jakarta.el.StandardELContext;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hardened companion to {@link ElPredicateEngine}.
 *
 * <p>{@code ElPredicateEngine} (the running default in gem #3) uses a regex
 * <em>denylist</em> over the predicate text. That gets you started, but a
 * denylist is fundamentally a speed bump — a sufficiently creative expression
 * can route around any string match.
 *
 * <p>{@code SafeELEvaluator} is the destination: it pairs {@link ELProcessor}
 * with a {@link SafeELResolver} that <strong>whitelists types</strong> (and a
 * short list of static classes), so reflection, the class loader, {@code
 * Runtime}, {@code System}, {@code ProcessBuilder} and threads are simply
 * unreachable. Every evaluation also runs under a per-call wall-clock timeout
 * via {@link Timeouts}, so a pathological predicate cannot block a request
 * thread.
 *
 * <p><b>How it relates to gem #3:</b> The denylist {@code ElPredicateEngine}
 * stays the default and remains backward-compatible with gem #2's infix
 * {@code intersects}/{@code contains} operators. This class is the strict,
 * "real" safe pattern that the blog post describes — pure EL, no DSL rewrite,
 * type-whitelisted, time-budgeted, fail-closed.
 *
 * <p><b>Inputs are still expected to be trusted</b> (operator-controlled
 * {@code rules.json}, not end-user input). The sandbox is defense-in-depth —
 * the same lesson the EL injection CVEs in the bonus post all illustrate.
 */
public final class SafeELEvaluator {

    private static final Logger LOG = System.getLogger(SafeELEvaluator.class.getName());

    /** Reject obviously hostile predicate text before EL ever parses it. */
    private static final Pattern OBVIOUSLY_HOSTILE = Pattern.compile(
            "Runtime|ProcessBuilder|Thread|System\\.|java\\.lang\\.|forName|exec\\s*\\(");

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(50);
    private static final int DEFAULT_MAX_RESOLUTIONS = 64;

    private static final PolicyFunctions FUNCTIONS = new PolicyFunctions();

    private final Duration timeout;
    private final int maxResolutions;

    /** Production defaults: 50 ms timeout, 64-resolution budget. */
    public SafeELEvaluator() {
        this(DEFAULT_TIMEOUT, DEFAULT_MAX_RESOLUTIONS);
    }

    /** Configurable timeout and budget — exposed for tests and rare tuning. */
    public SafeELEvaluator(Duration timeout, int maxResolutions) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxResolutions < 1) {
            throw new IllegalArgumentException("maxResolutions must be >= 1");
        }
        this.timeout = timeout;
        this.maxResolutions = maxResolutions;
    }

    /**
     * Evaluate {@code predicate} against {@code (speaker, event)}.
     *
     * <p>Empty / blank predicates allow; everything else fails closed —
     * any whitelist violation, timeout, or exception is treated as
     * <em>deny</em> and logged at WARNING level.
     */
    public boolean evaluate(String predicate, Speaker speaker, ConfEvent event) {
        if (predicate == null || predicate.isBlank()) {
            return true;
        }
        if (OBVIOUSLY_HOSTILE.matcher(predicate).find()) {
            LOG.log(Level.WARNING, "[safe-el] rejected hostile predicate text: {0}", predicate);
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                    Timeouts.callWithTimeout(timeout, () -> evalSandboxed(predicate, speaker, event)));
        } catch (Timeouts.EvaluationTimeoutException e) {
            LOG.log(Level.WARNING, "[safe-el] denied (timeout): {0} ({1})", predicate, e.getMessage());
            return false;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "[safe-el] denied (sandbox or EL error): {0} ({1})",
                    predicate, e.getMessage());
            return false;
        }
    }

    /** Per-call: a fresh processor + resolver so the resolution budget resets. */
    private Object evalSandboxed(String predicate, Speaker speaker, ConfEvent event) {
        ELProcessor p = new ELProcessor();
        StandardELContext ctx = p.getELManager().getELContext();
        ctx.addELResolver(new SafeELResolver(maxResolutions));   // installed at the front
        ctx.getImportHandler().importClass(LocalDate.class.getName());
        ctx.getImportHandler().importClass(Instant.class.getName());

        p.defineBean("speaker", toMap(speaker));
        p.defineBean("event",   toMap(event));
        p.defineBean("fn",      FUNCTIONS);
        return p.eval(predicate);
    }

    private static Map<String, Object> toMap(Speaker s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username",  s.username());
        m.put("languages", s.languages());
        m.put("regions",   s.regions());
        m.put("tracks",    s.tracks());
        m.put("roles",     s.roles());
        // portfolio is intentionally omitted: TalkTitle is not on the type
        // whitelist, so predicates cannot navigate into talk objects.
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

