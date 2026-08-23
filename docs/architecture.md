# Architecture

Mission Control is a **modular monolith**: one deployable unit, one database, one build — but
internally partitioned into modules with boundaries that are meant to be as real as if they were
separate services.

The goal is not to eventually split into microservices. It is to keep the option open while
paying none of the distributed-systems cost up front, and to stop the codebase from quietly
becoming a big ball of mud in the meantime.

## What a module is

A module is a **direct sub-package of `com.missioncontrol`**. That is Spring Modulith's rule, and
the whole model follows from it.

```
com.missioncontrol
├── MissionControlApplication      <- root package: visible to everything
├── platform                       <- OPEN module (infrastructure)
├── shared                         <- OPEN module (shared kernel, currently empty)
├── mission                        <- a future domain module
├── crew                           <- a future domain module
└── assignment                     <- a future domain module
```

Within a domain module:

```
com.missioncontrol.mission
├── api/           <- the published interface. Other modules may import ONLY this.
│   ├── MissionService.java        (interface)
│   ├── MissionSummary.java        (DTO / read model)
│   └── MissionPlannedEvent.java   (domain event)
└── internal/      <- everything else. Off-limits to other modules.
    ├── MissionEntity.java
    ├── MissionRepository.java
    ├── MissionServiceImpl.java
    └── MissionController.java
```

Rules of thumb:

- **JPA entities never leave a module.** They are `internal`. Expose a DTO or read model from
  `api` instead. An entity that escapes drags a transaction boundary and lazy-loading behaviour
  with it into code that has no idea it is holding one.
- **Controllers are internal.** HTTP is a delivery detail of the module that owns the data.
- **`api` should be small.** If everything is in `api`, there is no boundary.

### The two infrastructure modules

Both are declared `@ApplicationModule(type = OPEN)`, meaning any module may depend on them
without an explicit allow-list entry.

- **`platform`** — cross-cutting infrastructure: security, CORS, OpenAPI metadata, error
  handling, and the `/api/system/info` diagnostic endpoint. Keep it free of domain concepts. If
  something here starts to know what a Mission is, it belongs in a domain module.
- **`shared`** — the shared kernel, currently and deliberately empty. Add a type here only once
  a *second* module genuinely needs it. Premature sharing is exactly how module boundaries
  dissolve.

## How modules communicate

In order of preference:

1. **Don't.** The best module dependency is the one that doesn't exist. Check whether the data
   really belongs to the other module first.
2. **Call a published interface.** Inject the other module's `api` interface. Simple, synchronous,
   type-safe, easy to follow. Correct for a query ("does this crew member exist?").
3. **Publish a domain event.** For a side effect that the originating module should not care
   about, publish a Spring application event from `api` and let interested modules listen.
   `AssignmentCreated` should not require the assignment module to know that a notification
   module exists.

Option 3 currently uses plain in-process Spring events, which are **not transactional** — a
listener that fails does not roll back the publisher, and events do not survive a restart. When
that guarantee starts to matter, add:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-jpa</artifactId>
</dependency>
```

That enables the Event Publication Registry, which persists events and completes them only once
their listeners succeed. It creates an `event_publication` table, so it needs a Liquibase
changelog — Spring Modulith ships the DDL for it. It is left out today precisely because nothing
publishes events yet.

## Database schema ownership

Each module owns its tables and its migrations.

```
backend/src/main/resources/db/changelog/
├── db.changelog-master.yaml     <- includeAll over modules/
└── modules/
    ├── mission/
    │   └── 001-create-mission.yaml
    └── crew/
        └── 001-create-crew-member.yaml
