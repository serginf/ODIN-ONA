# ODIN Hybrid Refactor Plan — Java Core, Python Edges

---

## Baseline (Phase 0 — captured 2026-05-11)

### Java version
- **JDK 11 (Temurin)** — confirmed by `build-logic/src/main/groovy/odin.java-common-conventions.gradle`
  (`toolchain { languageVersion = JavaLanguageVersion.of(11) }`) and `backend/Dockerfile`
  (`FROM gradle:8.5.0-jdk11`).
- The refactor plan targets **JDK 17 LTS** as the upgrade destination (Phase 3, when Spring Boot is removed).
  Until then, keep JDK 11 for build reproducibility.
- **Local build note:** Gradle 8.5 does not support JDK 25+. If your system default JDK is 25 (as on
  the dev machine as of 2026-05-11), the Makefile auto-detects Temurin 11 via
  `/usr/libexec/java_home -v 11` and sets `JAVA_HOME` automatically. For raw Gradle invocations,
  prefix with `export JAVA_HOME=$(/usr/libexec/java_home -v 11) &&`.

### Dependency snapshot (baseline, pre-refactor)
| Component | Version |
|---|---|
| Spring Boot | 2.5.0 |
| Spring Dependency Management | 1.1.0 |
| Hibernate ORM | 6.0.0.Final |
| H2 (embedded DB) | 2.1.214 |
| Apache Jena (all libs) | 4.1.0 |
| Apache Spark SQL | 3.3.2 |
| Delta Core (Spark) | 2.3.0 |
| GraalVM native plugin | 0.9.20 (unused in practice) |
| Gradle | 8.5 |
| JUnit | 5.9.1 (api module), 5.10.0 (modules via convention) |

### Why refactoring instead of rewriting
A full Python rewrite would discard the algorithmic IP in NextiaCore/DI/JD/BS/MG and produce no
demonstrable value for ~6 months. This plan keeps the Java domain modules intact, strips
Spring/H2/embedded-Jena cruft from the API layer, and replaces REST-to-Python integration with a
typed, data-efficient contract (gRPC + Arrow Flight). Every phase produces working, demoable software.

### CI rule (documented here per Phase 0 plan)
CI must be green before any PR merges. The CI workflow runs:
1. `./gradlew build` (compile + unit tests + ArchUnit)
2. `make test-golden` (golden-fixture comparison for NextiaBS; extends to NextiaDI/MG as fixtures accumulate)
3. Ruff lint on Python modules (advisory until baseline is clean)

### Fresh-clone verification discipline
Before closing any phase, verify on a clean checkout:
```bash
git clone <repo> odin-verify && cd odin-verify
docker compose up -d
cd backend && ./gradlew build
cd .. && make test-golden
```
Then run the full demo flow through the UI. Document deviations in this file.

---

## Context

You have a working ODIN backend (~130 Java files across 7 Nextia modules + a Spring Boot API layer) with two existing Python modules (`IntentAnticipation`, `IntentSpecification2WorkflowGenerator`) integrated today as standalone Flask apps that the Java side calls via `RestTemplate`. Two real problems are driving the desire to change:

1. **Tech debt cruft.** Spring Boot 2.5, Hibernate 6, embedded H2, embedded Jena, GraalVM native plugin — heavy for what is mostly a research/demo backend, and slow to boot, test, and reason about.
2. **ML/AI integration friction.** New components are naturally Python (LLMs, embeddings, profiling). REST + JSON over `RestTemplate` is the worst of both worlds: high serialization tax for data-heavy calls (NextiaJD profiles, NextiaBS extraction), no schema typing, ad-hoc error semantics.

A full Python rewrite (the alternative plan) would discard the algorithmic IP in NextiaCore/DI/JD/BS/MG and produce no demonstrable value for ~6 months. This plan instead **keeps the Java domain modules intact**, **strips the Spring/H2/embedded-Jena cruft from the API layer**, and **replaces the REST-to-Python integration with a typed, data-efficient contract** so that future Python work plugs in cleanly.

The intended outcome: at the end of the plan you have (a) a leaner Java backend with the same algorithmic capabilities, (b) external Postgres + Fuseki replacing embedded stores, (c) a stable Java↔Python IPC layer (gRPC + Arrow Flight), (d) the existing Python modules migrated to that contract, and (e) one new Python ML/AI component plugged in to prove the pattern. Every phase produces working, demoable software.

---

## Architectural ground rules (apply throughout, in Java)

These mirror the rewrite plan's rules but in the existing codebase. They are cheap to add incrementally and painful to retrofit later.

1. **`tenantId` on every persisted entity.** Single default tenant for now. A `TenantContext` is set per-request (Spring filter → later a Javalin handler) and enforced in repositories.
2. **Append-only `events` table from day one.** `(id, tenant_id, actor, event_type, payload_json, created_at)`. Repositories write a row on every meaningful mutation. Don't consume it yet — just populate it.
3. **Hexagonal layering inside `api/`.** `domain/` (records / POJOs, framework-free), `storage/` (JPA or JOOQ), `services/` (pure business logic), `controllers/` (web only). Services never import controllers or Spring web types.
4. **No new Spring annotations in services/domain.** New code introduced in any phase respects rule 3, even if older code in the same package still violates it. Refactor opportunistically, never as the main goal of a phase.

---

## Phase 0 — Baseline, freeze, and golden-fixture capture

**Goal:** A reproducible local build + a fixtures harness that lets every later phase prove behavioral parity.

**Estimated effort:** 1 weekend.

### Tasks

**Baseline reproducibility**
- Pin Java version (Temurin 17 or 21 — confirm what the build currently expects, document in `DECISIONS.md`).
- `docker compose up` brings up the full stack as it is today (so you have a fixed reference baseline).
- Seed [DECISIONS.md](DECISIONS.md) with: Java version, baseline Spring/Hibernate/Jena versions, why we're refactoring rather than rewriting.

**Golden fixtures (the behavioral contract)**
- Create `tests/fixtures/golden/` at the repo root with subfolders per module (`nextiabs/`, `nextiajd/`, `nextiadi/`, `nextiamg/`).
- Capture 5–10 inputs per module, run them through the **current** Java implementation, and store the output as canonical JSON / N-Triples (sorted for deterministic comparison). These become the contract every later phase must not break.
- Add a `make test-golden` target that runs all golden-fixture comparisons. Initially trivially green against the unchanged code.

**ArchUnit (the architectural contract)**
- Add `com.tngtech.archunit:archunit-junit5` to `backend/api/build.gradle` (test dependency).
- Create `backend/api/src/test/java/.../arch/ArchitectureTest.java` with the architectural ground rules from the top of the plan as JUnit assertions:
  - No class under `controllers/` (or any `*Controller`) imports anything under `storage/`.
  - No class under `services/` or any `*Service` imports `org.springframework.web.*`, FastAPI-equivalent web types, or any `*Controller`.
  - All classes annotated `@Entity` have a `tenantId` field (post-Phase 1 — this rule is added in Phase 1, scaffolded now).
  - No `@Autowired` field annotations exist anywhere — all injection is constructor-based (post-Phase 1.7; rule scaffolded now, asserted-empty for now).
  - All `*Repository` classes import `TenantContext`.
- Initially these tests pass-trivially against today's code where applicable; they activate as later phases land. The test file is the executable architecture documentation.

**CI**
- Add `.github/workflows/ci.yml` running on push and PR:
  - `./gradlew build` (compile + unit tests + ArchUnit)
  - `make test-golden`
  - Ruff + (eventually) mypy on the Python modules
  - Cache Gradle and Python dependencies between runs
- CI must be green before any PR merges. Document the rule in `DECISIONS.md`.

**Verify-on-fresh-clone discipline**
- Document in `DECISIONS.md`: every phase's exit checklist includes a `git clone` to a fresh directory, `docker compose up`, `./gradlew build`, `make test-golden`, manual demo flow. This catches "works on my machine" drift across the months of part-time work.

### Critical files

- `backend/api/build.gradle` — current dependency snapshot, add ArchUnit
- `docker-compose.yml`
- New: `tests/fixtures/golden/`, `Makefile` or `scripts/test_golden.sh`
- New: `backend/api/src/test/java/.../arch/ArchitectureTest.java`
- New: `.github/workflows/ci.yml`
- New: `DECISIONS.md`

### Exit criteria

- [ ] `./gradlew build` and `docker compose up` work cleanly on a fresh checkout.
- [ ] `make test-golden` runs and passes.
- [ ] At least 5 fixtures captured per Nextia module that has a deterministic input→output transformation.
- [ ] ArchUnit test file exists and passes; rules are scaffolded for the architectural ground rules even if some assert-empty until later phases populate the layout.
- [ ] CI workflow runs on push and is green.
- [ ] **Fresh-clone verification:** clone the repo to a new directory on your machine, run the full demo flow end-to-end. Document the exact steps in `README.md`.

---

## Phase 1 — External Postgres + per-aggregate repositories

**Goal:** All relational persistence runs against an external Postgres, enforcing `tenant_id` at the data layer via per-aggregate repositories that replace the generic `ORMStoreInterface`. H2 dependency deleted.

**Estimated effort:** 2 weekends.

### Why first

H2 is the cheapest cruft to remove, gives you a real schema migration tool (Flyway) on day one, and unlocks `tenant_id` columns and the event log. JPA/Hibernate stays for now — replacing the ORM is a separate, larger effort that may never be needed.

The current `ORMStoreInterface` (`save`/`findById`/`getAll`/`deleteOne` over `Class<T>`) is anemic: it wraps JPA without adding expressiveness, can't enforce tenant filtering on reads (its signatures don't know which entities have `tenant_id`), opens and closes an `EntityManager` per call (no atomic multi-step transactions), and catches every exception as `InternalServerErrorException` (hides validation/constraint errors). Replacing it now — while we're already touching every entity for the `tenant_id` column — is much cheaper than retrofitting later, and removes the factory/singleton/`@Component` confusion that would otherwise leak into Phase 3 (Spring removal).

