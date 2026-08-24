# 08 — Dashboard

## Purpose

Give each role the screen it needs on login: a crew member sees the missions they are on, a
mission lead sees the missions they run, a director sees the whole organisation. Everything on
every dashboard is scoped to the caller's organisation.

Delivers `product.md` §3 *Dashboard*. Almost entirely a read model composed from earlier features —
it introduces no new entity and no new business rule.

## Functional requirements

- **FR-1** `GET /api/dashboard/crew` returns the caller's assignments split into pending offers,
  active missions and completed missions.
- **FR-2** `GET /api/dashboard/mission-lead` returns the missions the caller owns, grouped by
  status, and highlights those needing attention.
- **FR-3** `GET /api/dashboard/director` returns organisation-wide mission counts, the missions
  awaiting the caller's approval, and a small set of metrics.
- **FR-4** Each endpoint is restricted to its own role.
- **FR-5** A mission lead's dashboard flags missions that are rejected and awaiting revision, and
  approved missions not yet fully staffed.
- **FR-6** A director's dashboard lists missions in `PENDING_APPROVAL` first — the only items
  actually requiring them to act.
- **FR-7** Each dashboard returns a bounded number of items, with links to the full lists in
  [04](04-mission-management.md) and [07](07-crew-assignment.md) rather than paging inside the
  dashboard itself.
- **FR-8** An empty dashboard is a valid 200 response.

## Non-functional requirements

- **NFR-1** Each dashboard is a single request and a bounded number of queries. Its cost must not
  grow with the number of missions in the organisation.
- **NFR-2** Responds within 1 second for an organisation with 1,000 missions.
- **NFR-3** Read-only. No dashboard request writes anything.
- **NFR-4** Three endpoints rather than one polymorphic endpoint, so the generated TypeScript
  client gets three concrete types instead of a discriminated union.
- **NFR-5** Composed from the published interfaces of `mission`, `assignment` and `identity`;
  the dashboard owns no data.
- **NFR-6** Timestamps are UTC; the frontend renders them in the viewer's timezone.

## Business rules

- **BR-1** Every dashboard shows only the caller's organisation — *enforces T1*.
- **BR-2** A crew member sees only their own assignments. There is no view of other crew —
  *enforces A6's spirit and T2*.
- **BR-3** A mission lead sees only missions they own — *enforces M6's visibility half*.
- **BR-4** A director sees every mission in the organisation, matching their full visibility in
  `product.md` §2.
- **BR-5** Each role reaches only its own dashboard endpoint; the other two return 403.
- **BR-6** "Completed" for a crew member means an `ACCEPTED` assignment on a mission closed as
  `COMPLETED` — the same derivation as assignment history, not a separate definition.
- **BR-7** "Active" for a crew member means an `ACCEPTED` assignment on an `ACTIVE` mission.
- **BR-8** "Pending" for a crew member means an `OFFERED` assignment awaiting their response.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/dashboard/crew` | CREW_MEMBER | Own assignments |
| GET | `/api/dashboard/mission-lead` | MISSION_LEAD | Owned missions |
| GET | `/api/dashboard/director` | DIRECTOR | Organisation overview |

**GET `/api/dashboard/crew`**

- Response 200 —
  - `pendingOffers[]` — `assignmentId`, `mission` (`id`, `name`, `startsAt`, `endsAt`),
    `requirementTitle`, `offeredAt`
  - `activeMissions[]` — `assignmentId`, `mission`, `requirementTitle`
  - `upcomingMissions[]` — accepted, on missions not yet started
  - `completedCount` — total missions completed
  - `recentlyCompleted[]` — the last five

**GET `/api/dashboard/mission-lead`**

- Response 200 —
  - `counts` — missions owned, by status
  - `needsAttention[]` — rejected missions, and approved missions not fully staffed; each with
    `missionId`, `name`, `status`, `reason` (`REJECTED` | `UNDERSTAFFED`)
  - `awaitingApproval[]` — the caller's missions in `PENDING_APPROVAL`
  - `activeMissions[]` — `id`, `name`, `startsAt`, `endsAt`, `fullyStaffed`

**GET `/api/dashboard/director`**

- Response 200 —
  - `awaitingDecision[]` — missions in `PENDING_APPROVAL`, oldest first; `id`, `name`,
    `missionLead`, `submittedAt`
  - `counts` — all organisation missions, by status
  - `metrics` — `activeMissionCount`, `crewMemberCount`, `crewCurrentlyAssigned`,
    `crewAvailable`, `missionsCompletedLast90Days`

`crewAvailable` is derived, not stored: crew members with no `ACCEPTED` assignment on a
non-`CLOSED` mission overlapping today. It follows the same rule as availability in
[06](06-crew-matching.md) rather than defining a second one.

## Acceptance criteria

- [ ] A crew member with a pending offer sees it under `pendingOffers`.
- [ ] Accepting an offer moves it from `pendingOffers` to `upcomingMissions`.
- [ ] Starting the mission moves it from `upcomingMissions` to `activeMissions`.
- [ ] Closing the mission as `COMPLETED` moves it into the completed count.
- [ ] Closing a mission as `ABORTED` does **not** count it as completed.
- [ ] A crew member's dashboard never shows another crew member's assignment.
- [ ] A mission lead sees only their own missions.
- [ ] A rejected mission appears under `needsAttention` with reason `REJECTED`.
- [ ] An approved but under-staffed mission appears with reason `UNDERSTAFFED`.
- [ ] A fully staffed approved mission does not appear under `needsAttention`.
- [ ] A director sees missions from every mission lead in the organisation.
- [ ] A director never sees a mission from another organisation.
- [ ] `awaitingDecision` lists exactly the missions in `PENDING_APPROVAL`, oldest first.
- [ ] `crewAvailable` plus `crewCurrentlyAssigned` equals `crewMemberCount`.
- [ ] Each role receives 403 from the other two dashboard endpoints.
- [ ] A new organisation with no missions returns 200 with empty collections.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| No or invalid token | 401 | `urn:mission-control:unauthenticated` |
| Role does not match the endpoint | 403 | `urn:mission-control:forbidden` |

Dashboards take no parameters and address no specific resource, so 400 and 404 do not arise. An
empty dashboard is a 200 with empty collections — a user with nothing assigned is a normal state,
not an error.
