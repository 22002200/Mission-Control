# 05 — Mission Approval

## Purpose

Put a director's decision between a planned mission and a crewed one. A Mission Lead submits a
plan; a Director approves or rejects it; a rejected plan can be revised and resubmitted, or
abandoned.

Delivers the approval half of `product.md` §3 *Mission Management* and the whole of §4
*Mission Lifecycle*. Separated from [04](04-mission-management.md) because it is the only place
where roles genuinely divide: the person who plans a mission is never the person who approves it.

## Functional requirements

- **FR-1** `POST /api/missions/{id}/submit` moves `PLAN` to `PENDING_APPROVAL` and opens a
  `MissionApproval` with decision `PENDING`.
- **FR-2** `POST /api/missions/{id}/approve` moves `PENDING_APPROVAL` to `APPROVED` and records the
  decision.
- **FR-3** `POST /api/missions/{id}/reject` moves `PENDING_APPROVAL` to `REJECTED` with a required
  comment.
- **FR-4** `POST /api/missions/{id}/return-to-plan` moves `REJECTED` back to `PLAN` so the mission
  can be revised and resubmitted.
- **FR-5** A rejected mission can instead be closed, via the close action in
  [04](04-mission-management.md), with `closeReason` of `REJECTED`.
- **FR-6** `GET /api/missions/{id}/approvals` returns every approval cycle, newest first.
- **FR-7** A mission may be submitted, rejected and resubmitted any number of times; each cycle is
  its own record.
- **FR-8** Directors can list missions awaiting their decision.

## Non-functional requirements

- **NFR-1** Submitting and deciding are single transactions covering both the mission status change
  and the approval record. Neither can be left without the other.
- **NFR-2** Concurrent decisions on one mission must not both succeed; the second sees the status
  has already moved and fails.
- **NFR-3** Approval history is append-only. A decision, once recorded, is never edited or deleted.
- **NFR-4** Every decision records who made it and when, in UTC.

## Business rules

- **BR-1** Only `PLAN` may be submitted — *enforces M3*.
- **BR-2** Only the owning Mission Lead may submit — *enforces M6*.
- **BR-3** Approve and reject are valid only from `PENDING_APPROVAL`, and only for a `DIRECTOR` in
  the same organisation — *enforces M7*.
- **BR-4** A mission has at most one `PENDING` approval at a time — *enforces M8*.
- **BR-5** Submitting requires at least one crew requirement — *enforces M12*. Without this, an
  empty mission would be vacuously fully staffed and could be started the moment it was approved.
- **BR-6** Rejection requires a comment. A rejected plan that does not say why is not actionable.
- **BR-7** `REJECTED` may go to `PLAN` or to `CLOSED`, and nowhere else — *enforces M3*.
- **BR-8** A director may approve any mission in their organisation. Since directors cannot own
  missions (M2), a director can never approve their own work.
- **BR-9** Returning to `PLAN` leaves the approval history intact; the next submission opens a new
  cycle rather than reopening the old one.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/missions/{id}/submit` | owner MISSION_LEAD | `PLAN` → `PENDING_APPROVAL` |
| POST | `/api/missions/{id}/approve` | DIRECTOR | `PENDING_APPROVAL` → `APPROVED` |
| POST | `/api/missions/{id}/reject` | DIRECTOR | `PENDING_APPROVAL` → `REJECTED` |
| POST | `/api/missions/{id}/return-to-plan` | owner MISSION_LEAD | `REJECTED` → `PLAN` |
| GET | `/api/missions/{id}/approvals` | any with visibility | Full decision history |

**POST `/api/missions/{id}/submit`**

- Request — none
- Response 200 — the mission, `status: "PENDING_APPROVAL"`
- Errors — 403, 404, 409 not in `PLAN`, 409 no requirements

**POST `/api/missions/{id}/approve`**

- Request — `comment` (optional)
- Response 200 — the mission, `status: "APPROVED"`
- Errors — 403, 404, 409 not in `PENDING_APPROVAL`

**POST `/api/missions/{id}/reject`**

- Request — `comment` (**required**, 1–1000 chars)
- Response 200 — the mission, `status: "REJECTED"`
- Errors — 400 comment missing, 403, 404, 409 not in `PENDING_APPROVAL`

**POST `/api/missions/{id}/return-to-plan`**

- Request — none
- Response 200 — the mission, `status: "PLAN"`
- Errors — 403, 404, 409 not in `REJECTED`

**GET `/api/missions/{id}/approvals`**

- Response 200 — list of `id`, `decision`, `comment`,
  `submittedBy` (`id`, `fullName`), `submittedAt`,
  `decidedBy` (`id`, `fullName`, null while pending), `decidedAt`

Missions awaiting decision are reached through `GET /api/missions?status=PENDING_APPROVAL` from
[04](04-mission-management.md), which for a director is already organisation-wide. No separate
endpoint.

## Acceptance criteria

- [ ] An owning mission lead can submit a `PLAN` mission with at least one requirement.
- [ ] Submitting a mission with no requirements is rejected with 409.
- [ ] A non-owning mission lead cannot submit.
- [ ] A director cannot submit a mission.
- [ ] Submitting a mission that is not in `PLAN` is rejected.
- [ ] A director can approve a `PENDING_APPROVAL` mission.
- [ ] A mission lead attempting to approve receives 403.
- [ ] A director in another organisation receives 404.
- [ ] Rejection without a comment is rejected with 400.
- [ ] A rejected mission can be returned to `PLAN`, edited, and resubmitted.
- [ ] A rejected mission can instead be closed with `closeReason` of `REJECTED`.
- [ ] After submit → reject → plan → submit → approve, the history holds two cycles in order.
- [ ] At no point does a mission have two `PENDING` approvals.
- [ ] Approving an already-approved mission is rejected with 409.
- [ ] Two concurrent approvals on one mission result in exactly one success and one 409.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| Rejection comment missing or too long | 400 | `urn:mission-control:validation-failed` |
| Caller is not the owner (submit, return) | 403 | `urn:mission-control:forbidden` |
| Caller is not a director (approve, reject) | 403 | `urn:mission-control:forbidden` |
| Mission absent, or in another organisation | 404 | `urn:mission-control:not-found` |
| Action invalid from the current status | 409 | `urn:mission-control:invalid-transition` |
| Submit attempted with no requirements | 409 | `urn:mission-control:mission-has-no-requirements` |
| Concurrent decision already applied | 409 | `urn:mission-control:invalid-transition` |

`urn:mission-control:invalid-transition` carries `currentStatus` and `attemptedTransition`, so a
client can tell a stale view from a genuine mistake.
