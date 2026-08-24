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
| Frontend  | React 19.2.8, TypeScript 6.0.3, Vite 8, TanStack Query 5, MUI 9         |
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

`.env` supplies the JWT signing secret. There is no default baked into the build, so Compose
refuses to start without `JWT_SECRET` set - copying `.env.example` is what provides it.

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

### Seed data

The local and docker profiles load two fictional organisations so multi-tenant behaviour can be
exercised rather than assumed. See [`docs/features/01-seed-data.md`](docs/features/01-seed-data.md).

**Every seeded user has the password `Password123!`**

| Organisation | Role | Email |
| --- | --- | --- |
| Orbital Dynamics | Director | `vera.lindholm@orbitaldynamics.example` |
| Orbital Dynamics | Mission Lead | `marcus.reyes@orbitaldynamics.example` |
| Orbital Dynamics | Mission Lead | `priya.raman@orbitaldynamics.example` |
| Orbital Dynamics | Crew (×8) | `ada.kowalski@`, `bruno.sato@`, `chen.ibarra@`, `dana.osei@`, `elif.novak@`, `farid.lindqvist@`, `greta.mbeki@`, `hugo.delacroix@` `orbitaldynamics.example` |
| Helios Aerospace | Director | `tomas.eriksen@heliosaero.example` |
| Helios Aerospace | Mission Lead | `sofia.mendes@heliosaero.example` |
| Helios Aerospace | Mission Lead | `daniel.okafor@heliosaero.example` |
| Helios Aerospace | Crew (×6) | `ines.varga@`, `jonas.petrov@`, `kira.almeida@`, `liam.ferreira@`, `maya.tanaka@`, `nikolai.berg@` `heliosaero.example` |
| Orbital Dynamics | Mission Lead — **disabled** | `oona.halvorsen@orbitaldynamics.example` |

`oona.halvorsen@` exists to demonstrate that a non-`ACTIVE` account cannot log in: the password is
correct and the request is still refused, with `403 urn:mission-control:account-disabled`.

These credentials are demo data and are deliberately weak. Seed changesets are tagged with the
Liquibase context `seed`, which only the `local` and `docker` profiles activate — a deployment
that leaves `spring.liquibase.contexts` at its default gets the schema and none of this.

To run against a database with schema but no demo data:

```bash
SPRING_LIQUIBASE_CONTEXTS=default docker compose up backend
```

## Authentication

Every `/api` endpoint except `POST /api/auth/login` requires a bearer token.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/login` | Exchange email and password for a token |
| POST | `/api/auth/logout` | Revoke the caller's tokens |
| GET  | `/api/auth/me` | The caller's identity and role |

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login   -H 'Content-Type: application/json'   -d '{"email":"vera.lindholm@orbitaldynamics.example","password":"Password123!"}'   | sed -E 's/.*"token":"([^"]+)".*//')

curl -s localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

Three things worth knowing:

- **Tokens last 8 hours and there is no refresh token.** When one expires, log in again.
- **Logout revokes every token that user holds**, not just the one presented. It works by stamping
  `tokens_valid_from` on the user rather than by keeping a blacklist, so any token issued before
  that instant stops being accepted. Per-device logout would need a token table; see the open
  question in [`docs/data-model.md`](docs/data-model.md#open-questions).
- **An unknown email and a wrong password return the identical 401**, deliberately, so login cannot
  be used to discover which addresses have accounts.

`/api/system/info` is now behind authentication too, so the frontend shows it only once signed in.
That is the intended behaviour, not a regression.

In the UI, signing in reveals an account menu pinned to the top-right corner: the user's name with
a dropdown arrow, opening onto their role, organisation and a **Sign out** item.

## Skill catalogue

Read-only so far. Every role may read; creating, editing and retiring skills are director-only and
not yet built. See [`docs/features/03-skill-catalogue.md`](docs/features/03-skill-catalogue.md).

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/skills` | One page of the caller's organisation's catalogue, sorted by name |
| GET | `/api/skills/{id}` | One skill |

`GET /api/skills` takes four optional query parameters: `active` (boolean), `search` (a
case-insensitive substring of the name), `page` (default 0) and `size` (default 50, maximum 200).

```bash
curl -s "localhost:8080/api/skills?search=systems&size=5" -H "Authorization: Bearer $TOKEN"
```

Two things worth knowing:

- **The organisation comes from the token.** There is no parameter for it, and supplying one is
  inert. Asking for a skill belonging to another organisation returns **404, not 403** - a 403
  would confirm the row exists.
