# 07 — Crew Assignment

## Purpose

Turn a match suggestion into a real commitment. A Mission Lead offers a place on a mission; the
crew member accepts or declines; the lead can withdraw a place before the mission closes. Accepting
is what consumes a crew member's availability and builds their assignment history.

Delivers the crew-selection part of `product.md` §3 *Mission Management* and the Crew Member
abilities in §2.

## Functional requirements

- **FR-1** `POST /api/missions/{id}/assignments` offers a place to a crew member against a specific
  requirement, creating an `OFFERED` assignment.
- **FR-2** `GET /api/missions/{id}/assignments` lists a mission's assignments, grouped by
  requirement.
- **FR-3** `GET /api/assignments/me` lists the caller's own assignments.
- **FR-4** `POST /api/assignments/{id}/accept` moves `OFFERED` to `ACCEPTED`.
- **FR-5** `POST /api/assignments/{id}/decline` moves `OFFERED` to `DECLINED`.
- **FR-6** `POST /api/assignments/{id}/withdraw` moves `OFFERED` or `ACCEPTED` to `WITHDRAWN`.
- **FR-7** Declining and withdrawing free the place, so someone else can be offered it.
- **FR-8** Closing a mission withdraws its outstanding offers and leaves accepted assignments as
  they are.
- **FR-9** `GET /api/assignments/me` filters by status and by whether the mission is current,
  upcoming or finished.

## Non-functional requirements

- **NFR-1** Accepting is a single transaction: the schedule-conflict check, the capacity check and
  the status change either all apply or none do.
- **NFR-2** Two crew members accepting the last place on a requirement concurrently must result in
  exactly one success. Enforce with a lock or an optimistic retry — a read-then-write without
  either will oversubscribe under load.
- **NFR-3** One crew member's two overlapping acceptances arriving concurrently must likewise
  produce exactly one success.
