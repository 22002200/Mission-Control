# 04 — Mission Management

## Purpose

Let a Mission Lead create a mission, describe the crew it needs, and drive it through the parts of
its lifecycle that do not involve a director: editing, starting, and closing.

Delivers most of `product.md` §3 *Mission Management*. Approval transitions live in
[05](05-mission-approval.md); crew selection in [06](06-crew-matching.md) and
[07](07-crew-assignment.md).

## Functional requirements

- **FR-1** `POST /api/missions` creates a mission in `PLAN`, owned by the calling Mission Lead.
- **FR-2** `GET /api/missions` lists missions, scoped by role: a Mission Lead sees the ones they
  own; a Director sees all in the organisation; a Crew Member sees only missions they hold an
assignment on.
- **FR-3** The list filters by `status` and sorts by `startsAt`.
- **FR-4** `GET /api/missions/{id}` returns one mission with its requirements and staffing counts.
- **FR-5** `PATCH /api/missions/{id}` edits name, description, `startsAt` and `endsAt`.
- **FR-6** Editing a mission in `APPROVED` or `ACTIVE` returns it to `PLAN`.
- **FR-7** Crew requirements are managed under the mission: create, update, delete.
- **FR-8** A requirement's required skills are supplied inline as part of the requirement payload,
  not through separate endpoints.
- **FR-9** `POST /api/missions/{id}/start` moves `APPROVED` to `ACTIVE`.
- **FR-10** `POST /api/missions/{id}/close` moves any non-terminal status to `CLOSED` with a
  `closeReason`.
- **FR-11** Mission responses report, per requirement, how many of `requiredCount` are accepted,
  and whether the mission as a whole is fully staffed.

## Non-functional requirements

- **NFR-1** Reading a mission with its requirements, skills and staffing counts is a bounded number
  of queries — no N+1 across requirements.
