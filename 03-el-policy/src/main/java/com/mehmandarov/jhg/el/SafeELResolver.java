/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.el;

import jakarta.el.ELClass;
import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed guard {@link ELResolver} that turns a general-purpose
 * {@code ELProcessor} into a safe predicate evaluator by <strong>whitelisting
 * types</strong> rather than denylisting strings.
 *
 * <p>This is the "resolver is the destination" half of gem #3: where
 * {@link ElPredicateEngine} runs a regex denylist over the predicate
 * <em>text</em>, this resolver inspects the actual <em>objects</em> an
 * expression touches and refuses anything that is not explicitly allowed.
 *
 * <p>It is installed at the <em>front</em> of the resolver chain (via
 * {@code ELManager.addELResolver}) and acts purely as a veto:
 * <ul>
 *   <li>For an <strong>allowed</strong> access it returns {@code null} without
 *       marking the property resolved, so the standard resolvers
 *       (bean/map/list/static) do the real work.</li>
 *   <li>For a <strong>disallowed</strong> access it throws — which aborts the
 *       whole evaluation. {@link SafeELEvaluator} treats any throw as
 *       <em>deny</em>.</li>
 * </ul>
 *
 * <p>What it enforces:
 * <ul>
 *   <li><b>Instance whitelist</b> – methods/properties may only be resolved on
 *       the small set of base types a policy needs ({@link Map}, {@link
 *       Collection}, {@code CharSequence}, {@code Number}, {@code Boolean},
 *       {@code Character}, {@code Enum}, the {@code java.time} types and the
 *       {@code fn} helper). The class loader, {@code Runtime}, {@code System},
 *       {@code ProcessBuilder}, {@code Thread} and friends are simply never on
 *       the list, so they are unreachable.</li>
 *   <li><b>Static whitelist</b> – static references (e.g. {@code
 *       LocalDate.now()}) are only allowed for {@link LocalDate} and {@link
 *       Instant}, even though {@code java.lang.*} is imported by default. This
 *       is what closes {@code System.exit(0)} / {@code Runtime.getRuntime()}.</li>
 *   <li><b>Blocked methods</b> – {@code getClass}, {@code wait}, {@code clone},
 *       {@code forName}, … are refused even on an otherwise-allowed base, so
 *       {@code ''.getClass()} cannot be used as a reflection pivot.</li>
 *   <li><b>No {@code .class}</b> – the {@code class} property is rejected.</li>
 *   <li><b>Read-only</b> – {@link #setValue} always throws.</li>
 *   <li><b>Resolution budget</b> – a per-evaluation cap on the number of
 *       property/method resolutions, as a cheap denial-of-service guard that
 *       complements {@link SafeELEvaluator}'s wall-clock timeout.</li>
 * </ul>
 *
 * <p><b>Not thread-safe:</b> the resolution counter is per-instance, so a fresh
 * {@code SafeELResolver} is created for every evaluation.
 */
final class SafeELResolver extends ELResolver {

    /** Base object types a predicate may navigate into or call methods on. */
    private static final Set<Class<?>> ALLOWED_INSTANCE_TYPES = Set.of(
            Map.class, Collection.class, CharSequence.class, Number.class,
            Boolean.class, Character.class, Enum.class,
            TemporalAccessor.class, TemporalAmount.class,
            PolicyFunctions.class);

    /** Classes a predicate may reference statically, e.g. {@code LocalDate.now()}. */
    private static final Set<Class<?>> ALLOWED_STATIC_TYPES = Set.of(
            LocalDate.class, Instant.class);

    /** Method names that are never allowed, even on an otherwise-allowed base. */
    private static final Set<String> BLOCKED_METHODS = Set.of(
            "getClass", "wait", "notify", "notifyAll", "finalize", "clone",
            "forName", "newInstance", "exec", "exit", "halt", "getRuntime",
            "load", "loadLibrary", "start", "run");

    private final int maxResolutions;
    private int resolutions;

    SafeELResolver(int maxResolutions) {
        this.maxResolutions = maxResolutions;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        tick();
        if (base == null) {
            return null;                       // identifier / bean lookup → next resolver
        }
        if (base instanceof ELClass elClass) {
            requireAllowedStatic(elClass.getKlass());
            return null;                       // allowed static field
        }
        if (isClassProperty(property)) {
            throw deny("property", property, base);
        }
        requireAllowedInstance(base);
        return null;                           // allowed → next resolver reads it
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method,
                         Class<?>[] paramTypes, Object[] params) {
        tick();
        if (base == null) {
            return null;
        }
        String name = String.valueOf(method);
        if (BLOCKED_METHODS.contains(name)) {
            throw deny("method", name, base);
        }
        if (base instanceof ELClass elClass) {
            requireAllowedStatic(elClass.getKlass());
            return null;                       // allowed static method, e.g. LocalDate.now()
        }
        requireAllowedInstance(base);
        return null;                           // allowed instance method
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        // Policy predicates are read-only: writing through EL is never allowed.
        throw new PropertyNotWritableException(
                "SafeELResolver: policy predicates are read-only");
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return Object.class;
    }

    // ---- guards -------------------------------------------------------------

    private void tick() {
        if (++resolutions > maxResolutions) {
            throw new ELException(
                    "SafeELResolver: predicate exceeded the resolution budget ("
                    + maxResolutions + ") — possible denial-of-service expression");
        }
    }

    private void requireAllowedInstance(Object base) {
        Class<?> type = base.getClass();
        for (Class<?> allowed : ALLOWED_INSTANCE_TYPES) {
            if (allowed.isAssignableFrom(type)) {
                return;
            }
        }
        throw deny("type", type.getName(), base);
    }

    private void requireAllowedStatic(Class<?> klass) {
        if (!ALLOWED_STATIC_TYPES.contains(klass)) {
            throw new ELException(
                    "SafeELResolver: static access to " + klass.getName() + " is not allowed");
        }
    }

    private static boolean isClassProperty(Object property) {
        return property != null
                && "class".equals(String.valueOf(property).toLowerCase(Locale.ROOT));
    }

    private static ELException deny(String what, Object detail, Object base) {
        return new ELException("SafeELResolver: blocked " + what + " '" + detail
                + "' on " + base.getClass().getName());
    }
}

