# Mission Control

A web application for planning space missions and assigning crew to them, built as a
**modular monolith**.

Five domain modules are built: `identity` (logging in), `skill` (the org-scoped catalogue, reads
only), `mission` (planning missions, the crew they call for, and getting them approved),
`matching` (ranked crew suggestions) and `assignment` (offering places and answering them). The
dashboards are specified but not yet built — see
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

**Assignments are seeded for Helios Aerospace only.** Sign in as `ines.varga@heliosaero.example`
to find an offer waiting to be accepted or declined. Orbital Dynamics is deliberately left with
none: the worked example in
[`docs/features/06-crew-matching.md`](docs/features/06-crew-matching.md) reproduces against that
roster precisely because its experience and load terms are zero, and a single accepted assignment
there would change every score it documents.

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
- **`POST /start` needs a full crew.** Staffing counts come from the assignment module, so a
  mission only starts once every requirement has as many acceptances as it asked for. Until
  feature 07 that could never happen and the call always failed; now it is a real precondition
  rather than a missing one.

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

### Crew assignment

A Mission Lead offers places on an approved mission; the crew member accepts or declines; the lead
can withdraw somebody at any point before the mission closes. Accepting is what consumes a crew
member's availability and builds their assignment history. See
[`docs/features/07-crew-assignment.md`](docs/features/07-crew-assignment.md).

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/missions/{id}/assignments` | owner | Offer a crew member a place |
| GET | `/api/missions/{id}/assignments` | owner or DIRECTOR | The mission's crew, by requirement |
| GET | `/api/assignments/me` | CREW_MEMBER | The caller's own assignments |
| POST | `/api/assignments/{id}/accept` | CREW_MEMBER (self) | Take the place |
| POST | `/api/assignments/{id}/decline` | CREW_MEMBER (self) | Turn it down |
| POST | `/api/assignments/{id}/withdraw` | owner | Take it back |

`GET /api/assignments/me` takes `status`, `timeframe` (`CURRENT`, `UPCOMING` or `PAST`), `page` and
`size`.

```bash
curl -s -X POST "localhost:8080/api/missions/$ID/assignments" -H "Authorization: Bearer $TOKEN"   -H 'Content-Type: application/json'   -d '{"crewRequirementId":"'$REQ'","crewMemberId":"'$CREW'"}'
```

Five things worth knowing:

- **An offer holds the seat, but not the person.** Two mission leads may legitimately offer the
  same crew member overlapping dates and neither offer is refused. The clash surfaces when the
  second one is *accepted*, as a `409 urn:mission-control:schedule-conflict` naming the mission
  already committed to. That is a normal outcome rather than a fault, and it is the error leads
  will meet most.
- **Offers can only be made while a mission is `APPROVED`.** Not while it is planning, and not
  once it is flying. A place vacated after launch is dealt with by editing the mission, which
  sends it back to `PLAN` for re-approval.
- **The two halves of the workflow never overlap.** Offering and withdrawing are the owning lead's;
  accepting and declining are the crew member's. A director sees every assignment in the
  organisation and acts on none of them — their lever on a mission they disagree with is closing
  it. And once a crew member has accepted they are assigned: being let off is the lead's decision,
  not theirs.
- **Closing a mission withdraws its outstanding offers and leaves the acceptances alone.** An
  unanswered offer to a finished mission is moot; the acceptances are the crew member's history,
  and history is derived from exactly those rows. A closed mission also stops occupying anyone's
  calendar, so aborting one frees its crew immediately.
- **Two people accepting the last place produce one success and one 409.** Every staffing command
  takes the mission's write lock first, and an acceptance takes a second lock on the crew member's
  own open assignments — which is what catches one person accepting two clashing missions at once,
  a race the mission lock cannot see.

In the UI, offering happens on the crew matching board, where the reasoning for choosing one
candidate over another is visible; withdrawing is on the mission page, under the requirement, where
a director reads the same list with no buttons on it. A crew member sees **Your assignments** above
their mission board, with pending offers pinned to the top.

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
share one database, each test class works with its own seeded accounts - see the note in
`AbstractIntegrationTest`. There are now two reasons for that split rather than one: logout writes
to the user row and would revoke a token another class is holding, and since feature 07 a crew
member's availability is organisation-wide, so accepting a place on somebody's behalf changes what
every other class can do with them.

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
- **A crew matching draft is never saved.** Suggestions are worked out fresh on each request, and
  a draft is client state until the lead presses Offer. Nothing records *why* a crew member was
  suggested either - match runs are transient, so there is no audit of a ranking.
- **A Director cannot return a rejected mission to planning, and cannot offer or withdraw crew.**
  All three are owner-only; a Director's route out of a mission they disagree with is to close it.
- **A place vacated on a running mission cannot be refilled.** Offers may only be made while a
  mission is `APPROVED`, so re-crewing an `ACTIVE` one means editing it back to `PLAN` and having
  it approved again. That is the strict reading of invariant A1 and it is deliberate.
- **No dashboards.** Feature 08 gives each role a landing screen; until then a crew member's
  offers appear as a section above the mission board.
- **Demo secrets.** `JWT_SECRET` and the database password in `.env.example` are development
  values. Nothing here is a deployment manifest.