### Tasks

**Postgres + Flyway**
- Add Postgres 16 service to `docker-compose.yml`.
- Add Flyway plugin to `backend/api/build.gradle`. Generate the initial schema migration (`V1__baseline.sql`) by exporting the H2 schema Hibernate currently produces.
- Switch `application.properties` (or equivalent) from H2 URL to Postgres URL via env var.
- Remove H2 runtime dependency from [build.gradle](backend/api/build.gradle).

**Tenant context + event log**
- Add `tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'` to every persisted entity. Migration `V2__tenant_id.sql` adds the column with the default and an index per table.
- Introduce `TenantContext` (request-scoped bean for now) and a Spring filter that reads `X-Tenant-Id` (defaulting to the default UUID if absent — keeps existing frontend working unchanged).
- Add a JPA entity listener / `@PrePersist` hook that stamps `tenantId` from `TenantContext` on every save.
- Add the `events` table (`V3__events.sql`) + an `EventLog` service with a single method `append(tenantId, actor, eventType, payload)`. Wire it into the 3–4 most important services (Project, Dataset, IntegratedGraph). Don't try to be exhaustive; this is structural seeding.

**Cross-cutting entity discipline (apply to every persisted entity in this phase)**
- **Optimistic locking:** add `@Version private Long version;` to every entity. JPA throws `OptimisticLockException` on concurrent edits; the `GlobalExceptionHandler` (cleaned up in Phase 1.7) translates that to HTTP 409 Conflict. Cost: one annotation per entity. Benefit: silent lost-update bugs become loud conflict errors.
- **Time as `Instant`, not `LocalDateTime`.** Every timestamp column (`created_at`, `updated_at`, `events.created_at`, etc.) maps to `java.time.Instant`. `LocalDateTime` has no timezone and silently uses the JVM default zone — a guaranteed source of bugs the moment dev (Barcelona) and prod (any deployment) diverge. Migration `V4__instant_timestamps.sql` ensures all timestamp columns are `TIMESTAMP WITH TIME ZONE` in Postgres.
- **Try-with-resources discipline.** Repository methods that hold an `EntityManager` use try-with-resources, not `try { ... } finally { em.close(); }`. Same for any `Connection`, `Statement`, or `ResultSet` that gets touched. ArchUnit can't easily check this — flag it as a code-review rule in `DECISIONS.md` and grep for `finally` + `close()` after the phase to catch leftovers.

**Reorganize the `Project` entity**

Today, `backend/api/src/main/java/edu/upc/essi/dtim/odin/projects/pojo/Project.java` is named like a DTO and lives in a `pojo/` folder, but it is in fact the persisted domain entity (used by `ProjectService` via `ORMStoreFactory.getInstance().findById(Project.class, id)`; there is no other `Project` class in the codebase, including NextiaCore). The misclassification is misleading on its own, and the class also bundles too much: it embeds `IntegratedGraphJenaImpl` (a Jena impl Phase 1.5 deletes), embeds full lists of `repositories`/`integratedDatasets`/`dataProducts`/`intents` (each its own aggregate with its own lifecycle), and mixes permanent state with `temporal*` workflow state.

- Move `Project.java` out of `projects/pojo/` to a `storage/entities/Project.java` (alongside the per-aggregate repositories introduced in this phase).
- Replace embedded graph objects with ID references:
  - `IntegratedGraphJenaImpl integratedGraph` → `UUID integratedGraphId` (resolved via `RdfStoreClient` in Phase 2 when actually needed)
  - `IntegratedGraphJenaImpl temporalIntegratedGraph` → moved out of `Project` entirely (see below)
- Replace embedded lists with FK relationships fetched on demand:
  - `List<DataRepository> repositories` → `DataRepositoryRepository.findByProjectId(projectId)`
  - `List<Dataset> integratedDatasets` → `DatasetRepository.findIntegratedByProjectId(projectId)`
  - `List<DataProduct> dataProducts` → `DataProductRepository.findByProjectId(projectId)`
  - `List<Intent> intents` → `IntentRepository.findByProjectId(projectId)`
- Decide where transient integration state lives:
  - Option A — a separate `IntegrationDraft` entity persisted alongside `Project` with FK back to it, lifetime = duration of the integration workflow.
  - Option B — kept entirely in memory in a service-layer cache (Caffeine or similar), lost on restart.
  - Pick one in this phase; document the choice in `DECISIONS.md`. Option A is safer for the demo (survives restarts); Option B is simpler if the workflow always completes within a session.

**Replace `ORMStoreInterface` with per-aggregate repositories**
- For each service that today holds an `ORMStoreInterface ormStore = ORMStoreFactory.getInstance()`, create a corresponding repository class:
  - `ProjectRepository`, `DatasetRepository`, `WorkflowRepository`, `RepositoryRepository` (or rename the entity to avoid the naming clash), `MappingsRepository`, `DataProductRepository`, `IntentRepository`, `QueryRepository`.
- Each repository takes `EntityManager` and `TenantContext` via constructor (Spring `@Component` for now; constructor wiring in Phase 3 removes the annotation).
- Each repository exposes domain-specific methods. The default set per aggregate:
  ```java
  Optional<Project> findById(UUID id);                     // WHERE id = :id AND tenant_id = :t
  Page<Project> findAll(int offset, int limit);            // WHERE tenant_id = :t LIMIT :l OFFSET :o
  long countAll();                                         // WHERE tenant_id = :t — pairs with findAll for total
  Project save(Project p);                                 // tenantId stamped via @PrePersist; @Version handles concurrent edits
  void delete(UUID id);                                    // WHERE id = :id AND tenant_id = :t
  ```
  Plus any aggregate-specific finders that today live as one-off `em.createQuery` calls in the service layer — pull those down into the repository.
- **Pagination is not optional.** No repository method returns an unbounded `List<T>`. `Page<T>` is a small record `Page<T>(List<T> items, long total, int offset, int limit)`. Controllers accept `?offset=&limit=` query params; the OpenAPI spec (Phase 3) documents the defaults. Adding pagination retroactively forces frontend changes; doing it now is free.
- **Every read method filters by `tenant_id`.** No exceptions. This is the single rule that makes the layer earn its keep over `ORMStoreInterface`.
- Replace `ORMStoreFactory.getInstance()` calls in services with constructor-injected repositories. Delete `ORMStoreInterface`, `ORMStoreJpaImpl`, and `ORMStoreFactory`.
- **Stop swallowing exceptions.** Let JPA's `EntityNotFoundException`, `OptimisticLockException`, and `ConstraintViolationException` propagate. Translate at the controller boundary only (Phase 3 will revisit this when controllers move to Javalin).
- **Compose transactions in services where atomicity matters.** Where a service needs to save a Project + its Datasets atomically, wrap the multi-repository call in a single `EntityTransaction` (or `@Transactional` for now). The new repositories don't open per-call transactions — they assume an ambient `EntityManager` lifecycle managed by the service or a Spring `@Transactional`.

### Critical files

- [backend/api/build.gradle](backend/api/build.gradle) — swap H2 for Postgres + Flyway
- [backend/api/src/main/java/edu/upc/essi/dtim/odin/config/AppConfig.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/config/AppConfig.java)
- [backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaStore/relationalStore/](backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaStore/relationalStore/) — **delete this directory** at the end of the phase
- [backend/api/src/main/java/edu/upc/essi/dtim/odin/projects/pojo/Project.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/projects/pojo/Project.java) — move to `storage/entities/`, strip embedded graph + collection fields
- All `*Service.java` files (8 services use `ORMStoreFactory.getInstance()` today; each gets a constructor-injected repository instead)
- All entity classes under `backend/api/src/main/java/edu/upc/essi/dtim/odin/` — add `tenantId` field
- New: `backend/api/src/main/java/edu/upc/essi/dtim/odin/storage/` — new home for repositories and entities (aligns with the hexagonal layering ground rule)
- New: `backend/api/src/main/resources/db/migration/V*.sql`

### Exit criteria

- [ ] H2 dependency removed; build still passes.
- [ ] `ORMStoreInterface`, `ORMStoreJpaImpl`, `ORMStoreFactory` deleted. `grep -r "ORMStore" backend/` returns nothing.
- [ ] One repository class per aggregate, all in `backend/api/src/main/java/edu/upc/essi/dtim/odin/storage/`.
- [ ] Existing `ProjectControllerTest` and `SourceControllerTest` pass against an ephemeral Postgres (testcontainers).
- [ ] **New tenant isolation test:** create two projects under different tenant IDs, assert that calls with tenant A's context never return tenant B's data on `findById`, `findAll`, or `delete`. This test must fail if any repository method skips the `tenant_id` filter.
- [ ] `make test-golden` still green.
- [ ] Frontend still works against the running stack.
- [ ] Event log shows rows after exercising the UI for create/update/delete operations on Project, Dataset, and IntegratedGraph.
- [ ] No `catch (Exception e) { throw new InternalServerErrorException(...) }` patterns in the storage layer.
- [ ] `Project` entity lives in `storage/entities/` (not in a `pojo/` folder), holds `UUID integratedGraphId` references rather than embedded graph objects, and does not contain `temporal*` workflow fields.
- [ ] `[DECISIONS.md](DECISIONS.md)` records the choice of Option A (persisted `IntegrationDraft`) vs. Option B (in-memory cache) for transient integration state, with rationale.
- [ ] Every `@Entity` has `@Version Long version`. **ArchUnit test asserts this** and fails the build if any entity is missing it.
- [ ] Every timestamp field is `Instant`, not `LocalDateTime`. ArchUnit test asserts no entity field has type `java.time.LocalDateTime`.
- [ ] Every repository read method returns either `Optional<T>` or `Page<T>` — never unbounded `List<T>`. ArchUnit test asserts no `*Repository` method has return type `List<*>`.
- [ ] No `try { ... } finally { x.close(); }` blocks in the new repository code; resources are managed via try-with-resources. Verified by grep at end of phase.

