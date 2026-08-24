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
- **FR-4** `POST /api/missions/{id}/replan` moves `REJECTED` back to `PLAN` so the mission
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
| POST | `/api/missions/{id}/replan` | owner MISSION_LEAD | `REJECTED` → `PLAN` |
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

**POST `/api/missions/{id}/replan`**

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

- [x] An owning mission lead can submit a `PLAN` mission with at least one requirement.
- [x] Submitting a mission with no requirements is rejected with 409.
- [x] A non-owning mission lead cannot submit. They receive **404, not 403** — see the notes below.
- [x] A director cannot submit a mission.
- [x] Submitting a mission that is not in `PLAN` is rejected.
- [x] A director can approve a `PENDING_APPROVAL` mission.
- [x] A mission lead attempting to approve receives 403.
- [x] A director in another organisation receives 404.
- [x] Rejection without a comment is rejected with 400.
- [x] A rejected mission can be returned to `PLAN`, edited, and resubmitted.
- [x] A rejected mission can instead be closed with `closeReason` of `REJECTED`.
- [x] After submit → reject → plan → submit → approve, the history holds two cycles in order.
- [x] At no point does a mission have two `PENDING` approvals.
- [x] Approving an already-approved mission is rejected with 409.
- [x] Two concurrent approvals on one mission result in exactly one success and one 409.

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

## Notes from the build

- **`POST /replan` cannot rely on the transition table.** `MissionStatus.canTransitionTo(PLAN)` is
  true from `REJECTED`, `APPROVED` **and** `ACTIVE` — the last two are M5, an edit discarding an
  approval. Three states share one arrow for two different reasons, so a `replan` guarded only by
  M3 would happily throw away a live approval with nobody having edited anything. The endpoint
  names `REJECTED` explicitly. A unit test over every other status is what caught it.

- **Concurrency is a pessimistic row lock, and every command has to take it.** NFR-2 says the
  second caller "sees the status has already moved", which is exactly what `select ... for update`
  gives: the loser blocks, re-reads the committed status, and gets a 409 carrying it. `@Version`
  would instead surface a generic lock failure knowing nothing about the new status, and would
  change every existing mission write for a guarantee only these endpoints need. The subtlety is
  that **a lock one side skips is not a lock** — dirty checking writes `set status = ?` with no
  status predicate, so an unlocked `close` would block on the winner's lock and then overwrite it,
  leaving an approval record beside a mission that contradicts it. So `update`, `start`, `close`
  and the requirement endpoints all load through the same locking path.

- **`ApprovalDecision` gained a fourth code, `CANCELLED`.** A mission can be closed while it is
  still awaiting a decision, which leaves a cycle nobody will ever decide. Left `PENDING` it reads
  on screen as still waiting for someone, and it holds M8's partial unique index against a
  resubmission that can never come. `data-model.md` is updated; the codes are append-only, so 4 is
  the only place it could go. The seam is a `MissionApprovals` component both services use, so
  `MissionService.close` records the cancellation without either service depending on the other.

- **404 beats 403 for a non-owning lead**, as feature 04 established at length. The error table
  above says "caller is not the owner → 403", but a lead who cannot *see* another lead's mission
  must not be told it exists. Visibility is checked first and answers 404; 403 is left for a caller
  who genuinely can see the mission but may not act — a director attempting `submit` or `replan`.

- **BR-3 is an annotation, not a service check.** `@PreAuthorize("hasRole('DIRECTOR')")` needs no
  mission in hand, and it does not weaken the tenant rules: a director from another organisation
  passes the role check and is then told the mission does not exist. Submit and replan carry no
  annotation, precisely so the 404 above survives.

- **The approvals list is not paged**, unlike every other list in this API. FR-6 asks for every
  cycle, and the screen showing them needs the count before it can render. That rests on an
  assumption stated in three places rather than left implicit — a mission gains a cycle only when a
  plan is sent back and resubmitted, so the count stays in single digits. If that ever stops being
  true, the fix is the standard `page`/`size` envelope with the same newest-first order.