- **NFR-4** Listing a mission's assignments is a bounded number of queries regardless of crew count.
- **NFR-5** `assignment` may depend on `mission`; `mission` must never depend on `assignment`.
  Staffing counts flow back through a read model this module publishes — see
  [architecture.md](../architecture.md#module-ownership).
- **NFR-6** Every status change records its timestamp in UTC. `offeredAt` is set on creation and
  `respondedAt` when the assignment leaves `OFFERED` - which includes a withdrawal, so
  `respondedAt` reads as 'when this was settled' rather than only 'when the crew member replied'.

## Business rules

- **BR-1** Offers may only be made while the mission is `APPROVED` — *enforces A1*.
- **BR-2** `OFFERED` plus `ACCEPTED` for a requirement never exceeds `requiredCount` —
  *enforces A2*.
- **BR-3** Accepting is refused if the crew member already has an `ACCEPTED` assignment, on a
  mission that is not `CLOSED`, whose dates overlap this one — *enforces A3*.
- **BR-4** **That overlap check runs when the offer is accepted, not when it is made** —
  *enforces A4*. Offers never reserve a crew member, so two mission leads may each legitimately
  offer the same person for clashing dates. The second acceptance is what must fail. This cannot
  be a database uniqueness constraint; it is a check inside the accepting transaction.
- **BR-5** A crew member has at most one non-terminal assignment per mission — *enforces A5*.
- **BR-6** Only the crew member named on an assignment may accept or decline it. Not the mission
  lead, not a director — *enforces A6*.
- **BR-7** Permitted transitions: `OFFERED` → `ACCEPTED`, `DECLINED` or `WITHDRAWN`; `ACCEPTED` →
  `WITHDRAWN`. `DECLINED` and `WITHDRAWN` are terminal — *enforces A7*.
- **BR-8** Closing a mission withdraws its `OFFERED` assignments and leaves `ACCEPTED` ones
  untouched — *enforces A8*. Withdrawing accepted assignments would erase the crew member's
  history, which is derived from exactly those rows.
- **BR-9** Withdrawal is the owning mission lead's alone; accept and decline are the crew member's.
  The two never overlap, and neither side can do the other's half. A director may see every
  assignment on every mission but withdraws none - their lever on a mission they disagree with is
  closing it, the same narrowing `POST /replan` already makes. A crew member who has accepted is
  assigned: releasing them is the lead's decision, not theirs.
- **BR-10** The offered crew member and the mission are in the same organisation — *enforces T2*.
- **BR-11** Withdrawing crew from an `ACTIVE` mission does not revert its status. M11 is a
  precondition of starting, not a standing invariant.

## API

| Method | Path                             | Role | Purpose |
| --- |----------------------------------| --- | --- |
| POST | `/api/missions/{id}/assignments` | owner MISSION_LEAD | Offer a place |
| GET | `/api/missions/{id}/assignments` | owner or DIRECTOR | Mission's assignments |
| GET | `/api/assignments/me`            | CREW_MEMBER | Own assignments |
| POST | `/api/assignments/{id}/accept`   | CREW_MEMBER (self) | `OFFERED` → `ACCEPTED` |
| POST | `/api/assignments/{id}/decline`  | CREW_MEMBER (self) | `OFFERED` → `DECLINED` |
| POST | `/api/assignments/{id}/withdraw` | owner MISSION_LEAD | → `WITHDRAWN` |

**POST `/api/missions/{id}/assignments`**

- Request — `crewRequirementId` (required), `crewMemberId` (required)
- Response 201 — `id`, `status: "OFFERED"`, `crewMember`, `crewRequirementId`, `offeredAt`
- Errors — 400, 403, 404, 409 mission not `APPROVED`, 409 requirement full,
  409 crew member already on this mission

**GET `/api/missions/{id}/assignments`**

- Query — `status` (optional)
- Response 200 — `requirements[]`, each with `requirementId`, `title`, `requiredCount`,
  `acceptedCount`, `assignments[]` of `id`, `crewMember` (`id`, `fullName`), `status`,
  `offeredAt`, `respondedAt`

`crewMember.id` is the **crew profile id**, the same id `CandidateResponse.crewMemberId` carries in
[06](06-crew-matching.md), so a suggestion can be offered without a second lookup. Every
requirement on the mission appears, including ones nobody has been offered yet - an empty line is
the one a lead most needs to see.

**GET `/api/assignments/me`**

- Query — `status` (optional), `timeframe` (`CURRENT` | `UPCOMING` | `PAST`, optional), `page`,
  `size`
- Response 200 — paged list of `id`, `status`, `offeredAt`, `respondedAt`,
  `mission` (`id`, `name`, `status`, `startsAt`, `endsAt`), `requirementTitle`

`timeframe` is measured against the **mission's dates**, not its status: `CURRENT` is
`startsAt <= now <= endsAt`, `UPCOMING` is `startsAt > now`, `PAST` is `endsAt < now`. Dates rather
than status because a mission nobody remembered to close should still read as finished, and because
a crew member asking "what is next" means the calendar.

**POST `/api/assignments/{id}/accept`**

- Request — none
- Response 200 — the assignment, `status: "ACCEPTED"`
- Errors — 403 not the named crew member, 404, 409 not `OFFERED`, 409 schedule conflict,
  409 requirement already full

**POST `/api/assignments/{id}/decline`**

- Request — none
- Response 200 — the assignment, `status: "DECLINED"`
- Errors — 403, 404, 409 not `OFFERED`

**POST `/api/assignments/{id}/withdraw`**

- Request — none
- Response 200 — the assignment, `status: "WITHDRAWN"`
- Errors — 403 not the owning mission lead, 404, 409 already terminal

## Acceptance criteria

- [ ] A mission lead can offer a place on an `APPROVED` mission.
- [ ] Offering on a `PLAN` or `PENDING_APPROVAL` mission is rejected with 409.
- [ ] Offering beyond `requiredCount` is rejected with 409.
- [ ] Offering the same crew member twice on one mission is rejected with 409.
- [ ] Offering a crew member from another organisation returns 404.
- [ ] A crew member sees their offer in `GET /api/assignments/me`.
- [ ] The named crew member can accept, and the requirement's accepted count rises.
- [ ] A different crew member attempting to accept that assignment receives 403.
- [ ] A mission lead attempting to accept on a crew member's behalf receives 403.
- [ ] **Two mission leads may both offer the same crew member for overlapping dates; neither offer
      is rejected.**
- [ ] **The first of those offers accepts successfully; the second acceptance is rejected with 409
      schedule conflict.**
- [ ] An overlapping assignment on a `CLOSED` mission does not block acceptance.
- [ ] A crew member may accept two assignments whose mission dates do not overlap.
- [ ] Declining frees the place and another crew member can then be offered it.
- [ ] A declined assignment cannot be accepted afterwards.
- [ ] A mission lead can withdraw an accepted assignment; the accepted count falls.
- [ ] Withdrawing from an `ACTIVE` mission leaves it `ACTIVE`.
- [ ] Closing a mission withdraws its outstanding offers.
- [ ] Closing a mission leaves accepted assignments `ACCEPTED`.
- [ ] After a mission closes as `COMPLETED`, its accepted assignments appear in crew history.
- [ ] Two concurrent acceptances of the last place yield one success and one 409.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| Missing or invalid ids | 400 | `urn:mission-control:validation-failed` |
| Caller is not the owning lead (offer, withdraw) - a director included | 403 | `urn:mission-control:forbidden` |
| Caller is not the named crew member (accept, decline) | 403 | `urn:mission-control:forbidden` |
| Assignment, mission or crew member absent or another organisation's | 404 | `urn:mission-control:not-found` |
| Mission not `APPROVED` | 409 | `urn:mission-control:invalid-transition` |
| Requirement already at `requiredCount` | 409 | `urn:mission-control:requirement-full` |
| Crew member already has a non-terminal assignment on this mission | 409 | `urn:mission-control:duplicate-assignment` |
| Accepting would overlap an existing accepted mission | 409 | `urn:mission-control:schedule-conflict` |
| Assignment is already terminal | 409 | `urn:mission-control:invalid-transition` |

`urn:mission-control:schedule-conflict` names the conflicting mission and its dates, so the crew
member can see what they are already committed to. This is the error mission leads will hit most,
and it is a normal outcome rather than a fault — it is the direct consequence of offers not
reserving anyone.
