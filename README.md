# Mission Control

A web application for planning space missions and assigning crew to them, built as a
**modular monolith**.

Three domain modules are built: `identity` (logging in), `skill` (the org-scoped catalogue, reads
only) and `mission` (planning missions, the crew they call for, and getting them approved).
Matching, crew assignment and the dashboards are specified but not yet built — see
[`docs/features/`](docs/features/README.md) for the build order.

See [`docs/architecture.md`](docs/architecture.md) for the module rules and the checklist for
adding the next one.

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

## Missions

Mission Leads plan missions and describe the crew they need; Directors oversee every mission in
their organisation and decide whether a plan goes ahead. Matching and crew assignment come later -
see [`docs/features/04-mission-management.md`](docs/features/04-mission-management.md) and
[`docs/features/05-mission-approval.md`](docs/features/05-mission-approval.md).

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/missions` | any | One page, scoped by role |
| POST | `/api/missions` | MISSION_LEAD | Create, in `PLAN` |
| GET | `/api/missions/{id}` | any with visibility | One mission with its requirements |
| PATCH | `/api/missions/{id}` | owner or DIRECTOR | Edit name, description or dates |
| POST | `/api/missions/{id}/start` | owner or DIRECTOR | `APPROVED` to `ACTIVE` |
| POST | `/api/missions/{id}/close` | owner or DIRECTOR | Close, which is also how a mission is aborted |
| POST | `/api/missions/{id}/requirements` | owner | Add a staffing line |
| PATCH | `/api/missions/{id}/requirements/{reqId}` | owner | Replace one, skills included |
| DELETE | `/api/missions/{id}/requirements/{reqId}` | owner | Remove one |
| POST | `/api/missions/{id}/submit` | owner | `PLAN` to `PENDING_APPROVAL` |
| POST | `/api/missions/{id}/approve` | DIRECTOR | `PENDING_APPROVAL` to `APPROVED` |
| POST | `/api/missions/{id}/reject` | DIRECTOR | `PENDING_APPROVAL` to `REJECTED`, with a reason |
| POST | `/api/missions/{id}/replan` | owner | `REJECTED` back to `PLAN` |
| GET | `/api/missions/{id}/approvals` | any with visibility | Every decision cycle, newest first |

`GET /api/missions` takes `status` (**repeatable** - `?status=PLAN&status=APPROVED`), `search`,
`page` and `size` (default 20, maximum 100). It is sorted by start date.

```bash
curl -s "localhost:8080/api/missions?status=ACTIVE&status=CLOSED" -H "Authorization: Bearer $TOKEN"
```

Four things worth knowing:

- **The list is scoped by role.** A Mission Lead sees the missions they own, a Director sees every
  mission in the organisation, and a Crew Member sees only the ones they hold an assignment on.
- **Anything you cannot see is a 404, including within your own organisation.** A second mission
  lead asking for someone else's mission gets the same answer as another tenant. 403 is reserved
  for a caller who genuinely can see the mission but may not change it.
- **Editing an `APPROVED` or `ACTIVE` mission sends it back to `PLAN`.** The approval described a
  plan that no longer exists, so it has to be resubmitted. The UI warns before you save.
- **`POST /start` always fails right now**, and that is correct rather than broken. Staffing counts
  come from the assignment module, which does not exist until feature 07, so no mission reads as
  crewed. A mission can now legitimately reach `APPROVED`, so this is the one step still missing.

### Approval

A Mission Lead submits a plan; a Director approves or rejects it; a rejected plan is revised and
resubmitted, or abandoned. This is the only place in the product where the roles genuinely divide -
and because a Director cannot own a mission, a Director can never approve their own work.

```bash
curl -s -X POST "localhost:8080/api/missions/$ID/reject" -H "Authorization: Bearer $TOKEN"   -H 'Content-Type: application/json' -d '{"comment":"The window clashes with the Vesta flyby."}'
```

Four things worth knowing:

- **A plan needs someone to staff.** Submitting a mission with no crew requirements is refused: an
  empty mission is vacuously fully crewed, and would otherwise be startable the moment it was
  approved.
- **A rejection must say why.** The comment is required, in the API, in the UI and as a database
  constraint. A rejected plan that does not say what to fix is not actionable.
- **Every submit-and-decide cycle is kept.** Sending a plan back and resubmitting it opens a *new*
  cycle rather than overwriting the old one, so `GET /approvals` is the full history. Returning a
  rejected mission to planning changes nothing already recorded.
- **Two directors deciding at once produce one decision.** The second gets `409` carrying
  `currentStatus`, so a client can tell a stale screen from a mistake. That holds because every
  command takes a write lock on the mission row - including `close`, which would otherwise commit
  over a decision it had read before the other caller made it.

In the UI a Director's board gains an **Awaiting approval** section holding just the missions
waiting on them, and the mission page grows Submit / Approve / Reject / Return to plan according to
who is looking. Each mission page carries its approval history, which opens itself when the newest
cycle is a rejection.

### The mission board

The signed-in application is a board at `/missions`, split into three lifecycle sections - **Draft**
(`PLAN`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`), **Active**, and **Completed** - each a
responsive grid of cards that pages independently. Each section queries only its own statuses; the
alternative, fetching one page and bucketing it client-side, would silently drop any mission that
fell on another page.

Times are entered and displayed in your own timezone and stored as UTC. Everything goes through
`frontend/src/lib/datetime.ts`.

### A note on styling

MUI throughout, themed in `src/theme.ts`. The hand-rolled `mc-` classes that used to dress the
shell and the login form are gone: they were a reasonable trade while there was one authenticated
screen, and stopped being one at several. `src/index.css` is now just the palette custom properties
and the page background, which has to be painted before React mounts or the first frame is a white
flash on a dark application. The colours are duplicated between the two files deliberately - see
the comment in `theme.ts`.

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
- **Missions cannot be crewed or started.** Features 04 and 05 build planning, approval, editing
  and closing; matching is 06 and crew assignment 07. Until then every mission reads as unstaffed
  and `POST /start` is refused - correctly, since nobody can accept a place yet.
- **A Director cannot return a rejected mission to planning.** That action is owner-only; a
  Director's route out of a rejected mission is to close it.
- **The remaining domain modules are missing** — no Crew, Assignment or Matching module, though
  the crew tables are seeded ready for them.
- **Demo secrets.** `JWT_SECRET` and the database password in `.env.example` are development
  values. Nothing here is a deployment manifest.