---

## Phase 1 — Decisions made during implementation (2026-05-12)

### String IDs kept
All entity identifiers remain `String` (not migrated to `UUID`). The entities predated this refactor and the IDs carry no production data, so the risk of the change was not worth the benefit. Decision: keep as-is until Phase 3 constructor-wires everything and the cost of migrating IDs drops to near zero.

### IntegrationDraft: Option A (persisted entity)
The transient `temporalIntegratedGraph` field on `Project` (used during the integration workflow) was confirmed to use **Option A** — a persisted `IntegrationDraft` entity with an FK back to `Project`. Rationale: the demo survives a restart without losing in-progress integration state; the in-memory cache (Option B) would lose work on every redeploy, which is frequent during development.

`IntegrationDraft` creation is deferred to Phase 1 entity cleanup (still pending — see checkpoint).

### orm.xml and `ddl-auto=update`
`META-INF/orm.xml` is auto-loaded by the JPA spec; no `persistence.xml` is needed. `ddl-auto=update` is intentional for dev (no production data to protect, schema evolves freely). Flyway manages only the `events` table (a non-JPA table introduced in this phase).

### `GraphStoreFactory` wiring fix
`GraphStoreFactory` previously instantiated `GraphStoreJenaImpl` with `new` (bypassing Spring), meaning the Jena impl was created twice — once by Spring (with DI) and once by the factory (without). Fixed by adding a Spring constructor to `GraphStoreFactory` that captures the Spring-managed `GraphStoreJenaImpl` bean in a static field. The static `getInstance(AppConfig)` signature is preserved for callers.

### `Query` entity not persistable
`NextiaCore.queries.Query` is a plain POJO with no JPA annotations and is absent from `orm.xml`. Both `QueryService.getQueryByID` and `saveQuery` were already TODO-only and have never worked. Replaced their bodies with `UnsupportedOperationException`. The Query flow is tracked as a known gap until Phase 2's query rewriting rework.

### ArchUnit Phase 1 rules deferred
The plan calls for ArchUnit assertions on `@Version` and `Instant` field types. Because entities use XML mapping (`orm.xml`) rather than `@Entity` annotations, standard ArchUnit field-annotation rules do not apply. These assertions need a custom ArchUnit rule that reads orm.xml metadata — deferred to when the entity layer is next touched.

### Phase 1 build status (2026-05-12)
All services migrated from `ORMStoreFactory` to per-aggregate repositories. `ORMStoreInterface`, `ORMStoreJpaImpl`, and `ORMStoreFactory` deleted. `./gradlew :api:build` passes clean. Still pending from the full Phase 1 plan:
- Project entity cleanup: `IntegrationDraft` entity, replace embedded graphs with IDs, replace embedded list fields with repo queries
- Tenant isolation test and paginated `findAll` wired into controllers

---

## Phase 1.5 — Collapse `NextiaCore.graph` to a single concrete class

**Goal:** Replace the 15-file `Graph` / `IntegratedGraph` / `LocalGraph` / `GlobalGraph` / `WorkflowGraph` / `MappingsGraph` hierarchy (with one Jena impl per subtype) with a single concrete `RdfGraph` class + a `Kind` enum + small composition records for subtypes that carry extra state. This is a precondition for Phase 2 — the new persistence layer is much cleaner against `RdfGraph` than against the existing hierarchy.

**Estimated effort:** 1 weekend.

### Why

Today's hierarchy doesn't earn its keep:
- `IntegratedGraph`, `GlobalGraph`, `MappingsGraph` are **empty marker interfaces** extending `Graph`.
- `LocalGraph` redeclares `getGraphicalSchema/setGraphicalSchema` that already exist on `Graph` (copy-paste bug hidden by the depth of the abstraction).
- Each subtype has its own `*JenaImpl` class, several of which do nothing but extend the base impl.
- The hierarchy forces Jackson `@JsonTypeInfo` polymorphism for serialization, which a single concrete class avoids.
- The `Graph` interface leaks Jena's `Model` and `ResIterator` through `getGraph()` and `retrieveSubjects()` anyway, so the abstraction was never a portability layer in practice.

The **real** portability boundary is the persistence layer (`RdfStoreClient`, introduced in Phase 2). Keeping a fake portability layer at the construction level just adds noise.

### Tasks

- Define a single concrete class `RdfGraph` in `backend/Modules/NextiaCore/src/main/java/edu/upc/essi/dtim/NextiaCore/graph/`:
  ```java
  public final class RdfGraph {
      public enum Kind { INTEGRATED, GLOBAL, LOCAL, WORKFLOW, MAPPINGS }

      private final Kind kind;
      private String name;
      private String graphicalSchema;
      private final Model model;   // Jena Model, in-memory; honest about it

      public void addTriple(String s, String p, String o) { ... }
      public void addTripleLiteral(String s, String p, String literal) { ... }
      public void deleteTriple(String s, String p, String o) { ... }
      public List<Map<String,Object>> query(String sparql) { ... }
      public ResIterator retrieveSubjects() { ... }     // keep as-is for now; callers untouched
      public List<String> retrievePredicates() { ... }
      public void write(String file) { ... }
      public Model getModel() { return model; }         // explicit, no longer pretending
      // getters/setters for kind, name, graphicalSchema
  }
  ```
- Define companion records for the two subtypes that carry real extra state:
  ```java
  public record WorkflowContext(RdfGraph graph, Map<String, List<String>> workflow) {}
  public record LocalContext(RdfGraph graph, String localGraphAttribute) {}
  ```
- Mechanical rewrite of all callers across `NextiaCore`, `NextiaDI`, `NextiaMG`, `NextiaJD`, `NextiaBS`, and `backend/api/`:
  - `IntegratedGraph`/`IntegratedGraphJenaImpl` → `RdfGraph` with `kind=INTEGRATED`
  - `GlobalGraph`/`GlobalGraphJenaImpl` → `RdfGraph` with `kind=GLOBAL`
  - `MappingsGraph`/`MappingsGraphJenaImpl` → `RdfGraph` with `kind=MAPPINGS`
  - `WorkflowGraph`/`WorkflowGraphJenaImpl` → `RdfGraph` with `kind=WORKFLOW` (plus `WorkflowContext` where the extra map is needed)
  - `LocalGraph`/`LocalGraphJenaImpl` → `RdfGraph` with `kind=LOCAL` (plus `LocalContext` where the extra attribute is needed)
- Delete `CoreGraphFactory.java`. Replace its usages with direct `new RdfGraph(kind, name)` calls or static factory methods on `RdfGraph` (`RdfGraph.integrated(name)`, `RdfGraph.local(name, schema)`, etc.).
- Delete all 5 subtype interfaces and all 6 `*JenaImpl` classes. Net file count: 15 → 3 (`RdfGraph.java`, `WorkflowContext.java`, `LocalContext.java`), plus the existing `Triple.java` and `URI.java` which stay.
- Replace any Jackson `@JsonTypeInfo` polymorphism on graph types with plain serialization of `RdfGraph` (the `kind` enum field is sufficient discriminator if anything ever needs it).

### Critical files

- [backend/Modules/NextiaCore/src/main/java/edu/upc/essi/dtim/NextiaCore/graph/](backend/Modules/NextiaCore/src/main/java/edu/upc/essi/dtim/NextiaCore/graph/) — entire directory restructured
- All callers across all `Nextia*` modules and `backend/api/` — IDE-driven rename + type substitution handles >90% of changes

### Exit criteria

- [ ] Single class `RdfGraph` is the only graph type in `NextiaCore.graph` (plus the two context records and existing `Triple`/`URI`).
- [ ] All Java modules compile.
- [ ] All existing unit tests pass.
- [ ] `make test-golden` produces byte-identical output to Phase 1 baseline (this refactor is semantically a no-op).
- [ ] `LocalGraph.graphicalSchema` redeclaration bug is gone by construction.
- [ ] `[DECISIONS.md](DECISIONS.md)` updated: why the hierarchy was collapsed, why portability lives at the persistence layer not the construction layer.

---

## Phase 1.7 — Delete `nextiaInterfaces/` wrapper layer, introduce `services/`

**Goal:** Replace the 7 single-impl module wrapper pairs in `backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/` with named services under a new `services/` package. Services call the Nextia* modules directly, persist results via the Phase 1 repositories, and emit event-log entries. The hexagonal-layering ground rule from the start of this plan stops being aspirational and becomes structural.

**Estimated effort:** 2 weekends.

### Why

The wrapper layer is the third instance of the "interface + single impl + thin pass-through" anti-pattern in this codebase, and its specific failure modes are worse than the previous two:

1. **Pure pass-through.** Each `*ModuleImpl` is 22–97 lines, single-method, doing nothing but `new SomeNextiaThing().doX(input)` wrapped in a `try { ... } catch (Exception e) { throw new InternalServerErrorException(...) }`. No state, no orchestration, no decisions.
2. **Not actually decoupling.** Services use `new XModuleImpl()` directly; nothing is injected. The interface buys nothing at runtime — it's not mocked, not swapped, not extended.
3. **Leaks types Phase 1.5 deletes.** `qrModuleImpl` imports `IntegratedGraphJenaImpl`. `mapgenModuleImpl` imports `IntegratedGraph` and `GlobalGraph`. `integrationModuleImpl` imports `CoreGraphFactory`. Every wrapper must be touched in Phase 1.5 anyway, so this work is on the natural cleanup path.
4. **Bugs hidden by indirection.**
   - `integrationModuleImpl.main()` ships ~80 lines of debug scaffolding with hardcoded paths `/Users/anbipa/Desktop/DTIM/Cyclops/Cyclops-Test/...` committed to `main`.
   - `jdModuleImpl` declares `@Autowired private static AppConfig appConfig` — Spring does not autowire static fields. This is `null` at runtime and the fact it's not crashing means the code path is barely exercised.