```

The master changelog uses `includeAll`, so **adding a migration never requires editing a shared
file** — no merge conflicts on the master changelog, and it stays obvious which module owns what.

Conventions:

- Prefix table names with nothing; the module's ownership is expressed by which changelog created
  it. Keep names singular and unqualified (`mission`, `crew_member`).
- **No foreign keys across module boundaries.** Reference another module's row by ID and let the
  owning module validate it. A cross-module FK is a hard coupling the database will enforce
  forever, and it is the single hardest thing to unpick if a module is ever extracted.
- `spring.jpa.hibernate.ddl-auto` is `validate`. Liquibase owns the schema; Hibernate only checks
  that the mapping matches. Never set this to `update`.

## Enforcement

Everything above is enforced by
[`ModularityTests`](../backend/src/test/java/com/missioncontrol/ModularityTests.java), the one
test in the codebase. Run it with:

```bash
docker compose run --rm backend ./mvnw test
```

It uses no Spring context and no database — Spring Modulith analyses the compiled bytecode with
ArchUnit — so it finishes in a few seconds.

`ApplicationModules.verify()` fails the build on:

- a module referencing a **non-exposed type** in another module (anything outside that module's
  base package when it is closed);
- a module depending on one it has **not declared** in `allowedDependencies`;
- **cyclic** dependencies between modules.

Detection covers field types, constructor calls and method calls, and violations are reported
with a file and line number:

```
org.springframework.modulith.core.Violations:
- Module 'probebeta' depends on non-exposed type
  com.missioncontrol.probealpha.internal.AlphaInternal within module 'probealpha'!
  Method <com.missioncontrol.probebeta.BetaService.leak()> calls method
  <...AlphaInternal.secret()> in (BetaService.java:9)
```

That output is real: the guard was validated by temporarily adding two closed modules with a
deliberate violation, confirming the build failed, and removing them again. A guardrail nobody
has watched fail is not yet a guardrail.

### The caveat that matters right now

**An `OPEN` module exposes everything by design, so violations against it cannot be detected.**
Both current modules — `platform` and `shared` — are open infrastructure, which means the test
has almost nothing to bite on today. It passes because there is nothing yet to catch, not because
it is proving much.

It becomes load-bearing the moment the first **closed** domain module lands. It exists now so
that boundary enforcement is already in place on the day it starts to matter, rather than being
retrofitted onto code that has already grown around its absence.

### Generated documentation

The second test writes C4-style component diagrams and a per-module canvas into
`target/spring-modulith-docs`:

| File                     | Contents                                                |
| ------------------------ | ------------------------------------------------------- |
| `components.puml`        | PlantUML diagram of all modules and their dependencies  |
| `module-<name>.puml`     | Per-module diagram                                      |
| `module-<name>.adoc`     | Module canvas: exposed types, Spring beans, properties  |
| `all-docs.adoc`          | Everything combined                                     |

These are derived from the code and its Javadoc, so unlike a diagram in a wiki they cannot drift
out of date. They are build output and are not committed.

## Checklist: adding a module

1. Create the package `com.missioncontrol.<module>`.
2. Add `package-info.java`. For a **domain** module do *not* mark it `OPEN`:
   ```java
   @org.springframework.modulith.ApplicationModule(displayName = "Mission")
   package com.missioncontrol.mission;
   ```
3. Create `api/` and `internal/` sub-packages. Put the entity, repository, service
   implementation and controller in `internal`; expose only interfaces, DTOs and events from
   `api`.
4. Add migrations under `db/changelog/modules/<module>/`. No edit to the master changelog is
   needed.
5. Run `docker compose run --rm backend ./mvnw test`. `ModularityTests` picks the new module up
   automatically — no test needs editing. If it fails, the boundary is genuinely wrong; fix the
   code rather than opening up the module.
6. Regenerate the frontend client: `docker compose exec frontend npm run generate:api`.

## Frontend

The frontend is not modularised — it is small, and imposing structure on it now would be
speculation. The one rule that matters:

**`src/api/generated/` is generated, committed, and never hand-edited.** It comes from the
backend's OpenAPI document. When the backend contract changes, regenerate; TypeScript will then
point at every call site that needs updating.

## Open decisions

Things deliberately not settled yet, recorded so they are not silently forgotten:

- **Authentication.** Spring Security is wired but everything is `permitAll()`. `JwtProperties`
  sketches the intended configuration shape. Whether crew members are also application users is
  an open modelling question — the answer decides whether `identity` is one module or two.
- **Multi-tenancy.** Not considered at all. Retrofitting it is expensive; if there is any chance
  of it, decide before the schema grows.
- **Frontend routing.** No router yet, because there is one page.