- **There is no delete.** Skills are retired with an `active` flag so that crew ratings and mission
  requirements already referencing them stay readable.

### A note on styling

Two systems coexist, on purpose. The shell and the login form use the hand-rolled `mc-` classes in
`src/index.css`; the account menu uses MUI, themed in `src/theme.ts` to the same six colours. MUI
was introduced for the dropdown specifically - `Menu` already handles focus trapping, keyboard
navigation, Escape and click-away, which is most of what a correct dropdown is. There is no
`CssBaseline`, because it would reset the body styling `index.css` owns.

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
# 1. generate, inside the container, against the backend on the project network
docker compose exec -e OPENAPI_URL=http://backend:8080/v3/api-docs frontend npm run generate:api

# 2. copy the result back onto the host, because it is committed
docker compose cp frontend:/app/src/api/generated ./frontend/src/api/generated
```

Both steps are needed, and neither is obvious:

- `openapi-ts.config.ts` defaults to `http://localhost:8080/v3/api-docs`, which inside the frontend
  container resolves to the frontend itself. `OPENAPI_URL` points it at the backend service.
- Compose `develop.watch` syncs **host to container only**. Without the copy back, the generator's
  output lives in the container and the committed client silently stays stale.

Because the output is typed, a backend contract change surfaces as a TypeScript compile error at
every affected call site rather than a runtime 404. Regenerating is not optional — treat a stale
`generated/` directory as a broken build.

Two settings in `backend/.../platform/OpenApiConfig.java` exist specifically to keep the generated
output stable and pleasant to consume; both have comments explaining why:

- the server URL is pinned to `/`, so no environment-specific host leaks into committed code;
- `SystemInfo` marks its fields `REQUIRED`, so the generated TypeScript properties are
  non-optional.

## Testing

| Tier | Command | What it covers |
| --- | --- | --- |
| Unit + API + boundaries | `docker compose run --rm backend ./mvnw test` | Token minting and validation, login rules, problem responses, the filter-chain policy, and `ModularityTests` |
| Integration | `cd backend && ./mvnw verify` *(on the host)* | The whole application against a real PostgreSQL and the real seed data |
| Frontend | `docker compose exec frontend npm test` | Session storage, the auth provider, the login form, the account menu, and which screen renders |

**Integration tests do not run in the dev container.** They use Testcontainers, which needs a
Docker socket, and the backend service has none - so `./mvnw test` inside Compose stays
container-free and the `*IT` classes are bound to `verify` via failsafe instead. Run those on the
host, where Docker Desktop is available. Host builds need JDK 21:

```bash
cd backend
JAVA_HOME="C:/Users/61449/.jdks/temurin-21.0.12" ./mvnw verify
```

All the integration tests share a single container and a single Spring context. Because they also
share one database, and logout writes to the user row, each test class works with its own seeded
accounts - see the note in `AbstractIntegrationTest`.

## Common commands

```bash
# Backend
docker compose run --rm backend ./mvnw test           # unit, API and module-boundary tests
docker compose run --rm backend ./mvnw package        # build the jar
docker compose exec db psql -U missioncontrol -d missioncontrol   # psql shell
docker compose logs -f backend

# Backend integration tests (Testcontainers - needs a Docker daemon, so run on the host)
cd backend && JAVA_HOME="C:/Users/61449/.jdks/temurin-21.0.12" ./mvnw verify

# Frontend
docker compose exec frontend npm test
docker compose exec frontend npm run typecheck
docker compose exec frontend npm run lint
docker compose exec frontend npm run build
docker compose exec frontend npm run generate:api

# Reset the database completely
docker compose down -v
```

## Current limitations

These are deliberate, not oversights:

- **No refresh tokens, and logout is global.** An expired session means logging in again, and
  signing out on one device signs out everywhere.
- **No user management.** Accounts exist only because feature 01 seeds them; there is no
  registration, invite or password-reset flow.
- **Cross-tenant 404s are enforceable but not yet demonstrable.** The rule that a resource in
  another organisation is reported as 404 rather than 403 is what `CurrentUser` exists to make
  possible, but no endpoint takes a resource id yet. Feature 03 is the first that can show it.
- **The remaining domain modules are missing** — no Mission, Crew, Skill or Assignment.
- **Demo secrets.** `JWT_SECRET` and the database password in `.env.example` are development
  values. Nothing here is a deployment manifest.