5. **Catch-all `InternalServerErrorException`.** Same anti-pattern Phase 1 removed from storage. Validation, constraint, and configuration errors all become opaque 500s.
6. **Java naming-convention violations.** `integrationModuleImpl`, `bsModuleImpl`, `jdModuleImpl`, `qrModuleImpl`, `mapgenModuleImpl`, `nextiaGraphyModuleImpl` — class names should be PascalCase.
7. **NextiaGraphy is in the wrong place.** It lives inside the API layer (`nextiaInterfaces/NextiaGraphy/`) rather than in `backend/Modules/` like the other Nextia* libraries, *and* it has a wrapper around itself that lives in the same package.

The wrappers were trying to be services and never grew into one. Phase 1 already established `services/` as the home for orchestration; this phase fills it.

### Tasks

**Absorb wrapper logic into the existing per-feature services (no new `services/` folder)**

The codebase already has per-feature application services (`integration/IntegrationService.java`, `datasets/DatasetService.java`, etc.). Today they delegate to the `*ModuleImpl` wrappers. After this phase they call the Nextia* libraries directly. Each Nextia* module is invoked from exactly one HTTP controller area, so a separate "cross-cutting domain service" layer is not needed — it would just create two services with similar names per feature. Keep things simple: the per-feature service is the orchestrator.

| Today (delete) | Logic absorbed into | Owns |
|---|---|---|
| `bsModuleInterface` + `bsModuleImpl` | `datasets/DatasetService` | Calls `NextiaBS` directly. Persists resulting `Schema` via `DatasetRepository` (+ `SchemaRepository` if introduced). Emits `dataset.bootstrapped` event. |
| `integrationModuleInterface` + `integrationModuleImpl` | `integration/IntegrationService` | Calls `NextiaDI` (`integrate`, `joinIntegration`, `getUnused`) directly. Persists resulting `IntegratedGraph` metadata + uploads triples via `RdfStoreClient` (Phase 2 wires this). Emits `graph.integrated` event. |
| `jdModuleInterface` + `jdModuleImpl` | `integration/IntegrationService` (alignment discovery is initiated from the integration flow) | Calls `NextiaJD.Discovery` directly. Persists resulting `Alignment` list via `AlignmentRepository`. Emits `discovery.completed` event. Receives `DataLake` (Phase 2.5) via constructor instead of fishing out a static singleton. |
| `mapgenModuleInterface` + `mapgenModuleImpl` | `mappings/MappingsService` | Calls `NextiaMG` directly. Persists resulting `Mappings` via `MappingsRepository`. Emits `mappings.generated` event. |
| `qrModuleInterface` + `qrModuleImpl` | `query/QueryService` | Calls `NextiaQR.QueryRewriting` directly. Returns query results to the controller. Emits `query.executed` event with the rewritten SQL in payload (useful for the demo and for debugging). Receives `DataLake` via constructor. |

- Each per-feature service is rewritten in this phase to: (a) be constructor-injected with the modules, repositories, `EventLog`, and `TenantContext` it needs, (b) drop all calls into `nextiaInterfaces/`, (c) remove `@Autowired` field annotations, (d) handle no exception swallowing.
- Where the wrapper was the only orchestration, the service becomes the only orchestration. Where the controller currently does additional work between calling the wrapper and returning a response, that work moves into the service. Controllers shrink to: validate input → call service → translate exceptions to HTTP → return response.

**Fold `NextiaGraphy` into a `GraphVisualizationService`**

Graph visualization is the one piece of orchestration that genuinely crosses feature boundaries — it's invoked when fetching projects, fetching integrated graphs, and previewing integrations. It earns a dedicated home.

- Create `integration/GraphVisualizationService.java` (placed in the `integration/` folder because that's where it's most heavily used; reachable from other features as a constructor-injected dependency). Exposes one method: `String generateVisualGraph(RdfGraph graph)`.
- Move `NextiaGraphy.java`, `graphy/Graphy.java`, `graphy/Links.java`, `graphy/Nodes.java` next to `GraphVisualizationService` (or as a small private helper package under `integration/`). Keep the data classes; they're fine — just relocate.
- Move `vocabulary/Namespaces.java`, `vocabulary/Vocabulary.java`, `vocabulary/DataSourceVocabulary.java`, `vocabulary/DataSourceGraph.java`, `vocabulary/GlobalGraph.java`, `vocabulary/SourceGraph.java` to `backend/Modules/NextiaCore/src/main/java/.../vocabulary/` next to existing vocabulary classes (where `R2RML.java` already lives). These are plain constants; they don't belong in the API layer at all.
- Delete `nextiaGraphyModuleInterface` + `nextiaGraphyModuleImpl`.
- Phase 2's mention of `GraphVisualizationService` then becomes "pre-existing service from 1.7, now invoked from feature services instead of from inside storage" — a smaller change.

**Delete bugs, not just files**
- Delete `integrationModuleImpl.main()` and the hardcoded `/Users/anbipa/Desktop/...` paths. If any of those lines were genuinely useful as a smoke test, port them to a proper test under `tests/integration/` with fixture paths from `tests/fixtures/golden/nextiadi/` (Phase 0).
- Replace `@Autowired private static AppConfig appConfig` (broken) with constructor injection of an `AppConfig` parameter into `integration/IntegrationService` (where the join discovery logic now lives).
- Stop catching `Exception → InternalServerErrorException` everywhere. Let domain exceptions propagate. The existing `exception/GlobalExceptionHandler.java` already exists — clean it up to translate the existing domain exceptions (`ElementNotFoundException`, `EmptyFileException`, `FormatNotAcceptedException`, `IntegrityConstraintViolation`, `CustomIOException`) to appropriate HTTP statuses. Everything else becomes 500 *with logging*, not silent wrapping.
- **Delete `exception/InternalServerErrorException.java`.** Its only purpose was to wrap arbitrary errors in the now-deleted `ORMStoreInterface` and `nextiaInterfaces/` layers. Once both are gone, no caller remains.

**Rewire callers**
- Every controller that today calls a wrapper switches to calling the new service. Mechanical: same method names, just on a differently-named class with constructor injection instead of `new`.
- Any place that was reaching across packages — e.g. `GraphStoreJenaImpl` calling `nextiaGraphyModuleImpl.generateVisualGraph()`, or `integrationModuleImpl` calling `nextiaGraphyModuleImpl` — gets rewired through the service. (Phase 2 will then delete `GraphStoreJenaImpl` entirely; this just stops bleeding.)

**Consolidate API DTOs (the actual transport types)**

The current `pojos/` / `pojo/` / `POJOs/` subdirectories under each controller mix two unrelated things: real request/response shapes (which should be DTOs) and the misclassified `Project` entity (already moved to `storage/entities/` in Phase 1). What's left after Phase 1 is the genuine DTO set. While we're rewiring controllers anyway, consolidate them:

- New package: `backend/api/src/main/java/.../api/dto/` (or `controllers/dto/`, depending on where Phase 3 ends up landing controllers — pick one and stick to it).
- Move and rename:
  - `integration/pojos/IntegrationData.java` → `api/dto/IntegrationRequest.java`
  - `integration/pojos/IntegrationTemporalResponse.java` → `api/dto/IntegrationPreviewResponse.java`
  - `integration/pojos/JoinAlignment.java` → `api/dto/JoinAlignment.java` (kept name; it extends `Alignment` from NextiaCore — that relationship stays)
  - `query/pojos/QueryDataSelection.java` → `api/dto/QueryRequest.java`
  - `query/pojos/QueryResult.java` → `api/dto/QueryResponse.java`
  - `query/pojos/Classes.java` → `api/dto/QueryClassSelection.java` (the current name shadows `java.lang.Class`-related concepts)
  - `query/pojos/Property.java` → `api/dto/QueryPropertySelection.java` (same — current name shadows `java.util.Properties`-related concepts)
  - `workflows/pojos/WorkflowResponse.java` → `api/dto/WorkflowResponse.java`
  - `repositories/POJOs/DataRepositorySchemaInfo.java` → `api/dto/DataRepositorySchemaOption.java` (it's a label/value pair for UI dropdowns)
  - `repositories/POJOs/TableInfo.java` → `api/dto/TableInfoResponse.java`
- Delete the old `pojos/` / `pojo/` / `POJOs/` subdirectories. The naming inconsistency (three case variations) goes away by construction.
- Convert each DTO to a Java record. They are pure data carriers — `record IntegrationRequest(Dataset dsA, Dataset dsB, String integratedName, List<Alignment> alignments) {}` replaces the entire current file. Boilerplate getters/setters disappear.
- `JoinAlignment` is the one exception: it inherits from `Alignment` (NextiaCore) and adds fields. Keep it as a class, but mark `final` and ensure all fields are private with getters only — there's no need for setters on a transport object that's deserialized once and read.
- Update callers to use the new names. Each controller and service is already being touched in this phase; the rename is a few find-and-replace passes.

### Critical files

- [backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/](backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/) — **delete entire directory** at end of phase (the `nextiaDataLayer/` subdirectory is already deleted by Phase 2.5)
- New: `backend/api/src/main/java/.../services/BootstrapService.java`, `IntegrationService.java`, `JoinDiscoveryService.java`, `MappingsGenerationService.java`, `QueryRewritingService.java`, `GraphVisualizationService.java`
- New (relocated): `backend/Modules/NextiaCore/src/main/java/.../vocabulary/Namespaces.java`, `Vocabulary.java`, `DataSourceVocabulary.java`, `DataSourceGraph.java`, `GlobalGraph.java`, `SourceGraph.java` (the constants currently in `nextiaInterfaces/NextiaGraphy/vocabulary/`)
- All controllers under `backend/api/src/main/java/edu/upc/essi/dtim/odin/{integration,projects,datasets,workflows,repositories,mappings,dataProducts,intents,query}/` — switch from wrapper calls to service calls
- [backend/api/src/main/java/edu/upc/essi/dtim/odin/exception/GlobalExceptionHandler.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/exception/GlobalExceptionHandler.java) — clean up the existing handler to translate domain exceptions properly
- [backend/api/src/main/java/edu/upc/essi/dtim/odin/exception/InternalServerErrorException.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/exception/InternalServerErrorException.java) — **delete**

### Exit criteria

- [ ] `nextiaInterfaces/` directory deleted. `grep -r "nextiaInterfaces" backend/` returns nothing.
- [ ] `exception/InternalServerErrorException.java` deleted; the existing `GlobalExceptionHandler` translates the 5 remaining domain exceptions to appropriate HTTP statuses.
- [ ] No new `services/` folder created at the top level. Per-feature services hold the orchestration; only `GraphVisualizationService` lives outside its primary feature (placed in `integration/`).
- [ ] All `pojos/` / `pojo/` / `POJOs/` subdirectories under controller packages deleted. DTOs live in a single `api/dto/` directory.
- [ ] DTOs renamed `*Request` / `*Response` consistently and converted to Java records (except `JoinAlignment`, which inherits from NextiaCore `Alignment` and stays a class).
- [ ] All 5 services exist under `services/`, are PascalCase, are constructor-injected, and contain no `@Autowired` field annotations.
- [ ] `integrationModuleImpl.main()` and its `/Users/anbipa/Desktop/...` paths are gone. `grep -r "anbipa\|Desktop/DTIM" backend/` returns nothing.
- [ ] No `catch (Exception e) { throw new InternalServerErrorException(...) }` remains in any service. Domain exceptions propagate; the new `GlobalExceptionHandler` is the only HTTP-status-translation site.
- [ ] `NextiaGraphy/` is gone from the API layer. Its data classes live under `services/graphvisualization/`; its vocabulary lives in `NextiaCore`.
- [ ] All existing controller tests pass.
- [ ] `make test-golden` still green for every Nextia* module — this phase is structural only, behavior must not change.
- [ ] **Service-layer test:** for each of the 5 services, a unit test with the underlying Nextia module stubbed, asserting (a) the service calls the module with correct arguments, (b) persists the result via the right repository, (c) writes the right `event_type` to the event log. This is the test surface the wrappers never had.
- [ ] `[DECISIONS.md](DECISIONS.md)` updated: why the wrapper layer was deleted rather than refactored, why services own persistence + event-logging (so future phases don't bleed orchestration back into controllers).

---

## Phase 2 — External RDF store (Oxigraph default, Fuseki fallback)

**Goal:** All RDF reads/writes go to an external triple store over SPARQL HTTP. Embedded `apache-jena-libs` runtime usage removed for *persistence* — Jena `Model` continues to be the in-memory construction format inside `RdfGraph`. The new `RdfStoreClient` abstraction has two implementations (Oxigraph and Fuseki) so the choice stays reversible.

**Estimated effort:** 2 weekends.

### Why Oxigraph as default

The Java code uses OWL only as **vocabulary constants** (`OWL.Class.getURI()` returns an IRI string) — there is no `Reasoner`, `InfModel`, or `ReasonerRegistry` usage anywhere in the codebase. That removes the single biggest reason teams stay on Jena/Fuseki. SPARQL features in use (`UNION`, `OPTIONAL`, `FILTER`, `VALUES`) are all standard SPARQL 1.1, fully supported by Oxigraph.

Concrete benefits over Fuseki for ODIN's situation:
- ~20 MB single Rust binary vs. ~250 MB Fuseki + JVM; sub-second startup vs. seconds.
- Idle memory <50 MB vs. 200–400 MB. Matters on a €10/month single-VM deployment (Phase 6).
- `pyoxigraph` embedded mode opens the same RocksDB from Python — a future Python ML service can query the integrated graph **without a network hop**, aligning directly with the "not through API calls" goal that drove this whole plan.
- Same SPARQL 1.1 + Graph Store HTTP Protocol, so the Java client code is identical.

Tradeoffs to acknowledge in `DECISIONS.md`: smaller community, fewer Stack Overflow answers, no built-in text indexing or reasoning. The two-implementation pattern below is the escape hatch.

### Tasks

**Introduce `RdfStoreClient` and the two implementations**
- Add Oxigraph to `docker-compose.yml` as the default RDF store. Keep a commented-out Fuseki service block alongside for easy switching.
- Define an `RdfStoreClient` interface in `backend/api/src/main/java/.../storage/rdf/` (using the `storage/` package introduced in Phase 1):
  ```java
  public interface RdfStoreClient {
      boolean ask(String sparql, String namedGraphUri);
      List<Map<String,Object>> select(String sparql, String namedGraphUri);
      Model construct(String sparql, String namedGraphUri);
      void update(String sparqlUpdate, String namedGraphUri);
      void loadGraph(String namedGraphUri, Model model);   // takes a Jena Model from RdfGraph
      void dropGraph(String namedGraphUri);
  }
  ```
- Implement `OxigraphClient` and `FusekiClient` against this interface. Both serialize Jena `Model` to N-Triples and POST via the standard Graph Store HTTP Protocol — the implementations differ mostly in base URL conventions and a few endpoint quirks. Each is ~100 lines.
- Wire which implementation is active via env var `ODIN_RDF_STORE=oxigraph|fuseki` (default `oxigraph`).
- Named-graph naming: `urn:odin:tenant:{tenantId}:graph:{graphId}`. All queries are tenant-scoped via `GRAPH <tenant-uri> { ... }` query rewriting in `RdfStoreClient`'s implementation. Document the rewriting strategy in `DECISIONS.md`.

**Demolish `nextiaStore/graphStore/` and its layering violations**

The current `GraphStoreInterface` / `GraphStoreJenaImpl` / `GraphStoreFactory` triplet repeats the relationalStore anti-pattern *and* mixes in two layer violations: the storage `getGraph()` method calls into `nextiaGraphyModuleImpl.generateVisualGraph()` (presentation) and `integrationModuleInterface.generateGlobalGraph()` (domain logic). The class-based kind discrimination (`findById(LocalGraphJenaImpl.class, ...)`) also stops compiling once Phase 1.5 collapses the hierarchy. All of this gets cleaned up here.

- Delete `GraphStoreInterface.java`, `GraphStoreJenaImpl.java`, `GraphStoreFactory.java`. The whole `nextiaStore/graphStore/` directory goes away.
- The storage operations they exposed map directly onto `RdfStoreClient`:
  - `saveGraph(Graph)` → `rdfStore.dropGraph(uri); rdfStore.loadGraph(uri, graph.getModel());`
  - `saveGraphFromStringRepresentation(Graph, String)` → parse the Turtle string into a `Model`, then same as above.
  - `deleteGraph(Graph)` → `rdfStore.dropGraph(uri);`
  - `getGraph(name)` → split into separate steps (see below).
  - `changeGraphName(...)` — **dead code, returns `null`. Delete without replacement.** If a real rename use-case ever arrives, implement it as an explicit SPARQL UPDATE through `RdfStoreClient.update(...)`.
- Replace the god-method `getGraph(name)` by extracting its three concerns into the right layers:
  - **Storage** (the only part that belongs here): `RdfStoreClient.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }", namedGraphUri)` returns the `Model`. Wrap in an `RdfGraph` with the `kind` already known (it's stored on the metadata row in Postgres alongside `name`, after Phase 1.5 — no class-based `findById` needed).
  - **Presentation** (visual schema): move `nextiaGraphyModuleImpl.generateVisualGraph()` to a new `GraphVisualizationService` in `backend/api/src/main/java/.../services/`. Controllers call this service explicitly when they need the `graphicalSchema` for the UI; the storage layer does not.
  - **Domain** (global graph derivation for integrated graphs): move the `integrationModuleInterface.generateGlobalGraph()` call to a new `GlobalGraphService` (or fold into the existing integration service). Same rule — explicit caller, not implicit storage side effect.
- Repository sketch for the metadata row that now drives kind discrimination (created in Phase 1, kind field added here):
  ```java
  // In Phase 1's GraphRepository (or a new one if not yet created)
  Optional<RdfGraph.Kind> findKindByName(String namedGraphUri);
  ```

**Migration and cleanup**
- Migration of existing data: write a one-shot Gradle task that reads any embedded TDB on disk and uploads named graphs to the configured store via `RdfStoreClient.loadGraph`. Run once per environment.
- Drop `apache-jena-libs` runtime persistence usage (`TDBFactory`, `TDB2Factory`, `Dataset`, `RDFConnection` to embedded). Keep `org.apache.jena:jena-core` and `jena-arq` for in-memory `Model` and `jena-querybuilder` for SPARQL string construction — they're libraries, not stores.

### Critical files

- [backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaStore/graphStore/](backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaStore/graphStore/) — **delete entire directory** at end of phase
- New: `backend/api/src/main/java/.../storage/rdf/RdfStoreClient.java`, `OxigraphClient.java`, `FusekiClient.java`
- Updated: `backend/api/src/main/java/.../integration/GraphVisualizationService.java` (created in Phase 1.7; this phase now invokes it from feature services rather than from inside storage)
- Updated: `integration/IntegrationService` absorbs the global-graph derivation that today lives as a side effect inside `GraphStoreJenaImpl.getGraph()`
- All current callers of `GraphStoreFactory.getInstance()` and `GraphStoreInterface` — rewire to `RdfStoreClient` + the new services
- [docker-compose.yml](docker-compose.yml) — add Oxigraph service, comment-block Fuseki alternative
- [backend/api/build.gradle](backend/api/build.gradle) — drop embedded-store dependencies, keep in-memory Jena libs

### Exit criteria

- [ ] Embedded TDB usage gone from runtime persistence code paths (verify by grepping for `TDBFactory`, `TDB2Factory`, `DatasetFactory.create*`).
- [ ] `RdfStoreClient` is the only path to the persistent triple store. Both `OxigraphClient` and `FusekiClient` compile and pass a shared contract test (parametrized integration test that runs the same scenarios against each).
- [ ] `GraphStoreInterface`, `GraphStoreJenaImpl`, `GraphStoreFactory` deleted. `grep -r "GraphStore\(Interface\|Factory\|JenaImpl\)" backend/` returns nothing.
- [ ] No call to graph visualization or NextiaDI integration from anywhere under `storage/`. ArchUnit asserts: classes under `storage/` may not import `integration/` or any feature service.
- [ ] All controller integration tests pass against ephemeral Oxigraph (testcontainers).
- [ ] `make test-golden` still green for NextiaDI/MG fixtures (their RDF output is byte-equivalent to baseline).
- [ ] Cross-tenant isolation test: tenant A SPARQL query against tenant B's graph returns empty.
- [ ] Flipping `ODIN_RDF_STORE=fuseki` and re-running the same integration test suite passes.
- [ ] `[DECISIONS.md](DECISIONS.md)` updated: Oxigraph-as-default rationale, two-impl reversibility pattern, tenant scoping strategy, and the layering split that pulled visualization and global-graph derivation out of storage.

---

## Phase 2.5 — DataLake refactor (drop Spark, collapse abstraction, add tenant isolation)

**Goal:** Keep the *idea* of `NextiaDataLayer` — a single SQL access point so modules don't care whether data lives in Parquet, JDBC, JSON, or an API — and replace the *implementation*. Drop Spark entirely, collapse the 5-layer indirection to a single `DataLake` class, separate zones from storage, and add tenant-schema isolation.

**Estimated effort:** 2 weekends.

### Why

The current `NextiaDataLayer` is the third instance of the same anti-pattern (after `relationalStore` and `graphStore`): interface + abstract base + multiple impls + factory + singleton + duplicate wrapper in the API layer. The call stack to "run a SQL query" is:

```
Service
  → DataLayerImpl                      (in nextiaInterfaces/nextiaDataLayer/)
    → DataLayerSingleton.getInstance()
      → DataLayer (abstract)
        → DLDuckDB / DLSpark
          → JDBC
```

Plus three substantive problems beyond the abstraction smell:

1. **Spark is dead weight.** `DataLayer`'s constructor unconditionally creates a `SparkSession` and `JavaSparkContext` even when the active impl is DuckDB. `setMaster("local")` confirms Spark is running single-node — exactly DuckDB's sweet spot. Spark adds ~500 MB of dependencies, seconds of cold-start time, Scala 2.13 baggage, and pinned versions from 2023. DuckDB natively reads CSV/JSON/Parquet and via extensions reads JDBC sources, so it can replace every line of `DLSpark` and `generateBootstrappedDF`.
2. **Zones entangled with storage.** `DataLayer` mixes "what medallion zone" (landing/formatted/exploitation, plus `Temporal*` siblings) with "which underlying tech." Zones are a workflow concept; storage is infrastructure. They belong in different layers.
3. **No tenant isolation.** Tables are named `for_<uuid>`, `exp_<uuid>`, `tmp_exp_<uuid>` with no tenant prefix. After Phase 1's `tenant_id` discipline this is a hole. Plus the table-name SQL is built by string concatenation (`"CREATE TABLE for_" + tableName + " AS SELECT * FROM read_parquet('" + path + "')"`), which is exploitable if any value flows from user input — and `dataset.getDatasetName()` does flow into a `createOrReplaceTempView` call.

The abstraction's *purpose* — modules write SQL, don't know the storage tech — is correct and worth keeping. NextiaQR especially benefits: it produces SQL queries against the integrated graph and must not couple to Parquet vs JDBC. This phase keeps that property and removes everything else.

### Tasks

**Drop Spark, replace with DuckDB-native readers**
- Remove `org.apache.spark:spark-sql_2.13` and `io.delta:delta-core_2.13` from `backend/Modules/NextiaDataLayer/build.gradle` and from any other module's build that pulled them transitively.
- Replace `generateBootstrappedDF`'s Spark code path with DuckDB calls:
  - CSV → `read_csv_auto('<path>', header=true)`
  - JSON → `read_json_auto('<path>', format='auto')`
  - Parquet → `read_parquet('<path>')`
  - JDBC sources → DuckDB's `postgres_scanner` / `mysql_scanner` extensions (loaded once at `DataLake` startup).
- The "wrapper" pattern (each `Dataset` carries a SQL `wrapper` query that gets registered as a temp view) stays — DuckDB has the same concept (`CREATE TEMP VIEW <datasetName> AS <wrapper>`).

**Collapse to a single `DataLake` class**
- Create `backend/Modules/NextiaDataLayer/src/main/java/.../DataLake.java`:
  ```java
  public final class DataLake implements AutoCloseable {
      private final Connection conn;     // DuckDB JDBC

      public DataLake(Path storeDir) { ... }

      public Iterator<Row> query(String sql, List<Dataset> datasets);
      public RecordBatchReader queryArrow(String sql, List<Dataset> datasets);   // for Phase 4 Arrow Flight
      public void registerDataset(Dataset d, String tenantSchema);
      public void unregister(String tenantSchema, String tableName);
      public Path materialize(String tenantSchema, String tableName, String format);
      @Override public void close() { ... }
  }
  ```
- Delete `DataLayer.java` (abstract), `DLDuckDB.java`, `DLSpark.java`, `DataLayerFactory.java`. Delete the API-layer wrapper triplet `nextiaInterfaces/nextiaDataLayer/DataLayerInterface.java`, `DataLayerImpl.java`, `DataLayerSingleton.java`.
- Construct `DataLake` once in the API's `main()` (Phase 3 will rewire this in plain Java; for now it's a Spring bean).

**Separate zones from storage**
- Create `DataLakeZoneService` in `backend/api/src/main/java/.../services/`:
  ```java
  public class DataLakeZoneService {
      enum Zone { LANDING, FORMATTED, EXPLOITATION, TMP_LANDING, TMP_FORMATTED, TMP_EXPLOITATION }

      public void promote(Dataset d, Zone from, Zone to);
      public void uploadToLanding(Dataset d);            // owns the Parquet write
      public void uploadToFormatted(Dataset d);          // calls DataLake.registerDataset
      public void uploadToExploitation(String sql, UUID uuid);
      public void persistFromTemporal(UUID uuid);
      public void remove(Zone zone, String name);
  }
  ```
- The medallion semantics live here, not in `DataLake`. NextiaQR and NextiaJD call `DataLake.query` directly with the exploitation-zone schema; they don't see zones at all.

**Tenant isolation via DuckDB schemas**
- On first request for a tenant, `DataLake.ensureTenantSchema(tenantId)` runs `CREATE SCHEMA IF NOT EXISTS tenant_<id>`.
- All zone-prefixed tables become `tenant_<id>.<zone>_<uuid>` (or move zone into the table name, schema groups by tenant).
- Per-connection or per-statement: `SET search_path = tenant_<id>` so SQL written by NextiaQR/NextiaJD doesn't have to know the tenant. The `TenantContext` from Phase 1 supplies the value.
- Cross-tenant query attempts become impossible by construction — no schema, no tables visible.

**Replace string-dispatch and unsafe SQL**
- `getDataCollector(repo)` currently does `if (repo.getRepositoryType().equals("ApiRepository")) ... else if ("RelationalJDBCRepository") ...`. Replace with a `Map<String, Supplier<DataCollector>>` registry built once at startup. Adding a new collector becomes one map entry, not an `else if` branch.
- Every SQL string built by concatenation gets audited. Identifier interpolation (table names, schema names) uses an allowlist regex (`^[a-zA-Z0-9_]+$`) before insertion; value interpolation uses prepared statements. Document the rule in `DECISIONS.md`.
- Replace `assert df != null` with explicit null-check + `IllegalStateException`.

**Absorb and delete `utils/Utils.java`**

The current `backend/api/src/main/java/edu/upc/essi/dtim/odin/utils/Utils.java` has three methods, each with a real home elsewhere:

- `generateUUID()` — misleadingly named: it produces a 16-character random alphanumeric string with a `"UUID_"` prefix, not a real UUID. Java has `java.util.UUID.randomUUID()` built in. **Delete this method** and rewrite all callers to use `UUID.randomUUID().toString()`. Phase 1 already adopts `UUID` for tenant IDs and entity IDs; this aligns the rest of the codebase to the same identifier type.
- `reformatName(String)` and `toCamelCase(String)` — sanitize table names for SQL. They belong with this phase's identifier-allowlist sanitization. Move both into a small `SqlIdentifiers` helper in the same package as `DataLake` (e.g. `backend/Modules/NextiaDataLayer/src/main/java/.../SqlIdentifiers.java`), or inline into `DataLake` if usage stays local. Either way, the methods become package-private and pair with the allowlist regex check before insertion.
- After moving the contents, the `utils/` folder is empty. **Delete `backend/api/src/main/java/edu/upc/essi/dtim/odin/utils/`.**

**Result API**
- `DataLake.query` returns `Iterator<Row>` for Java callers (a small `Row` record wrapping column-name → value).
- `DataLake.queryArrow` returns DuckDB's native Arrow `RecordBatchReader` for cross-language consumers. Phase 4 will use this directly when wiring Arrow Flight for Java↔Python data-plane calls — no double conversion.
- Drop the JDBC `ResultSet` return type. It was the leaky abstraction that forced `DLSpark` to fake a `ResultSet`; with DuckDB-only and an Arrow-aware API, the fake goes away.

### Critical files

- [backend/Modules/NextiaDataLayer/build.gradle](backend/Modules/NextiaDataLayer/build.gradle) — drop Spark + Delta, keep DuckDB
- [backend/Modules/NextiaDataLayer/src/main/java/edu/upc/essi/dtim/NextiaDataLayer/](backend/Modules/NextiaDataLayer/src/main/java/edu/upc/essi/dtim/NextiaDataLayer/) — restructure: delete `dataLayer/DataLayer.java`, `DLDuckDB.java`, `DLSpark.java`, `utils/DataLayerFactory.java`; add `DataLake.java`
- [backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/nextiaDataLayer/](backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/nextiaDataLayer/) — **delete entire directory** (the `Interface`/`Impl`/`Singleton` triplet)
- New: `backend/api/src/main/java/.../services/DataLakeZoneService.java`
- All current `DataLayer` callers — rewire to `DataLake` (storage) or `DataLakeZoneService` (workflow):
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/projects/ProjectService.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/projects/ProjectService.java)
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/datasets/DatasetService.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/datasets/DatasetService.java)
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/repositories/RepositoryService.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/repositories/RepositoryService.java)
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/mappings/MappingsService.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/mappings/MappingsService.java)
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/dataProducts/DataProductService.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/dataProducts/DataProductService.java)
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/nextiaQR/qrModuleImpl.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/nextiaQR/qrModuleImpl.java)
  - [backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/nextiaJD/jdModuleImpl.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/nextiaInterfaces/nextiaJD/jdModuleImpl.java)
  - [backend/Modules/NextiaQR/src/main/java/edu/upc/essi/dtim/NextiaQR/Main.java](backend/Modules/NextiaQR/src/main/java/edu/upc/essi/dtim/NextiaQR/Main.java) and [QueryRewriting.java](backend/Modules/NextiaQR/src/main/java/edu/upc/essi/dtim/NextiaQR/rewriting/QueryRewriting.java)
  - [backend/Modules/NextiaJD/src/main/java/edu/upc/essi/dtim/NextiaJD/discovery/Discovery.java](backend/Modules/NextiaJD/src/main/java/edu/upc/essi/dtim/NextiaJD/discovery/Discovery.java)

