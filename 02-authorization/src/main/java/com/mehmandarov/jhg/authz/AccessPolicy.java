/*
 * Copyright 2026 Rustam Mehmandarov.
 */
package com.mehmandarov.jhg.authz;

import com.mehmandarov.jhg.domain.ConfEvent;
import com.mehmandarov.jhg.domain.Speaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Portable, in-process authorization policy — the heart of gem #2. Combines:
 * <ol>
 *   <li>A live {@link PolicyRuleSet} reloaded by a {@link WatchService}
 *       whenever the policy file changes on disk (hot reload, no redeploy).</li>
 *   <li>An endpoint-level decision {@link #canAccess(Set, String, String)},
 *       enforced by the JAX-RS {@code AuthorizationFilter}.</li>
 *   <li>A row-level decision {@link #canSee(Speaker, ConfEvent)} that runs the
 *       rule's {@code predicate} via {@link PredicateEvaluator} — gem #3 swaps
 *       that engine for sandboxed Jakarta EL.</li>
 * </ol>
 *
 * <p><strong>Why plain CDI + JAX-RS and not container-managed JACC?</strong>
 * Portability. This engine is just {@code @RolesAllowed} + a request filter, so
 * it runs unchanged on Open Liberty, <em>Helidon and Quarkus</em>. When
 * you outgrow that — container-wide pluggable policy governing every component
 * type uniformly — <em>Jakarta Authorization (JACC) 3.0</em> is the full-profile
 * escalation: the same rules plugged into the container's
 * {@code jakarta.security.jacc.Policy} SPI instead of a filter. (Covered as a
 * bonus, not part of the portable core.)
 *
 * <p>Resolution order for the policy file location:
 * <ol>
 *   <li>{@code JHG_POLICY_FILE} env var — explicit absolute or relative path.</li>
 *   <li>{@code ${user.dir}/policy/rules.json} — repo-root layout for {@code mvn liberty:dev}.</li>
 *   <li>Classpath fallback {@code policy/rules.json}, copied to {@code ${java.io.tmpdir}/jhg-rules.json}
 *       so the {@link WatchService} has something to watch.</li>
 * </ol>
 */
@ApplicationScoped
public class AccessPolicy {

    private static final Logger LOG = System.getLogger(AccessPolicy.class.getName());
    private static final String CLASSPATH_DEFAULT = "policy/rules.json";
    private static final String DEFAULT_TMP_NAME  = "jhg-rules.json";

    private final AtomicReference<PolicyRuleSet> current = new AtomicReference<>(PolicyRuleSet.empty());
    private Path watchedFile;
    private Thread watcherThread;
    private volatile boolean stopping;

    @Inject
    PredicateEngine predicates;

    @PostConstruct
    void start() {
        try {
            this.watchedFile = resolvePolicyFile();
            reload();
            startWatcher();
            LOG.log(Level.INFO, "[authz] watching policy at {0} ({1} rules loaded)",
                    watchedFile, current.get().rules().size());
        } catch (IOException e) {
            LOG.log(Level.ERROR, "[authz] failed to initialise policy", e);
        }
    }

    @PreDestroy
    void stop() {
        stopping = true;
        if (watcherThread != null) watcherThread.interrupt();
    }

    // ---- public API ---------------------------------------------------------

    /** Current immutable rule set. Reloaded out-of-band by the watcher. */
    public PolicyRuleSet currentRules() { return current.get(); }

    /**
     * Endpoint-level decision: does <em>any</em> rule allow the given subject
     * to perform {@code action} on {@code resource}?
     */
    public boolean canAccess(Set<String> subjectRoles, String action, String resource) {
        for (PolicyRule rule : current.get().rules()) {
            if (rule.subjectMatches(subjectRoles)
                    && rule.actionMatches(action)
                    && rule.resourceMatches(resource)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Row-level decision: filter callback used by {@code EventsResource} to
     * decide whether {@code speaker} should see {@code event} in their list.
     * The first rule whose subject and resource match wins; an empty/missing
     * predicate is treated as allow.
     */
    public boolean canSee(Speaker speaker, ConfEvent event) {
        for (PolicyRule rule : current.get().rules()) {
            if (rule.subjectMatches(speaker.roles())
                    && rule.resourceMatches("/api/events/" + event.id())) {
                return predicates.evaluate(rule.predicate(), speaker, event);
            }
        }
        return false;   // fail-closed
    }

    // ---- file resolution + hot reload --------------------------------------

    private static Path resolvePolicyFile() throws IOException {
        String env = System.getenv("JHG_POLICY_FILE");
        if (env != null && !env.isBlank()) return Path.of(env);

        Path repoLayout = Path.of(System.getProperty("user.dir"), "policy", "rules.json");
        if (Files.exists(repoLayout)) return repoLayout;

        // Classpath fallback → copy to a known temp location so we can watch it.
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), DEFAULT_TMP_NAME);
        if (!Files.exists(tmp)) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try (InputStream in = cl.getResourceAsStream(CLASSPATH_DEFAULT)) {
                if (in == null) throw new IOException("policy/rules.json not on classpath");
                Files.createDirectories(tmp.getParent());
                Files.copy(in, tmp);
            }
        }
        return tmp;
    }

    private void reload() throws IOException {
        try (InputStream in = Files.newInputStream(watchedFile)) {
            PolicyRuleSet next = PolicyRuleSet.parse(in);
            current.set(next);
            LOG.log(Level.INFO, "[authz] reloaded policy: {0} rules", next.rules().size());
        }
    }

    private void startWatcher() throws IOException {
        Path dir = watchedFile.toAbsolutePath().getParent();
        WatchService ws = dir.getFileSystem().newWatchService();
        dir.register(ws,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE);

        watcherThread = new Thread(() -> watchLoop(ws), "jhg-policy-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void watchLoop(WatchService ws) {
        String fileName = watchedFile.getFileName().toString();
        while (!stopping) {
            WatchKey key;
            try {
                key = ws.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            boolean touched = key.pollEvents().stream()
                    .anyMatch(ev -> ev.context() != null
                            && fileName.equals(ev.context().toString()));
            if (touched) {
                try {
                    Thread.sleep(50);   // de-bounce typical editor save patterns
                    reload();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException ioe) {
                    LOG.log(Level.WARNING, "[authz] reload failed: {0}", ioe.getMessage());
                }
            }
            if (!key.reset()) return;
        }
    }
}