- **NFR-2** Staffing counts are derived from assignments on read, never stored on the mission.
- **NFR-3** `mission` must not depend on the `assignment` module. Staffing counts are supplied by a
  read model that `assignment` owns and `mission` consumes — see
  [architecture.md](../architecture.md#module-ownership).
- **NFR-4** Status changes are transactional: a rejected transition leaves nothing partly applied.
- **NFR-5** Dates are accepted and returned as UTC instants.

## Business rules

- **BR-1** `endsAt` is strictly after `startsAt` — *enforces M1*.
- **BR-2** Only a `MISSION_LEAD` may create a mission, and they own it. Directors do not own
  missions — *enforces M2*.
- **BR-3** Only the owning lead, or a director in the same organisation, may modify a mission —
  *enforces M6*.
- **BR-4** Every status change follows the permitted transitions — *enforces M3*.
- **BR-5** Editing details while `APPROVED` or `ACTIVE` reverts the mission to `PLAN`, discarding
  the approval — *enforces M5*.
- **BR-6** `closeReason` is set exactly when the mission closes, and never otherwise —
  *enforces M4*.
- **BR-7** `requiredCount` is at least 1 — *enforces M9*.
- **BR-8** A requirement lists each skill at most once, with `minimumProficiency` in 1–5 —
  *enforces M10*.
- **BR-9** `APPROVED → ACTIVE` requires every requirement to have `requiredCount` accepted
  assignments — *enforces M11*.
- **BR-10** Requirements may only be added, changed or removed while the mission is in `PLAN`.
  Changing the crew a mission needs after it has been approved would invalidate the approval, and
  BR-5 already covers that path for mission details.
- **BR-11** Closing from `ACTIVE` defaults `closeReason` to `COMPLETED`; from any other status it
  defaults to `ABORTED`. The caller may state a reason explicitly, except that `REJECTED` is only
  valid when closing a rejected mission.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/missions` | any | List, scoped by role |
| POST | `/api/missions` | MISSION_LEAD | Create in `PLAN` |
| GET | `/api/missions/{id}` | any with visibility | Mission with requirements |
| PATCH | `/api/missions/{id}` | owner or DIRECTOR | Edit details |
| POST | `/api/missions/{id}/start` | owner or DIRECTOR | `APPROVED` → `ACTIVE` |
| POST | `/api/missions/{id}/close` | owner or DIRECTOR | → `CLOSED` |
| POST | `/api/missions/{id}/requirements` | owner | Add a requirement |
| PATCH | `/api/missions/{id}/requirements/{reqId}` | owner | Update a requirement |
| DELETE | `/api/missions/{id}/requirements/{reqId}` | owner | Remove a requirement |

**POST `/api/missions`**

- Request — `name` (required), `description`, `startsAt` (required), `endsAt` (required)
- Response 201 — the mission, `status: "PLAN"`
- Errors — 400, 403

**GET `/api/missions/{id}`**

- Response 200 — `id`, `name`, `description`, `status`, `closeReason`, `startsAt`, `endsAt`,
  `missionLead` (`id`, `fullName`), `fullyStaffed` (boolean), `requirements[]`
- Each requirement — `id`, `title`, `description`, `requiredCount`, `acceptedCount`,
  `skills[]` of `skillId`, `skillName`, `minimumProficiency`, `mandatory`, `weight`

**PATCH `/api/missions/{id}`**

- Request — any of `name`, `description`, `startsAt`, `endsAt`
- Response 200 — the mission; `status` may have reverted to `PLAN`
- Errors — 400, 403, 404, 409 mission is closed

**POST `/api/missions/{id}/start`**

- Request — none
- Response 200 — the mission, `status: "ACTIVE"`
- Errors — 403, 404, 409 not `APPROVED`, 409 not fully staffed

The 409 for under-staffing lists which requirements are short, so the caller knows what to fix.

**POST `/api/missions/{id}/close`**

- Request — `closeReason` (optional: `COMPLETED` | `ABORTED`), `comment` (optional)
- Response 200 — the mission, `status: "CLOSED"`
- Errors — 403, 404, 409 already closed

**POST/PATCH `/api/missions/{id}/requirements`**

- Request — `title` (required), `description`, `requiredCount` (required, >= 1),
  `skills[]` of `skillId`, `minimumProficiency` (1–5), `mandatory` (boolean),
  `weight` (optional, default 1)
- Response 201/200 — the requirement
- Errors — 400, 403, 404, 409 mission not in `PLAN`, 409 duplicate skill, 409 skill inactive

## Acceptance criteria

- [x] A mission lead can create a mission and it starts in `PLAN`.
- [x] A director attempting to create a mission receives 403.
- [x] A mission with `endsAt` before `startsAt` is rejected.
- [x] A mission lead sees only their own missions; a director sees all in the organisation.
- [x] A crew member sees only missions they are assigned to. With no `assignment` module that list
      is always empty, which is the correct answer to the question rather than a stub: the check
      runs through the same read model feature 07 will supply.
- [x] A mission from another organisation returns 404.
- [x] A second mission lead cannot edit a mission they do not own. They receive **404, not 403** —
      a lead has no visibility of another lead's mission, so admitting it exists would leak exactly
      what the 404 elsewhere is there to hide. 403 is reserved for a caller who genuinely can see
      the mission but may not change it: a crew member on its crew.
- [x] Editing an `APPROVED` mission returns it to `PLAN`.
- [x] Editing an `ACTIVE` mission returns it to `PLAN`.
- [x] Requirements cannot be added once the mission has left `PLAN`.
- [x] A requirement listing the same skill twice is rejected.
- [x] A requirement with `requiredCount` of 0 is rejected.
- [x] Starting a mission that is not `APPROVED` is rejected.
- [x] Starting a mission with any unfilled requirement is rejected, and the response says which.
- [ ] Starting a fully staffed `APPROVED` mission succeeds. **Covered by unit and slice tests, not
      end to end.** No mission can be fully staffed until feature 07 exists, and injecting a fake
      read model into an integration test would fork the shared Spring context and start a second
      Postgres container. `MissionServiceTest` and `MissionControllerApiTest` mock the port and
      cover both the success and the shortfall; the end-to-end proof belongs with 07.
- [ ] Withdrawing crew from an `ACTIVE` mission does not revert it to `APPROVED`. **Deferred to
      [07](07-crew-assignment.md)** — there is no way to withdraw crew yet. The property holds by
      construction: M11 is checked only as a precondition of starting, and nothing re-evaluates it.
- [x] A mission can be closed from `PLAN`, `PENDING_APPROVAL`, `APPROVED` and `ACTIVE`.
- [x] Closing from `ACTIVE` without a reason records `COMPLETED`; from `PLAN`, `ABORTED`.
- [x] A closed mission rejects every further edit or transition.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| Invalid field, or `endsAt` <= `startsAt` | 400 | `urn:mission-control:validation-failed` |
| Caller is not the owner or a director | 403 | `urn:mission-control:forbidden` |
| Caller is not a mission lead (on create) | 403 | `urn:mission-control:forbidden` |
| Mission absent, or in another organisation | 404 | `urn:mission-control:not-found` |
| Transition not permitted from current status | 409 | `urn:mission-control:invalid-transition` |
| Start attempted while under-staffed | 409 | `urn:mission-control:mission-understaffed` |
| Requirement changed outside `PLAN` | 409 | `urn:mission-control:mission-not-editable` |
| Skill listed twice in one requirement | 409 | `urn:mission-control:duplicate-skill` |
| Referenced skill is inactive or unknown | 409 | `urn:mission-control:invalid-skill` |

`urn:mission-control:mission-understaffed` carries a `requirements` property listing each short
requirement with its `requiredCount` and `acceptedCount`.

`urn:mission-control:invalid-transition` carries `currentStatus` and `attemptedTransition`, so a
client can tell a stale view from a genuine mistake. That is specified in
[05](05-mission-approval.md) and was cheaper to add now than to retrofit.

## Notes from the build

- **The staffing read model is a port owned by `mission`, not by `assignment`.** NFR-3 says the
  read model is "owned by `assignment` and consumed by `mission`", but an interface declared in
  `assignment.api` would make `mission` depend on `assignment` — the exact cycle
  `ModularityTests` exists to catch. `mission.api.StaffingReadModel` is therefore declared on the
  consumer's side and `assignment` will implement it in feature 07. Ownership of the *data* is
  unchanged; only the interface moved.
- **`POST /start` cannot succeed in the running application, and that is correct.** The default
  read model reports nothing as staffed, so any mission with requirements is refused. It is moot
  in practice: without feature 05 no mission can reach `APPROVED` at all.
- **A mission with no requirements is also refused a start.** M11 read literally is vacuously
  satisfied by an empty mission — `data-model.md` says as much, and M12 closes it at submission
  time in feature 05. Refusing it here as well costs one branch and stops `fullyStaffed` and
  `start` from disagreeing.
- **`skill` and `identity` grew their first `api` packages.** A requirement stores a `skillId` and
  a mission stores a `missionLeadId`, and neither renders from an id alone. Both are bulk lookups
  only — a single-id variant would make the N+1 that NFR-1 forbids the easy thing to write. Both
  are annotated `@NamedInterface`: Spring Modulith exposes only a closed module's base package by
  default, so without it an `api` directory is a convention with nothing enforcing it.
- **`mission` gained a `close_comment` column.** The close request carries an optional `comment`
  and 04 had nowhere to put it — `MissionApproval`, which will own the rejection comment, arrives
  with 05. Storing it beat silently discarding a documented field.
- **`includeAll` in the master changelog needed an `endsWithFilter`.** It was handing the
  `.gitkeep` in `db/changelog/modules/` to a parser and aborting the whole migration, so the
  application could not start against a fresh database. Pre-existing, and unrelated to this
  feature, but it had to be fixed to get here.