### Exit criteria

- [ ] Spark dependencies gone from every Gradle module. `grep -r "spark-sql\|spark-core\|delta-core" backend/` returns nothing in build files.
- [ ] `DataLayer` (abstract), `DLDuckDB`, `DLSpark`, `DataLayerFactory` deleted. `DataLayerInterface`, `DataLayerImpl`, `DataLayerSingleton` deleted.
- [ ] One concrete class `DataLake` is the only entry point to the SQL store. Zone semantics live in `DataLakeZoneService` only.
- [ ] **Tenant isolation test for the data lake:** create datasets under two tenants, assert that tenant A's `DataLake.query` cannot see tenant B's tables (DuckDB returns "table not found" or empty).
- [ ] **SQL-injection audit:** no string concatenation builds SQL with values that originate outside the application. All identifier interpolation passes through the allowlist validator. Document the audit in `DECISIONS.md`.
- [ ] `utils/Utils.java` and the `utils/` folder deleted. `generateUUID()` callers use `java.util.UUID.randomUUID().toString()`; `reformatName`/`toCamelCase` live next to `DataLake` with the allowlist validation.
- [ ] All NextiaQR and NextiaJD golden fixtures pass — these modules are the heaviest `DataLayer` users; their behavior is the correctness contract.
- [ ] `make test-golden` runs measurably faster (drop Spark startup tax). Baseline and new timing recorded in `DECISIONS.md`.
- [ ] Application cold-start time measured before and after; the drop is the headline metric for this phase.
- [ ] `[DECISIONS.md](DECISIONS.md)` updated: Spark removal rationale, DuckDB-only commitment, what this closes off (cluster-scale processing — never realistic on a research/demo system but worth being explicit), schema-per-tenant choice, identifier-allowlist rule.

