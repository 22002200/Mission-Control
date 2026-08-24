# 06 — Crew Matching

## Purpose

For a given crew requirement, produce a ranked list of crew members who could fill it. Eligibility
is a hard filter on availability and mandatory skills; ranking then prefers the crew member who
fits the requirement *most closely* rather than the one who is most qualified overall.

There are two ways in. **Match All** drafts candidates for every open seat on a mission in one
call. **Match** and **Rematch** work a single requirement at a time, returning a short ranked list
that can be re-run for the next batch when the lead does not like what came back.

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
- **FR-6** The per-requirement endpoint accepts a `limit` (default 3, max 10).
- **FR-7** Results are computed per request and never persisted.
- **FR-8** An empty result is a valid answer, not an error.
- **FR-9** `GET /api/missions/{missionId}/matches` returns, for every requirement on the mission,
  the top-ranked candidates for its open seats.
- **FR-10** Open seats are `requiredCount` minus accepted minus offered, floored at zero. A
  requirement with no open seats is still listed, with an empty candidate list, so one response
  renders the whole mission.
- **FR-11** No crew member appears more than once in a Match All response.
- **FR-12** The per-requirement endpoint accepts `exclude`, a set of crew member ids to leave out.
  Rematch is that endpoint called with the ids the lead has already seen or already drafted.
- **FR-13** Exclusions are applied before the limit, so a rematch returns a full list whenever
  enough candidates remain.
- **FR-14** The per-requirement response reports how many eligible candidates are still unseen, so
  a client can tell "there is nobody else" apart from "the list came back short".

## Non-functional requirements

- **NFR-1** Scoring is deterministic: identical data, at a given instant, yields an identical
  ordering. The load window in BR-8 is derived from the organisation's own data and from the clock,
  so it moves as missions complete — which a rolling window always did.
- **NFR-2** Candidate skills and assignment counts are fetched in bulk, once per *request* rather
  than once per requirement. No per-candidate query.
- **NFR-3** Responds within 2 seconds for an organisation of up to 500 crew members.
- **NFR-4** Purely read-only: no writes, no state change. Match All drafts a crew, it does not
  offer anyone anything.
- **NFR-5** The `matching` module reads `crew`, `mission`, `skill` and `assignment` through their
  published interfaces, and owns no data of its own.
