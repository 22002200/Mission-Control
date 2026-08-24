# 06 — Crew Matching

## Purpose

For a given crew requirement, produce a ranked list of crew members who could fill it. Eligibility
is a hard filter on availability and mandatory skills; ranking then prefers the crew member who
fits the requirement *most closely* rather than the one who is most qualified overall.

Delivers `product.md` §3 *Crew Matching*. Read-only — it suggests, it does not assign. Acting on a
suggestion is [07](07-crew-assignment.md).

## Functional requirements

- **FR-1** `GET /api/missions/{missionId}/requirements/{requirementId}/matches` returns ranked
  candidates for that requirement.
- **FR-2** Candidates failing any hard filter are excluded entirely, not ranked last.
- **FR-3** Each candidate carries its score and a breakdown explaining it.
- **FR-4** The breakdown names which skills matched, which fell short, and the experience and load
  contributions — so a mission lead can see why the ranking came out as it did.
- **FR-5** Results are ordered by score descending.
- **FR-6** The endpoint accepts a `limit` (default 20, max 100).
- **FR-7** Results are computed per request and never persisted.
- **FR-8** An empty result is a valid answer, not an error.

## Non-functional requirements

- **NFR-1** Scoring is deterministic: identical data yields an identical ordering.
- **NFR-2** Candidate skills and assignment counts are fetched in bulk. No per-candidate query.
- **NFR-3** Responds within 2 seconds for an organisation of up to 500 crew members.
- **NFR-4** Purely read-only: no writes, no state change.
- **NFR-5** The `matching` module reads `crew`, `mission`, `skill` and `assignment` through their
  published interfaces, and owns no data of its own.
- **NFR-6** Scoring constants are configuration, not literals buried in the algorithm.

## Business rules

### Hard filters

A crew member is a candidate only if **all** hold:

- **BR-1** They are in the caller's organisation — *enforces T1, T2*.
- **BR-2** They hold every **mandatory** skill at or above its `minimumProficiency`.
- **BR-3** They have no `ACCEPTED` assignment, on a mission that is not `CLOSED`, whose dates
  overlap this mission's `startsAt`–`endsAt` — *enforces A3*.
- **BR-4** They have no non-terminal assignment already on this mission — *enforces A5*.

### Scoring

- **BR-5** Mandatory skills are scored to prefer the **least** over-qualified candidate:

  ```
  excess = proficiency - minimumProficiency          >= 0, guaranteed by BR-2
  fit    = 1 - excess / (5 - minimumProficiency)     1.0 at exactly the minimum
                                                     1.0 when minimumProficiency = 5
  ```

- **BR-6** Preferred skills are scored to prefer the **most** capable candidate, with partial
  credit below the minimum:

  ```
  met = 1                       if proficiency >= minimumProficiency
      = proficiency / minimum   if below the minimum
      = 0                       if the skill is absent
  ```

- **BR-7** The two combine into one weighted average across every required skill:

  ```
  skillScore = ( SUM_mandatory weight x fit + SUM_preferred weight x met ) / SUM_all weight
  ```

  `weight` defaults to 1. A requirement listing no skills scores 1.0.

- **BR-8** Experience and recent load adjust the result:

  ```
  score = skillScore                          0..1
        + 0.1  x completedMissions            capped at +0.3
        - 0.05 x recentAssignments            capped at -0.3
  ```

  `completedMissions` — `ACCEPTED` assignments on missions closed as `COMPLETED`.
  `recentAssignments` — `ACCEPTED` assignments whose mission `startsAt` falls within the last
  365 days or in the future.

- **BR-9** Ties may be broken arbitrarily. Sort by `crewMemberId` as a secondary key so repeated
  calls return the same order, which keeps the UI from reshuffling between requests.

### Why mandatory and preferred pull in opposite directions