---

## Phase 3 — Strip Spring Boot from the API layer

**Goal:** Replace Spring Boot + Spring Web with Javalin (or Helidon SE). Spring's only earned its keep here as a DI container and HTTP server; both are replaceable cheaply.

**Estimated effort:** 2–3 weekends. **This is the biggest phase. Consider stopping here if Spring removal turns out painful — Phases 1+2 already deliver most of the cleanup value.**

### Why Javalin specifically

- Single-file routing, no annotations, ~1 MB jar
- Native Jetty under the hood (already what Spring Boot uses)
- Trivial to wire request filters for `TenantContext`
- Plays well with manual constructor injection — which you'll do because it forces clean service boundaries

If Javalin feels too bare, Helidon SE is the next step up. Avoid Quarkus and Micronaut — same annotation density problem as Spring.

### Tasks

**Spring Boot removal**
- Replace `OdinApplication` Spring Boot main with a plain `main()` that wires services manually and starts Javalin on the same port the frontend expects.
- Convert each `*Controller` from `@RestController` to a Javalin route group. Map HTTP verbs explicitly. Use Jackson (already present) for JSON.
- Replace `@Autowired` with constructor parameters. Build the dependency graph once in `main()`. This will reveal services with hidden cyclic dependencies — fix them as you find them, this is a feature.
- Replace `application.properties` with a small `Config` record loaded from env vars + a YAML file. Drop `spring-boot-configuration-processor`.
- Replace `RestTemplate` (used today to call Python Flask apps) with Java's built-in `HttpClient`. Phase 4 replaces this entirely with gRPC, but unblock Phase 3 with the JDK client.
- Tests: existing `*ControllerTest` classes use `@SpringBootTest` — port them to Javalin's test utilities or to plain HTTP integration tests against a started instance.
- Hibernate stays. JPA-via-Hibernate without Spring works fine; bootstrap an `EntityManagerFactory` manually in `main()`. (Replacing Hibernate with JOOQ is a tempting follow-up but **out of scope for this plan**.)

**Request correlation IDs**
- Add a Javalin `before` handler that:
  - Reads `X-Request-Id` from the incoming request, or generates `UUID.randomUUID().toString()` if absent.
  - Puts it on SLF4J's MDC (`MDC.put("requestId", id); MDC.put("tenantId", tenant.id().toString());`) for the duration of the request.
  - Echoes the ID back as a response header so the frontend can include it in bug reports.
- Propagate the same ID outbound: when calling Python services (still via JDK `HttpClient` in this phase, gRPC in Phase 4), set it as a request header. In Phase 4 it becomes gRPC metadata.
- Without this, debugging a slow or failed flow across frontend → Java → Python becomes guesswork once Phase 4 lands.

**Structured JSON logging**
- Add `net.logstash.logback:logstash-logback-encoder` to `build.gradle` and configure `logback.xml` to emit JSON to stdout in dev and prod (text format optional behind a profile).
- Every log line carries the MDC fields (`requestId`, `tenantId`, plus any phase-specific keys). Search by request ID across services becomes trivial.
- One pass over the codebase to scrub passwords, API keys, and full SQL parameter values out of log statements. Document the rule in `DECISIONS.md`: "no secrets in logs, no full row payloads in logs."

