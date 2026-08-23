# Mission Control

A web application for planning space missions and assigning crew to them, built as a
**modular monolith**.

This is currently a *walking skeleton*: the structure, build, and feedback loops are in place
and proven end to end, but there are no domain modules yet. See
[`docs/architecture.md`](docs/architecture.md) for the module rules and the checklist for
adding the first one.

## Stack

| Layer     | Technology                                                              |
| --------- | ----------------------------------------------------------------------- |
| Backend   | Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.12, Maven (via wrapper) |
| Database  | PostgreSQL 18, schema managed by Liquibase                              |
| API docs  | springdoc-openapi 2.9.0 (`/swagger-ui.html`)                            |
| Frontend  | React 19.2.8, TypeScript 6.0.3, Vite 8, TanStack Query 5               |
| API client| Generated from the OpenAPI spec by `@hey-api/openapi-ts`                |
| Local dev | Docker Compose v5                                                       |

> **Note on Spring Boot 3.5.16.** Free OSS support for the 3.5 line ended 30 June 2026, and
> 3.5.16 is its final community release. This was a deliberate choice, but it means no free
> security patches. Moving to Boot 4.x later also requires Spring Modulith 2.x and
> springdoc 3.x — those three versions must move together.

## Prerequisites

- **Docker Desktop** (tested on 29.7.2 / Compose v5.4.0). This is the only hard requirement —
  the full stack builds and runs in containers.
- **JDK 21** and **Node 24**, only if you want to run either side directly on the host.
  Neither is needed for the Docker workflow.

## Getting started

```bash
cp .env.example .env
docker compose up --build --watch
```

| Service      | URL                                   |
| ------------ | ------------------------------------- |
| Frontend     | http://localhost:5173                 |
| Backend API  | http://localhost:8080/api/system/info |
| Swagger UI   | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs     |
| Health       | http://localhost:8080/actuator/health |
| PostgreSQL   | `localhost:5432`                      |

The first build downloads the Maven and npm dependency trees and takes a few minutes.
Subsequent builds reuse cached layers and the `maven-cache` volume.

### Why `--watch`

`docker compose up --watch` *syncs* changed files into the running containers rather than
bind-mounting the host filesystem. On Windows this matters: bind mounts do not propagate
inotify events into Linux containers, so Vite's HMR silently stops working and you end up
reaching for polling workarounds. Syncing writes real files inside the container, so file
watching behaves normally.

- **Frontend** — changes under `frontend/src` sync in and hot-reload instantly.
- **Backend** — changes under `backend/src` sync in and restart the container, which
  recompiles. Roughly 15–20 seconds end to end.
- **`pom.xml` / `package.json`** — trigger a full image rebuild, since dependencies changed.

## Development workflows

### Everything in Docker (default)

```bash
docker compose up --build --watch
```

### Backend from IntelliJ, everything else in Docker

Faster backend iteration and a proper debugger.

```bash
docker compose up -d db frontend
```

Then run `MissionControlApplication` from the IDE with the **`local`** profile active.
The `local` profile points the datasource at `localhost:5432`.

`java` on this machine's `PATH` is Java 11, and `JAVA_HOME` is unset. For host-side builds:

```powershell
setx JAVA_HOME "C:\Users\61449\.jdks\temurin-21.0.12"
```

Open a **new** terminal afterwards. If you forget, `maven-enforcer-plugin` fails the build with
an explanatory message rather than an obscure bytecode error.

### Production-shaped build

Runs the packaged jar on a slim JRE and serves the built frontend from nginx:

```bash
docker compose -f compose.yaml -f compose.prod.yaml up --build
```

This validates the production build locally. It is **not** a deployment manifest — secrets
still come from `.env` and the database is still a container.

## The API client contract

The frontend never hand-writes types for the backend. `frontend/src/api/generated/` is produced
from the backend's live OpenAPI document and **is committed**, so the frontend builds without a
running backend.

After changing any controller or DTO, with the backend running:

```bash
docker compose exec frontend npm run generate:api
```

Because the output is typed, a backend contract change surfaces as a TypeScript compile error at
every affected call site rather than a runtime 404. Regenerating is not optional — treat a stale
`generated/` directory as a broken build.

Two settings in `backend/.../platform/OpenApiConfig.java` exist specifically to keep the generated
output stable and pleasant to consume; both have comments explaining why:

- the server URL is pinned to `/`, so no environment-specific host leaks into committed code;
- `SystemInfo` marks its fields `REQUIRED`, so the generated TypeScript properties are
  non-optional.

## Common commands

```bash
# Backend
docker compose run --rm backend ./mvnw test           # module boundary checks
docker compose run --rm backend ./mvnw package        # build the jar
docker compose exec db psql -U missioncontrol -d missioncontrol   # psql shell
docker compose logs -f backend

# Frontend
docker compose exec frontend npm run typecheck
docker compose exec frontend npm run lint
docker compose exec frontend npm run build
docker compose exec frontend npm run generate:api

# Reset the database completely
docker compose down -v
```

## Current limitations

These are deliberate, not oversights:

- **There is no authentication.** Spring Security is wired up but every endpoint is
  `permitAll()`. See the `TODO(auth)` in `SecurityConfig`. Do not expose this beyond localhost.
- **Test coverage is one test.** `ModularityTests` enforces the module boundaries (see
  [`docs/architecture.md`](docs/architecture.md#enforcement)). There are no integration tests and
  no frontend tests; the dependencies for both are declared and ready to use. Note that because
  both current modules are `OPEN`, the boundary check has little to bite on until the first
  closed domain module exists.
- **There are no domain modules yet** — no Mission, Crew, or Assignment.