- **NFR-6** Scoring constants are configuration, not literals buried in the algorithm.
- **NFR-7** Match All issues a fixed number of queries regardless of how many requirements the
  mission has or how many crew members the organisation has — see [Query budget](#query-budget).
- **NFR-8** The median mission duration behind BR-8 is read through `mission`'s published
  interface, since `mission` owns the dates. Once per request, never per candidate.

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
  window = k x medianCompletedMissionDuration(organisation)   k configurable, default 6
         = 365 days                                           if the organisation has
                                                              completed no missions

  score  = skillScore                        0..1
         + 0.1  x completedMissions          capped at +0.3
         - 0.05 x recentAssignments          capped at -0.3
  ```

  `completedMissions` — `ACCEPTED` assignments on missions closed as `COMPLETED`.
  `recentAssignments` — `ACCEPTED` assignments whose mission `startsAt` falls within the last
  `window` or in the future.
  `medianCompletedMissionDuration` — the median of `endsAt` minus `startsAt` across the
  organisation's missions closed as `COMPLETED`.

- **BR-9** Ties may be broken arbitrarily. Sort by `crewMemberId` as a secondary key so repeated
  calls return the same order, which keeps the UI from reshuffling between requests.

### Match All and Rematch

- **BR-10** Match All serves requirements **most-constrained-first**: ascending by how many
  candidates pass the hard filters, tie-broken by `requirementId` so the order is stable (NFR-1).
  Each requirement in turn takes the highest-ranked candidates not already claimed by an earlier
  one, so a candidate topping two lists is drafted onto the one with fewer alternatives and the
  other falls through to its next-best.

  Greedy, not an optimal allocation. It is one pass, it is explainable to the lead in a sentence,
  and the lead overrides it seat by seat anyway — a globally optimal draft would be a much harder
  promise to keep, and moving one person would break it.

- **BR-11** `exclude` names crew member ids. Ids that are unknown, belong to another organisation,
  or are already ineligible are **ignored rather than rejected** — a client holding a stale draft
  should get a shorter list, not a 400. More than 50 ids is a 400.

  Exclusion is client-supplied because match results are transient (FR-7): nothing server-side
  knows who was suggested a moment ago. This is not a rejection log the client has to keep. The set
  it sends is the crew currently drafted onto the mission, plus the shortlist already on screen —
  both of which it is holding in order to render them.

  Positions were the alternative — "give me items 4 to 6". They are rejected because a position
  cannot say *who*. It cannot exclude a candidate drafted onto a different requirement, since the
  server has no idea that draft exists; and it drifts silently, because a candidate who becomes
  ineligible between two calls shifts everyone below them up by one, so the next page skips
  somebody and nothing says so.

### Why the window is the organisation's own tempo

A fixed 365 days means two different things in two organisations. One running three-day sorties
cycles a crew member through dozens of assignments a year; one running six-month expeditions
manages two. The same `recentAssignments` count describes an overworked crew member in the second
organisation and an unremarkable one in the first, and a fixed window cannot tell them apart.

Three choices inside BR-8 worth recording:

- **A multiple of the median, not the median itself.** A window one mission long would find almost
  nobody recently loaded and the penalty would stop discriminating between candidates. `k = 6`
  reads as "roughly the last half-dozen missions' worth of time", and it is configuration, so an
  organisation that disagrees can say so.
- **Median, and therefore no floor or ceiling.** A mean is dragged a long way by one freak
  three-year mission, which is exactly what a clamp would have been guarding against. A median is
  not, so the clamp has nothing left to do and is not there.
- **Small samples are volatile, and that is accepted.** With one or two completed missions the
  median *is* that mission's duration. Only the organisation's own history can inform this, it
  converges quickly, and the penalty is capped at −0.3 either way — so the cost of a bad early
  window is a third of a point on a term that is already secondary.

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

BR-4, `recentAssignments` and the open-seat count all read assignment data. Before
[07](07-crew-assignment.md) exists there is none, so the penalty is zero, nothing is excluded, and
`openSeats` equals `requiredCount` — Match All drafts a full crew, which is the correct answer
rather than a stub.

The window is not in that position. Missions can already be closed as `COMPLETED` in
[04](04-mission-management.md), so the median has real data to work from from the start.

One interface addition falls out of `openSeats`.
[`mission.api.StaffingReadModel`](../../backend/src/main/java/com/missioncontrol/mission/api/StaffingReadModel.java)
publishes accepted counts per requirement; open seats need offered counts too, since A2 caps
`OFFERED` plus `ACCEPTED` at `requiredCount`. Add a bulk offered-count method in the same shape —
keyed by requirement id, absent meaning zero. `UnstaffedReadModel` answers with an empty map until
07, which is what makes the pre-07 behaviour above fall out rather than being special-cased.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/missions/{missionId}/matches` | owner MISSION_LEAD or DIRECTOR | A draft crew for every open seat |
| GET | `/api/missions/{missionId}/requirements/{requirementId}/matches` | owner MISSION_LEAD or DIRECTOR | Ranked candidates for one requirement |

**GET `/api/missions/{missionId}/matches`**

- Query — none. Open seats are the limit, and a draft is the first thing a lead asks for, so there
  is nothing yet to exclude. Refining a draft is the per-requirement endpoint's job.
- Response 200 — `missionId`, `requirements[]`
- Each requirement — `requirementId`, `title`, `requiredCount`, `acceptedCount`, `offeredCount`,
  `openSeats`, `candidates[]`
- Errors — 403, 404

**GET `/api/missions/{missionId}/requirements/{requirementId}/matches`**

- Query — `limit` (default 3, max 10), `exclude` (crew member ids, repeatable, at most 50)
- Response 200 — `requirementId`, `requiredCount`, `acceptedCount`, `offeredCount`, `openSeats`,
  `remainingCount`, `candidates[]`
- Errors — 400, 403, 404

`remainingCount` is how many eligible candidates were neither excluded nor returned. Zero means
Rematch has nothing further to show — the one thing a client cannot work out for itself, and which
would otherwise surface as an unexplained short list.

**Each candidate**, on both endpoints —

- `crewMemberId`, `fullName`
- `score` (number, 3 decimal places)
- `breakdown`:
  - `skillScore`
  - `experienceBonus`, `completedMissions`
  - `loadPenalty`, `recentAssignments`
- `skills[]` — `skillName`, `required`, `actual`, `mandatory`, `contribution`
- `shortfalls[]` — preferred skills held below the minimum or absent

Excluded crew members do not appear in the response. Explaining every ineligible person would make
the payload mostly noise.

### Query budget

What NFR-7 commits to. Constant in both requirement count and crew count:

1. Mission, requirements and required skills — one fetch join, as
   [04](04-mission-management.md) already does
2. Median completed-mission duration for the organisation
3. Accepted and offered counts per requirement
4. Crew members in the organisation
5. Their crew skills, in bulk
6. `ACCEPTED` assignments overlapping the mission window — BR-3
7. Non-terminal assignments on this mission — BR-4
8. Completed-mission counts per crew member — the experience term
9. Recent-assignment counts per crew member inside the window — the load term
10. Skill names for every skill the mission's requirements mention, in one lookup
11. Display names for every candidate, in one lookup

Eleven or so queries for a whole mission, against eleven *per requirement* if a client looped the
per-requirement endpoint instead. That is the argument for Match All being an endpoint rather than
a loop in the frontend. Step 9 needs the window, so it follows step 2; the rest are independent.

Scoring is then in memory: requirements x candidates x skills. At 500 crew, ten requirements and
five skills each that is roughly 25,000 operations, so NFR-3's two seconds are not in doubt.

The median is an ordered-set aggregate (`percentile_cont`) with no JPQL equivalent, so it is a
native query; taking it over epoch seconds rather than an interval keeps the mapping trivial. It is
deliberately **not** cached to start with — one aggregate over a small set is cheaper than a cache
whose staleness someone has to reason about. Revisit only if profiling asks for it.

## Acceptance criteria

- [x] A requirement with mandatory skills excludes everyone below the minimum.
- [x] A crew member with an overlapping accepted assignment is excluded.
- [x] A crew member whose overlapping assignment is on a `CLOSED` mission is **not** excluded.
- [x] A crew member already offered or accepted on this mission is excluded.
- [x] Crew from another organisation never appear.
- [x] Given the worked example above, the order is A, C, B and D is absent.
- [x] A candidate exactly at a mandatory minimum outranks an otherwise identical over-qualified one.
- [x] A candidate above a preferred minimum outranks an otherwise identical one below it.
- [x] A requirement with no skills returns every available crew member at `skillScore` 1.0.
- [x] More completed missions raises the score, to a maximum of +0.3.
- [x] More assignments inside the organisation's derived window lowers the score, to a minimum
      of −0.3.
- [x] An organisation that has completed no missions falls back to the 365-day window.
- [x] Two organisations with identical crew data but different median mission durations produce
      different load penalties.
- [x] One freak multi-year completed mission does not move the window — median, not mean.
- [x] Two calls with unchanged data return identical ordering.
- [x] No candidates is a 200 with an empty list, not a 404.
- [x] A mission lead who does not own the mission receives 403.
- [x] Calling either endpoint writes nothing to the database.
- [x] Match All returns `openSeats` candidates per requirement, not one.
- [x] A requirement whose seats are all accepted or offered comes back with an empty candidate
      list, not an error.
- [x] No crew member appears twice in a Match All response.
- [x] A crew member top-ranked on two requirements is drafted onto the more constrained one, and
      the other requirement gets its next-best.
- [x] Match All on a mission with no requirements is a 200 with an empty list.
- [x] Two Match All calls on unchanged data return an identical draft.
- [x] `exclude` removes the named candidates and still returns `limit` candidates when enough
      remain.
- [x] `exclude` containing an unknown id, or one from another organisation, is ignored rather than
      rejected.
- [x] `exclude` with more than 50 ids is a 400.
- [x] `remainingCount` reaches zero once every eligible candidate has been returned or excluded.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| `limit` out of range | 400 | `urn:mission-control:validation-failed` |
| `exclude` holds more than 50 ids | 400 | `urn:mission-control:validation-failed` |
| Caller is not the owner or a director | 403 | `urn:mission-control:forbidden` |
| Mission or requirement absent, or another organisation's | 404 | `urn:mission-control:not-found` |
| Requirement belongs to a different mission | 404 | `urn:mission-control:not-found` |

Matching is available in every mission status. Running it while a mission is still in `PLAN` is
useful for sizing a plan before submitting it, and it changes nothing.