**OpenAPI generation for the Javalin routes**
- Add `io.javalin:javalin-openapi` (Javalin's OpenAPI plugin). Annotate each route's request/response types with the small set of OpenAPI annotations the plugin requires.
- `GET /openapi.json` and `GET /swagger-ui` are served by Javalin out of the box. The frontend now has machine-readable docs.
- Add a Gradle task that runs `openapi-generator-cli` to generate a TypeScript Axios client from the spec into `frontend/src/api/generated/`. Run it as part of `./gradlew build`. The frontend gradually migrates to typed calls during Phase 9 of the rewrite-style frontend integration (already implicitly covered when the frontend is repointed at the new backend).
- The forcing function of "every endpoint must have a typed request and typed response in the spec" reveals any remaining hand-rolled JSON shapes. Those become DTOs in `api/dto/` (Phase 1.7's package) if they aren't already.

**Health and readiness endpoints**
- `GET /health/live` returns 200 if the JVM is up. For Caddy / Docker healthchecks.
- `GET /health/ready` returns 200 only if Postgres + Oxigraph + DataLake are reachable. Drives readiness probes and gives Phase 6's deployment something to monitor.

### Critical files

- [backend/api/src/main/java/edu/upc/essi/dtim/odin/OdinApplication.java](backend/api/src/main/java/edu/upc/essi/dtim/odin/OdinApplication.java)
- All `*Controller.java` under `backend/api/src/main/java/edu/upc/essi/dtim/odin/`
- [backend/api/build.gradle](backend/api/build.gradle) — drop Spring starters, add Javalin
- All `*ControllerTest.java` files

### Exit criteria

- [ ] `spring-boot-starter*` and `spring-boot-test` removed from build.gradle.
- [ ] Server starts in <2 seconds (was likely >10 with Spring Boot 2.5).
- [ ] All controller tests pass.
- [ ] Frontend works end-to-end (the contract is the HTTP API, which is unchanged).
- [ ] `make test-golden` still green.
- [ ] Every request log line includes `requestId` and `tenantId` MDC fields. `X-Request-Id` echoed in responses.
- [ ] Logs are JSON. `cat logs/odin.log | jq '.'` works.
- [ ] `GET /openapi.json` returns a valid spec covering every route. TypeScript client is generated into `frontend/src/api/generated/` as part of `./gradlew build`.
- [ ] `GET /health/live` and `GET /health/ready` return correct status under both healthy and degraded conditions (e.g., Postgres down → ready returns 503).
- [ ] ArchUnit assertion: no class outside `api/` imports `io.javalin.http.*`. Controllers are the only HTTP-aware code.
- [ ] No `@Autowired` annotations remain anywhere. ArchUnit asserts.

---

## Phase 4 — Java↔Python IPC via gRPC + Arrow Flight

**Goal:** Replace the existing Flask-over-REST integration with a typed, schema-versioned, data-efficient contract. The new contract is what every future Python module plugs into.

**Estimated effort:** 2–3 weekends.

### Why this shape

- **gRPC for control-plane calls** (small request/response with structure: "predict intent for this string", "generate workflow for this spec"). Schema-typed via `.proto`, generated stubs in both languages, mature error model.
- **Arrow Flight for data-plane calls** (large columnar payloads: profiling a dataset, computing embeddings over rows, schema extraction on big files). Zero-copy columnar transport — *faster than calling pandas from a Python monolith would be*, because Arrow is the in-memory format pandas/polars use anyway.
- Both run over HTTP/2 on the same port if you want; but easier to keep them as two endpoints.

You don't have to use Arrow Flight in this phase if no current call is data-heavy — gRPC alone is enough. Add Flight when the first heavy call arrives.

### Tasks

- Create `backend/proto/` with `.proto` files for the existing Python module surfaces:
  - `intent_prediction.proto` (mirrors current `/predictIntent` Flask route)
  - `graphdb.proto` (mirrors `api_graphdb_interaction.py` routes)
  - `workflow_generation.proto` (for `IntentSpecification2WorkflowGenerator`)
- Add a `proto` Gradle subproject that generates Java stubs.
- Add a Python `odin_proto/` package with generated stubs (use `grpcio-tools`).
- Rewrite `IntentAnticipation/llm/api_llm_interaction.py` and `read-write-graphdb/api_graphdb_interaction.py` as gRPC servers (drop Flask).
- Rewrite Java callers (search for `RestTemplate` usages from Phase 3) to use the gRPC stubs.
- Rewrite `start_apis.py` to start gRPC servers instead of Flask.
- Tenant context: pass `tenant_id` as a gRPC metadata header on every call. Python servers log it and (eventually) enforce it.
- Document the protocol versioning policy in `DECISIONS.md`: how `.proto` files are evolved, breaking-change rules, who owns each service.

### Critical files

- New: `backend/proto/`, `backend/proto/build.gradle`
- [backend/Modules/IntentAnticipation/start_apis.py](backend/Modules/IntentAnticipation/start_apis.py)
- [backend/Modules/IntentAnticipation/llm/api_llm_interaction.py](backend/Modules/IntentAnticipation/llm/api_llm_interaction.py)
- [backend/Modules/IntentAnticipation/read-write-graphdb/api_graphdb_interaction.py](backend/Modules/IntentAnticipation/read-write-graphdb/api_graphdb_interaction.py)
- Java caller files: identified during Phase 3 when `RestTemplate` was migrated to JDK `HttpClient`

### Exit criteria

- [ ] No more Flask in the running stack. `grep -r "from flask" backend/Modules` returns nothing.
- [ ] No more `HttpClient` calls from Java to Python. All Python interactions go through generated gRPC stubs.
- [ ] gRPC reflection enabled in dev so you can `grpcurl` test calls.
- [ ] Existing UI features that depend on Python modules (intent prediction, workflow generation) work end-to-end.

---

## Phase 5 — Plug in one new Python ML/AI component

**Goal:** Prove the IPC contract by building one new feature where it matters: a Python service called from Java with non-trivial data.

**Estimated effort:** 2 weekends.

### Pick one of these (you choose, based on research priorities)

- **LLM-assisted alignment review.** Java NextiaJD produces candidate alignments; a Python service backed by an LLM annotates each with a natural-language rationale and a refined confidence score. Returns to Java, persisted alongside the alignment.
- **Embedding-based join discovery.** Python service computes column embeddings (sentence-transformers or similar) for each dataset; Java NextiaJD calls it via Arrow Flight to get embeddings as a record batch, then uses cosine similarity as an additional signal. Bonus path: the Python service can read the integrated graph via embedded `pyoxigraph` opening the same RocksDB read-only — demonstrates the "not through API calls" integration story.
- **Schema-from-LLM extraction.** Given a CSV preview, Python LLM service returns a guessed schema. Java NextiaBS uses it as a hint when its deterministic extractor is uncertain.

Whichever you pick: it's a *new* feature, not a port. Its only purpose is to validate the contract for funder demos and future work.

### Tasks

- Add new `.proto` for the chosen service.
- New Python module under `backend/Modules/` with a gRPC server.
- Wire one Java service to call it. Make the call optional (feature-flagged) so the existing flow still works if the Python service is down.
- Add an integration test that mocks the gRPC server and asserts the Java side handles both success and unavailable cases.

### Exit criteria

- [ ] One new ML/AI feature reachable through the existing UI.
- [ ] Demo script: `make demo` brings the stack up, runs through extraction → discovery → integration, and the new ML feature is visible in the UI output.

---

## Phase 6 — Deployment and demo polish

**Goal:** Single-VM deployment of the whole hybrid stack, reachable by URL.

**Estimated effort:** 1 weekend.

### Tasks

**Deployment**
- Single VM (DigitalOcean / Hetzner / similar, €10–20/month).
- Caddy as reverse proxy + automatic HTTPS.
- `docker-compose.yml` with Postgres, Oxigraph, ODIN Java backend, Python gRPC services, Quasar static build served by Caddy.
- Nightly Postgres dumps + Oxigraph backups (RocksDB snapshot) shipped to object storage (S3-compatible — Backblaze B2 or DigitalOcean Spaces are cheap).
- `systemctl` unit that runs `docker compose up -d` on boot.
- No Kubernetes. No Terraform. No Helm.

**Auth fence (the stub is unsafe on the public internet)**

The plan uses `X-Tenant-Id` and `X-User-Id` headers as the auth stub throughout. That is fine on `localhost` but **dangerous on a publicly reachable URL** — anyone who hits the URL can pick any tenant ID. Real auth (Keycloak / OIDC) is correctly out of scope for the seed, but a coarse fence is required before any link is shared:

- Configure Caddy with HTTP basic auth on every route except `/health/live`. One shared username/password for the demo audience. Rotate per pitch.
- Set the Caddy `basic_auth` user in env vars, not in the committed Caddyfile.
- The basic-auth realm string is "ODIN demo — not for production data."

**`DECISIONS.md` disclaimer (must be written, must be referenced)**

Add a top-of-file `SECURITY.md` (linked from `README.md` and `DECISIONS.md`) stating in plain language:

> ODIN is currently in a research-demo configuration. Authentication is stubbed — the only access control is HTTP basic auth at the reverse proxy, intended to keep casual visitors out, not to protect data. **Do not upload any data you would not be comfortable handing to anyone with the demo password.** Real authentication and authorization land in the funded production phase, not in this seed.

This is the kind of disclaimer that matters more than it looks. It sets expectations before someone (you, Marc, an enthusiastic collaborator) accidentally onboards a real user against a stubbed system.

### Exit criteria

- [ ] `https://odin-demo.yourdomain.com` serves the full UI behind HTTPS via Caddy.
- [ ] The full demo flow (extraction → discovery → integration → ML feature) works through the public URL.
- [ ] Caddy basic auth blocks unauthenticated requests; only `/health/live` is open.
- [ ] `SECURITY.md` exists, is linked from `README.md`, and contains the stubbed-auth disclaimer.
- [ ] Nightly backup of Postgres and the Oxigraph RocksDB directory lands in object storage. Verified by manually restoring once.
- [ ] You can share the URL with Marc and click through the full pipeline on real infrastructure.

---

## Out of scope (deliberately)

- Replacing Hibernate with JOOQ. Possible later; not necessary for the seed.
- Real auth (Keycloak/OIDC). Stub stays as `X-Tenant-Id` header.
- Multi-tenant provisioning UI.
- Rewriting the Quasar frontend.
- Porting any existing Java module to Python. The whole point of this plan is that you don't.
- Plugin runtime formalization beyond gRPC + Arrow Flight (those *are* the plugin runtime).

These belong in a funded phase, not the seed.

---

## Running estimate

Realistically, **4–6 months of weekend work** to reach Phase 6, vs. 8–12 for the rewrite. Phase 1 (2 weekends) folds in the per-aggregate repository replacement; Phase 1.5 (1 weekend) collapses the graph hierarchy; Phase 1.7 (2 weekends) deletes the wrapper layer and creates the `services/` package; Phase 2.5 (2 weekends) drops Spark and consolidates the DataLake. Together these structural phases (~7 weekends) front-load all the cleanup, so Phase 3 (Spring removal) walks into a codebase that is already hexagonally layered with no factory/singleton/wrapper-layer confusion to untangle. Demo-worthy moment is end of Phase 4 (~month 4): clean Java backend, no Spring, no embedded stores, no Spark, no wrapper layer, gRPC into Python. Phase 5 adds the new ML feature for the funder pitch. Phase 6 deploys.

Critical: the existing UI keeps working at the end of every phase. You can stop after Phase 2 (smaller cleanup) or Phase 3 (Spring gone) and still have shipped real improvements. The rewrite has no comparable mid-flight off-ramps — that asymmetry is the main reason this plan is cheaper.

---

## Verification (across phases)

End-to-end test of any phase:

1. `docker compose up` — full stack starts.
2. `./gradlew test` — all unit + integration tests pass (testcontainers Postgres + Fuseki).
3. `make test-golden` — every captured fixture still produces byte-equivalent output.
4. Manual: open the Quasar UI, create a project, upload a CSV from `tests/fixtures/golden/nextiabs/`, run extraction → discovery → integration. Confirm result matches what the legacy stack produced for the same input.
5. From Phase 4 on: `grpcurl -plaintext localhost:<port> list` shows registered services; sample call returns expected structure.
6. From Phase 5 on: the new ML feature appears in the UI output for a known input.

If any of these fails after a phase, the phase is not done.
