# Data Model

Conceptual model only — entities, fields and invariants. No tables, columns or JPA mappings; see
[`architecture.md`](architecture.md#data-model) for the decisions and diagrams behind it.

Conventions: all timestamps are **UTC instants**. All enums are stored as **integers** using the
codes below. Every tenant-owned entity carries `organisationId`.

## Entities

### `identity`

**Organisation** — the tenant root.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `name` | |
| `createdAt` | |

**User** — an account that can log in. Exactly one role.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `email` | globally unique, case-insensitive |
| `passwordHash` | |
| `fullName` | |
| `role` | `UserRole` |
| `status` | `UserStatus` |
| `tokensValidFrom` | JWTs issued before this instant are rejected; logout sets it to now |
| `createdAt`, `updatedAt` | |

### `skill`

**Skill** — an entry in an organisation's skill catalogue.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `name` | unique per organisation, case-insensitive |
| `category` | optional grouping |
| `description` | optional |
| `active` | retire a skill without deleting history |
| `createdAt` | |

### `crew`

**CrewMember** — the crew-domain profile for a user. Thin by design: the substance is
`CrewSkill` plus derived history.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `userId` | unique; that user has role `CREW_MEMBER` |
| `createdAt`, `updatedAt` | |

**CrewSkill** — a crew member's proficiency in one skill.

| Field | Notes |
| --- | --- |
| `crewMemberId` | |
| `skillId` | |
| `proficiency` | 1–5 |

### `mission`

**Mission**

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `name`, `description` | |
| `status` | `MissionStatus` |
| `closeReason` | `MissionCloseReason`; set only when `CLOSED` |
| `closeComment` | optional note recorded when closing; the rejection comment lives on `MissionApproval` |
| `missionLeadId` | owning user, role `MISSION_LEAD` |
| `startsAt`, `endsAt` | mission timeline |
| `createdBy`, `createdAt`, `updatedAt` | |

**MissionApproval** — one row per submit-and-decide cycle, so the REJECTED to PLAN to resubmit
loop keeps its full history rather than overwriting a single rejection reason.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `missionId` | |
| `submittedBy`, `submittedAt` | |
| `decidedBy`, `decidedAt` | null while pending |
| `decision` | `ApprovalDecision`; `CANCELLED` when the mission was closed while the cycle was still open |
| `comment` | rejection reason, approval note, or the close comment of a cancelled cycle |

**CrewRequirement** — a staffing line on a mission. Quantity-based, not one row per seat.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `missionId` | |
| `title` | for example "Flight Engineer" |
| `requiredCount` | at least 1 |
| `description` | optional |

**RequiredSkill** — a skill a requirement calls for.

| Field | Notes |
| --- | --- |
| `crewRequirementId` | |
| `skillId` | |
| `minimumProficiency` | 1–5 |
| `mandatory` | true = hard filter **and** scored, preferring the least over-qualified; false = optional, scored preferring higher proficiency |
| `weight` | ranking weight for any required skill; defaults to 1 |

### `assignment`

**Assignment** — an offer of one crew member against one requirement.

| Field | Notes |
| --- | --- |
| `id` | UUID |
| `organisationId` | |
| `missionId` | |
| `crewRequirementId` | |
| `crewMemberId` | |
| `status` | `AssignmentStatus` |
| `offeredAt` | |
| `respondedAt` | null until accepted or declined |

There is no `COMPLETED` status — completion derives from the mission closing as `COMPLETED`.

## Enum codes

Codes are **pinned and append-only**. Never reorder or reuse a code: the integer is what is
stored, so changing it silently re-points existing rows at a different value.

| Enum | Codes |
| --- | --- |
| `UserRole` | 1 `DIRECTOR`, 2 `MISSION_LEAD`, 3 `CREW_MEMBER` (the type lives in `shared`, not `identity` - see architecture.md) |
| `UserStatus` | 1 `ACTIVE`, 2 `DISABLED` |
| `MissionStatus` | 1 `PLAN`, 2 `PENDING_APPROVAL`, 3 `APPROVED`, 4 `REJECTED`, 5 `ACTIVE`, 6 `CLOSED` |
| `MissionCloseReason` | 1 `COMPLETED`, 2 `ABORTED`, 3 `REJECTED` |
| `ApprovalDecision` | 1 `PENDING`, 2 `APPROVED`, 3 `REJECTED`, 4 `CANCELLED` |
| `AssignmentStatus` | 1 `OFFERED`, 2 `ACCEPTED`, 3 `DECLINED`, 4 `WITHDRAWN` |

## Invariants

Numbered so code and tests can cite them.

### Tenancy

- **T1** Every tenant-owned entity carries `organisationId`. Every query is filtered by the
  organisation on the caller's JWT, enforced in application code.
- **T2** No cross-tenant references: every reference between entities resolves within the same
  organisation.
- **T3** `organisationId` is immutable once set.

### Identity

- **I1** `email` is unique across the system, compared case-insensitively.
- **I2** A user has exactly one role.
- **I3** A user belongs to exactly one organisation.
- **I4** Only `ACTIVE` users can authenticate.

### Crew

- **C1** `CrewMember.userId` is unique, and that user has role `CREW_MEMBER`.
- **C2** `(crewMemberId, skillId)` is unique — no duplicate skills per crew member.
- **C3** `proficiency` is 1–5.

### Skill

- **S1** `(organisationId, name)` is unique, case-insensitive.
- **S2** A skill referenced by any `CrewSkill` or `RequiredSkill` is never deleted — set
  `active = false` instead, so history stays readable.

### Mission

- **M1** `endsAt` is after `startsAt`.
- **M2** `missionLeadId` is a `MISSION_LEAD` in the mission's organisation. Directors do not own
  missions.
- **M3** Status transitions are restricted to:

  | From | To |
  | --- | --- |
  | `PLAN` | `PENDING_APPROVAL`, `CLOSED` |
  | `PENDING_APPROVAL` | `APPROVED`, `REJECTED`, `CLOSED` |
  | `REJECTED` | `PLAN`, `CLOSED` |
  | `APPROVED` | `ACTIVE`, `PLAN`, `CLOSED` |
  | `ACTIVE` | `PLAN`, `CLOSED` |
  | `CLOSED` | terminal |

- **M4** `closeReason` is non-null if and only if `status` is `CLOSED`.
- **M5** Editing mission details while `APPROVED` or `ACTIVE` returns the mission to `PLAN`; the
  earlier approval no longer applies and it must be resubmitted.
- **M6** Only the owning mission lead, or a director in the same organisation, may modify a
  mission.
- **M7** Approve and reject are valid only from `PENDING_APPROVAL`, and only for a `DIRECTOR` in
  the same organisation.
- **M8** A mission has at most one `MissionApproval` with `decision = PENDING` at a time. Enforced
  as a partial unique index, so two concurrent submissions cannot both open a cycle. Closing a
  mission that is awaiting a decision settles its open cycle as `CANCELLED`, which frees the
  constraint rather than leaving it held by a cycle nobody will ever decide.
- **M9** `requiredCount` is at least 1.
- **M10** `(crewRequirementId, skillId)` is unique, and `minimumProficiency` is 1–5.
- **M11** `APPROVED` to `ACTIVE` requires every `CrewRequirement` to have `requiredCount`
  `ACCEPTED` assignments. This is a precondition of the transition, not a standing invariant:
  withdrawing crew from an already-`ACTIVE` mission does not revert it.
- **M12** Submitting for approval requires at least one `CrewRequirement`. Without this a mission
  with no requirements is vacuously fully staffed under M11 and could be started immediately.

### Assignment

- **A1** Assignments may be created only while the mission is `APPROVED` or `ACTIVE`.
- **A2** For each requirement, the number of `OFFERED` plus `ACCEPTED` assignments never exceeds
  `requiredCount`.
- **A3** A crew member has no two `ACCEPTED` assignments, on missions that are not `CLOSED`,
  whose mission date ranges overlap.
- **A4** **A3 is checked when an offer is accepted, not when it is made.** Offers never block, so
  two mission leads may legitimately offer the same crew member for clashing dates; the second
  acceptance is what must fail. This cannot be expressed as a unique constraint — it needs a
  check inside the accepting transaction.
- **A5** A crew member has at most one non-terminal (`OFFERED` or `ACCEPTED`) assignment per
  mission.
- **A6** Only the crew member themself may accept or decline their assignment.
- **A7** Status transitions: `OFFERED` to `ACCEPTED`, `DECLINED` or `WITHDRAWN`; `ACCEPTED` to
  `WITHDRAWN`. `DECLINED` and `WITHDRAWN` are terminal.
- **A8** Closing a mission withdraws its `OFFERED` assignments — an un-answered offer to a
  finished mission is moot. `ACCEPTED` assignments are left untouched: they are the crew
  member's history, and withdrawing them would erase it. A closed mission no longer occupies
  the calendar (see A3), so aborting one frees its crew immediately.

## Derived, not stored

Computed on read. Storing them would create a second source of truth that drifts.

| Concept | Derived from |
| --- | --- |
| Crew availability | Absence of an `ACCEPTED` assignment, on a mission that is not `CLOSED`, overlapping the window in question. The spec defines availability purely as "available unless assigned", so there is no leave or time-off entity. |
| Assignment history | `ACCEPTED` assignments on missions closed as `COMPLETED`. |
| Requirement fill status | Count of `ACCEPTED` assignments against `requiredCount`. Owned by the `assignment` module — see the cycle note in `architecture.md`. |
| Match suggestions | Computed per request by the `matching` module. |
| Mission tempo | The median duration of an organisation's missions closed as `COMPLETED`. Feature [06](features/06-crew-matching.md) scales its recency window to it, so an organisation running three-day sorties and one running six-month expeditions are measured on their own timescales rather than a shared year. Median rather than mean, so one freak multi-year mission cannot drag it — which is also why the window needs no floor or ceiling. |
| Org-level metrics | Aggregated over missions and assignments for the director dashboard. |

## Open questions

- **Logout scope.** `tokensValidFrom` invalidates *all* of a user's tokens, not just the current
  device. Simple, and needs no token table; revisit if per-device logout is wanted. Implemented in
  [02](features/02-authentication.md). One wrinkle worth recording: the comparison is against a
  millisecond-precision `iat_ms` claim, because a JWT's standard `iat` is whole seconds and this
  column is not, so comparing them directly is wrong in one direction or the other.
- **Mission edits after approval (M5).** Reverting to `PLAN` is the reading that satisfies both
  "edit at any time" and "resubmit for approval". What happens to crew who already accepted is
  not yet decided — currently they keep their assignments. Implemented in
  [04](features/04-mission-management.md). One consequence surfaced in 05: `APPROVED` to `PLAN` and
  `ACTIVE` to `PLAN` are legal transitions *because of M5*, so an endpoint that only asks M3
  whether `PLAN` is reachable is not asking the right question. `POST /replan` names `REJECTED`
  explicitly.
- **M11 cannot be satisfied until [07](features/07-crew-assignment.md).** Staffing counts reach
  `mission` through a port it declares itself, `mission.api.StaffingReadModel`, and the only
  implementation until then reports nothing as staffed. So `APPROVED` to `ACTIVE` is always
  refused in a running application. A mission *can* now reach `APPROVED` — that arrived with
  [05](features/05-mission-approval.md) — so this is no longer moot, merely unreachable one step
  later. One wrinkle worth recording: M11 read literally is vacuously true for a mission with no
  requirements, so 04 refuses that case explicitly rather than letting an empty mission launch.
  M12 is the real fix and it arrived with 05, at submission time; both refusals stay, catching the
  same hole at different depths.

- **A director cannot unstick a rejected mission.** `POST /replan` is owner-only, which is narrower
  than M6 allows, because 05's API table says so and having another go at a plan is planning work.
  If the owning lead is unavailable, a director's only lever is closing the mission as `REJECTED`.
  Revisit if that turns out to matter. Implemented in
  [05](features/05-mission-approval.md).
- **Match run auditability.** Suggestions are transient, so there is no record of why a crew
  member was picked. Persisting match runs would give that, at the cost of another entity.
- **Organisation settings.** The spec says Directors manage them but never says what they are, so
  `Organisation` holds only a name.
