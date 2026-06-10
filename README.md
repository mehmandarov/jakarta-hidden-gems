# Jakarta EE Hidden Gems

> *Cloud-Native, Reactive, and AI-Ready — Without Leaving the Spec.*

A working demo repository, built around one use case: **ConfSpeakerHub**, a personal radar for tech conferences with grounded LLM matchmaking against the speaker's own talk portfolio.

The thesis: **grounded retrieval beats unconstrained generation.** The framing, in four beats:

1. **There are too many AI-generated talks and abstracts.** The conference pipeline is filling up with generic, machine-written slop.
2. **People think this is a *great* use of AI** — "just have it write my talk." It's the obvious application, so everyone reaches for it.
3. **But it doesn't replace creativity.** Your ideas, your angle, your talks are the one thing AI can't generate for you.
4. **So use AI for something actually useful** — grounded matchmaking against *your own* portfolio — and pick up five hidden gems of Jakarta EE you can use in your own applications along the way.

## The five hidden gems

| # | Spec | Theme | Alternatives / escalations |
|---|---|---|---|
| 1 | Jakarta Interceptors | Observability | MicroProfile Telemetry, Spring AOP |
| 2 | `@RolesAllowed` (Jakarta Annotations) + JAX-RS ABAC filter | Authorization | Jakarta Authorization (JACC) 3.0 · OPA, Cedar · Spring Security |
| 3 | Jakarta Expression Language (EL) | Policy & rules | Drools, OPA/Rego, MVEL, Spring SpEL |
| 4 | Jakarta REST SSE + `@Asynchronous` | Reactive | WebFlux, Reactor, Mutiny |
| 5 | Jakarta JSON-P (`JsonParser`) | AI | Langchain4j, Spring AI, OpenAI SDK |
| 6 | **Capstone** — all five composing into ConfSpeakerHub | | |

> **Portable by design.** All five gems are Jakarta specs that run unchanged on
> every Jakarta runtime — Open Liberty, **Helidon, and Quarkus** (JVM mode).
> Gems #2 and #3 compose: the ABAC filter's predicates are evaluated by
> sandboxed Jakarta EL.

**Bonuses & escalations** (covered but not part of the core five):

- **MicroProfile Reactive Messaging** — Kafka/Redpanda decoupling for event
  ingest + audit. The portable, broker-backed messaging story; pairs with a
  verbal mention of **Jakarta Connectors (JCA)** as the full-profile JTA-atomic
  escalation.
- **MicroProfile JWT** — portable token authentication; the auth-side companion
  to gem #2's authorization. Already used by the capstone.
- **Jakarta EL sandbox + CVEs** — security deep-dive on gem #3: three real EL
  injection CVEs and the `SafeELEvaluator` walk-through.


## Tech stack (TL;DR)

- **Java 25** · Maven multi-module
- **Open Liberty** (primary; MicroProfile 7.1 incl. `mpJwt-2.1` +
  `mpTelemetry-2.1`), **Helidon MP** and **Quarkus** (portability
  proofs — each builds its own runnable fat-jar via a dedicated *runner module*)
- **Embedded H2 + JPA** via `@DataSourceDefinition` (no Postgres) — only used by the Reactive Messaging bonus
- **MicroProfile JWT** for authentication (no Keycloak; dev tokens minted at `/api/dev/token`)
- **Redpanda** for Kafka (bonus: Reactive Messaging)
- **Ollama** running **`qwen2.5:7b`** for the LLM (gem #5), `llama3.2:3b` as fallback
- **OTel collector + Jaeger** for traces (gem #1)
- Plain HTML + ~30 lines of vanilla JS for the dashboard

One `docker compose up`, no internet required.

## Quickstart

```bash
git clone https://github.com/mehmandarov/jakarta-hidden-gems.git
cd jakarta-hidden-gems
docker compose up
# open http://localhost:9080/
```

### Build & run locally

```bash
# build & test every module (gems + all three runners)
mvn clean package

# Open Liberty hot-reload dev mode
mvn -pl 06-capstone/runners/runner-liberty -am liberty:dev
# open http://localhost:9080/
```

The capstone app (`06-capstone`) is packaged as a **runtime-neutral library jar**.
Three thin **runner modules** under `06-capstone/runners/` turn it into a
**single runnable fat-jar per runtime** — same code, same `java -jar` shape,
swap the runtime by picking a module:

| Runtime | Runner module | Build | Run |
|---|---|---|---|
| **Open Liberty** (default) | `runner-liberty` | `mvn -pl 06-capstone/runners/runner-liberty -am package` | `java -jar 06-capstone/runners/runner-liberty/target/confspeakerhub.jar` |
| **Helidon MP** | `runner-helidon` | `mvn -pl 06-capstone/runners/runner-helidon -am package` | `java -jar 06-capstone/runners/runner-helidon/target/runner-helidon.jar` |
| **Quarkus** | `runner-quarkus` | `mvn -pl 06-capstone/runners/runner-quarkus -am package` | `java -jar 06-capstone/runners/runner-quarkus/target/runner-quarkus-runner.jar` |

> **How the runners differ.**
> * Liberty bakes the app into a self-contained runnable fat-jar (kernel + features
>   resolved entirely from **Maven Central** — no `public.dhe.ibm.com` download).
> * Helidon boots MicroProfile straight from the classpath, packaged as a thin jar
>   with dependencies copied to `target/libs/`.
> * Quarkus produces a single uber-jar.
> * The gems layer their richer beans in with a portable CDI `@Alternative` +
>   `@Priority`, so the **gem source is identical across all three runtimes**.

### Integration tests (Testcontainers)

The messaging integration test spins up a real **Redpanda** broker via
Testcontainers and verifies a Kafka round-trip:

```bash
mvn -pl bonus-reactive-messaging verify
```

Requires Docker. On **Colima** (no Docker Desktop), point Testcontainers at the
Colima socket first:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
```

### Docker — one image per runtime

Each runner has its **own multi-stage Dockerfile** (build context = repo root):

```bash
# Liberty (default)
docker build -t confspeakerhub/liberty:dev -f 06-capstone/runners/runner-liberty/Dockerfile .

# Helidon MP
docker build -t confspeakerhub/helidon:dev -f 06-capstone/runners/runner-helidon/Dockerfile .

# Quarkus
docker build -t confspeakerhub/quarkus:dev -f 06-capstone/runners/runner-quarkus/Dockerfile .
```

`docker compose up` runs the **Liberty** image by default; switch runtimes by
editing the `app.build.dockerfile` line in `docker-compose.yml` to point at the
Helidon or Quarkus Dockerfile.

## License

© 2026 Rustam Mehmandarov. All Rights Reserved.

---

Built by **[Rustam Mehmandarov](https://mehmandarov.com/)**
