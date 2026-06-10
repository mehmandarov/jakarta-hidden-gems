# 06-capstone — ConfSpeakerHub (runtime-neutral library + runners)

The always-runnable shell of ConfSpeakerHub. Per-gem modules wire in their
pieces incrementally; CDI scanning composes them. This module is a
**runtime-neutral library jar** — the runnable fat-jars are produced by the
thin **runner modules** under [`runners/`](runners/), one per Jakarta runtime
(Liberty, Helidon MP, Quarkus).

> 🗺️ **Architecture diagrams** (component flow, the "one click, five gems"
> sequence, deployment view): [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

| Endpoint | Phase added | Notes |
|---|---|---|
| `GET /api/health` | 0 | liveness probe (public) |
| `GET /api/events` | 1 (gem #1, week 2) | hard-coded list from `feeds.json`, `@Traced @Timed` |
| `GET /api/events/{id}` | 1 (gem #1, week 2) | single event by id |
| (same, filtered) | 1 (gems #2 + #3, weeks 3–4) | rows filtered per-speaker by the ABAC filter; predicates evaluated by sandboxed Jakarta EL; MP-JWT bearer token required |
| `GET /api/events/stream` | 1 (gem #4, week 5) | SSE feed of new events |
| `GET /api/events/{id}/analyze` | 1 (gem #5, week 6) | SSE stream of LLM analysis |
| `GET /api/dev/token?user=rustam` | 0 (dev only) | mints an MP-JWT for `rustam` or `maria` |

## Build

```bash
# from the repo root — produces the default (Liberty) runnable fat-jar
mvn -pl 06-capstone/runners/runner-liberty -am package
java -jar 06-capstone/runners/runner-liberty/target/confspeakerhub.jar
```

The artifact is a self-contained Liberty + features + app fat-jar (kernel and
features resolved from Maven Central); no Liberty install on the host required.

## Run (dev mode, hot-reload)

```bash
mvn -pl 06-capstone/runners/runner-liberty -am liberty:dev
```

The plugin downloads Liberty and installs the features from `server.xml` on
first run, then watches for source changes. After it's up:

```bash
# mint a demo JWT and use it
TOKEN=$(curl -s 'http://localhost:9080/api/dev/token?user=rustam')
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:9080/api/events | jq '. | length'
curl -s http://localhost:9080/api/health | jq
curl -s http://localhost:9080/api/events                   # → 401 Unauthorized
open  http://localhost:9080/
```

### Hot-reloading the policy

Edit [`../policy/rules.json`](../policy/rules.json) on the host while the
container is running — the in-container `WatchService` reloads atomically
within ~100 ms. Try adding `es` to the predicate-allowed languages and re-curl
as `rustam` to see the Spanish-language conferences appear. Predicates can use
the full sandboxed-EL surface (date arithmetic, member access, etc. — see gem #3).

## Run (full stack, Docker Compose)

From the repo root:

```bash
docker compose up --build
```

Services that come up:

| URL | What |
|---|---|
| http://localhost:9080/ | ConfSpeakerHub landing page |
| http://localhost:9080/api/health | Health probe |
| http://localhost:9080/api/events | List of seed events (with OTel spans) |
| http://localhost:16686/ | Jaeger UI — search service `confspeakerhub` |
| http://localhost:11434/ | Ollama (gem #5) |
| `redpanda:9092` | Kafka — only consumed by the `bonus-reactive-messaging` module, not by the capstone itself |

## Run on other runtimes (Helidon MP, Quarkus)

Each runner builds a single `java -jar` fat-jar of the same app:

```bash
# Helidon MP 4.4.1
mvn -pl 06-capstone/runners/runner-helidon -am package
java -jar 06-capstone/runners/runner-helidon/target/runner-helidon.jar

# Quarkus 3.36.1
mvn -pl 06-capstone/runners/runner-quarkus -am package
java -jar 06-capstone/runners/runner-quarkus/target/runner-quarkus-runner.jar
```

Each runner also ships its own Dockerfile — see the runtime table in the
[root README](../README.md#run-on-a-jakarta-ee-runtime).

## Stopping

```bash
docker compose down -v   # -v wipes the Ollama model cache too; omit to keep it
```

