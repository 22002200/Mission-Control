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
  own; a Director sees all in the organisation; a Crew Member sees only missions they hold a
  non-terminal assignment on.
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

- [ ] A mission lead can create a mission and it starts in `PLAN`.
- [ ] A director attempting to create a mission receives 403.
- [ ] A mission with `endsAt` before `startsAt` is rejected.
- [ ] A mission lead sees only their own missions; a director sees all in the organisation.
- [ ] A crew member sees only missions they are assigned to.
- [ ] A mission from another organisation returns 404.
- [ ] A second mission lead cannot edit a mission they do not own.
- [ ] Editing an `APPROVED` mission returns it to `PLAN`.
- [ ] Editing an `ACTIVE` mission returns it to `PLAN`.
- [ ] Requirements cannot be added once the mission has left `PLAN`.
- [ ] A requirement listing the same skill twice is rejected.
- [ ] A requirement with `requiredCount` of 0 is rejected.
- [ ] Starting a mission that is not `APPROVED` is rejected.
- [ ] Starting a mission with any unfilled requirement is rejected, and the response says which.
- [ ] Starting a fully staffed `APPROVED` mission succeeds.
- [ ] Withdrawing crew from an `ACTIVE` mission does not revert it to `APPROVED`.
- [ ] A mission can be closed from `PLAN`, `PENDING_APPROVAL`, `APPROVED` and `ACTIVE`.
- [ ] Closing from `ACTIVE` without a reason records `COMPLETED`; from `PLAN`, `ABORTED`.
- [ ] A closed mission rejects every further edit or transition.

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