- **The seed gained approval rows.** Seeded missions already sat in `PENDING_APPROVAL`, `APPROVED`
  and `REJECTED` from feature 04 with no cycles — a state `submit` can never produce, and one where
  a director opening *Tethys Relay* would find nothing to decide. `004-seed-mission-approvals` gives
  every seeded mission past `PLAN` the history it must have had. The three with none (two in `PLAN`,
  and *Io Survey*, aborted straight out of `PLAN`) are cases rather than omissions. Because the seed
  is now complete, `approve` and `reject` *require* an open cycle and treat its absence as a bug
  rather than reconstructing one.

- **M12 finally has a home.** Feature 04 had to refuse an empty mission at `POST /start`, because
  the real check — at submission — had nowhere to live yet. Both refusals stay: they catch the same
  hole at different depths.

- **`replan` is owner-only, which is narrower than M6.** The API table says so and this follows it:
  having another go at a plan is planning work. The consequence worth knowing is that a director has
  no way to unstick a rejected mission whose lead is unavailable — their lever is closing it as
  `REJECTED`. Note also the asymmetry a caller meets: a *director* calling `replan` gets 403 because
  they can see the mission, a *non-owning lead* gets 404 because they cannot.

- **A missing request body was a 500, everywhere.** `POST /reject` with no body is specified as a
  400 and was answering 500 — `HttpMessageNotReadableException` had no handler. Pre-existing and
  reachable since feature 04 (`POST /api/missions` with an empty body did the same), just never
  exercised. Fixed in `GlobalExceptionHandler`, which is one handler for every module.

- **`CrewRequirementRepository` is gone.** Noticed while rewiring `CrewRequirementService`, which
  injected it and never called it. The reason is structural rather than an oversight in one method:
  `CrewRequirement` is not an aggregate root. Reads come off the mission that
  `findDetailByIdAndOrganisationId` already fetch-joined; adds cascade from `mission.save`; updates
  are dirty checking; deletes are `orphanRemoval`. And loading the mission is unavoidable anyway,
  because visibility, ownership and the `PLAN` check all need it before a requirement id is
  resolved — so the requirement is always in memory before anything could look it up. Its Javadoc
  described the alternative design it was written for, *tenant-scoped in its own right rather than
  relying on the mission having been fetched first*, which is exactly the path the service does not
  take. Leaving it would have kept a false signal in the module.

### UI

The spec says nothing about the screens, so these were decisions:

- **A director gets a fourth board section, "Awaiting approval"**, holding `PENDING_APPROVAL` and
  rendered only for them. FR-8 is reached through the existing list rather than a new endpoint, but
  two missions awaiting a decision buried among everything else still in planning is how a queue
  goes unnoticed. The status is *moved* out of Draft rather than duplicated: a mission in two
  sections would be counted twice and paged independently in each. A lead keeps the original three —
  every mission in their Draft section is theirs, and they know which they submitted.
- **The actions live on the mission detail page only**, in the row beside Edit / Start / Close. A
  director has to open a mission — and so see its dates and requirements — before deciding it,
  which is the point of an approval gate.
- **Submit is shown disabled, with a reason, when a mission has no requirements**, rather than
  hidden. The same treatment `Start` already gets on an uncrewed mission, and for the same reason:
  "why can I not submit this?" is the question the screen exists to answer.
- **The rejection dialog will not submit without a comment.** The server enforces BR-6 regardless;
  this only avoids a round trip that was always going to fail, and makes the requirement visible
  before the click rather than after it.
- **The approval history is a collapsed accordion** below the requirements, which opens itself when
  the newest cycle is a rejection — that comment is the most actionable thing on a rejected mission.
  It is a *controlled* accordion: `defaultExpanded` is read on the first render, while the query is
  still pending and there is no rejection to see, so it never fired. MUI warns about exactly this.
- **Closing now offers `Rejected`** as a reason, but only for a mission that really was rejected,
  which is the one case the server accepts it in. That is FR-5.
- **`styled()` appears for the first time**, for the two multi-override pieces of the history
  timeline, per the rule in `CLAUDE.md`. The rest of the app uses `sx`; single-override cases here
  still do.