A mandatory `minimumProficiency` is the job's actual bar. Exceeding it buys the mission nothing —
but it costs the organisation something real. Assign a 5/5 specialist to a requirement needing 3/5
and they become unavailable for the mission that genuinely needs 5/5, which the 3/5 crew member
could never have covered. Over-qualification is an opportunity cost, so the score penalises it.

Preferred skills are the opposite: they express desirable extras rather than a threshold, so more
is better, and falling slightly short still counts for something.

Worked example — `EVA` (mandatory, min 3) and `Robotics` (preferred, min 4), both weight 1:

| Candidate | EVA | Robotics | fit / met | skillScore | Outcome |
| --- | --- | --- | --- | --- | --- |
| A | 3 | 4 | 1.00 / 1.00 | **1.00** | Exact fit on both — ranked first |
| C | 3 | 2 | 1.00 / 0.50 | 0.75 | Short on a preferred skill, partial credit |
| B | 5 | 4 | 0.00 / 1.00 | 0.50 | Over-qualified on the mandatory skill |
| D | 2 | 5 | — | — | Excluded: fails the mandatory minimum |

**A known tension.** The experience bonus rewards veterans while the mandatory term penalises
over-qualification, so an experienced specialist gains on one and loses on the other. The ±0.3
caps keep both secondary to `skillScore`, which spans a full 0–1, so closeness of fit stays the
dominant signal. This is deliberate, not an oversight.

### Dependency note

`recentAssignments` and BR-4 both read assignment data. Before [07](07-crew-assignment.md) exists
there are no assignments, so the penalty is zero and nothing is excluded — matching still returns
correct rankings on skills and availability alone.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/missions/{missionId}/requirements/{requirementId}/matches` | owner MISSION_LEAD or DIRECTOR | Ranked candidates |

- Query — `limit` (default 20, max 100)
- Response 200 — `requirementId`, `requiredCount`, `acceptedCount`, `candidates[]`
- Each candidate —
  - `crewMemberId`, `fullName`
  - `score` (number, 3 decimal places)
  - `breakdown`:
    - `skillScore`
    - `experienceBonus`, `completedMissions`
    - `loadPenalty`, `recentAssignments`
  - `skills[]` — `skillName`, `required`, `actual`, `mandatory`, `contribution`
  - `shortfalls[]` — preferred skills held below the minimum or absent
- Errors — 403, 404

Excluded crew members do not appear in the response. Explaining every ineligible person would make
the payload mostly noise.

## Acceptance criteria

- [ ] A requirement with mandatory skills excludes everyone below the minimum.
- [ ] A crew member with an overlapping accepted assignment is excluded.
- [ ] A crew member whose overlapping assignment is on a `CLOSED` mission is **not** excluded.
- [ ] A crew member already offered or accepted on this mission is excluded.
- [ ] Crew from another organisation never appear.
- [ ] Given the worked example above, the order is A, C, B and D is absent.
- [ ] A candidate exactly at a mandatory minimum outranks an otherwise identical over-qualified one.
- [ ] A candidate above a preferred minimum outranks an otherwise identical one below it.
- [ ] A requirement with no skills returns every available crew member at `skillScore` 1.0.
- [ ] More completed missions raises the score, to a maximum of +0.3.
- [ ] More assignments in the last 365 days lowers the score, to a minimum of −0.3.
- [ ] Two calls with unchanged data return identical ordering.
- [ ] No candidates is a 200 with an empty list, not a 404.
- [ ] A mission lead who does not own the mission receives 403.
- [ ] Calling the endpoint writes nothing to the database.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| `limit` out of range | 400 | `urn:mission-control:validation-failed` |
| Caller is not the owner or a director | 403 | `urn:mission-control:forbidden` |
| Mission or requirement absent, or another organisation's | 404 | `urn:mission-control:not-found` |
| Requirement belongs to a different mission | 404 | `urn:mission-control:not-found` |

Matching is available in every mission status. Running it while a mission is still in `PLAN` is
useful for sizing a plan before submitting it, and it changes nothing.
