# Feature Specs

Mission Control broken into incrementally buildable features. Each spec can be built and demoed on
its own, on top of the ones before it.

Read [`../product.md`](../product.md), [`../architecture.md`](../architecture.md) and
[`../data-model.md`](../data-model.md) first — these specs assume all three.

## Build order

| # | Spec | Delivers | Depends on |
| --- | --- | --- | --- |
| 01 | [Seed data](01-seed-data.md) | Organisations, users, crew profiles, skills | — |
| 02 | [Authentication](02-authentication.md) | Login, logout, tenant and role enforcement | 01 |
| 03 | [Skill catalogue](03-skill-catalogue.md) | Org-scoped skill CRUD | 02 |
| 04 | [Mission management](04-mission-management.md) | Missions, crew requirements, start, close | 02, 03 |
| 05 | [Mission approval](05-mission-approval.md) | Submit, approve, reject, return to plan | 04 |
| 06 | [Crew matching](06-crew-matching.md) | Ranked crew suggestions | 04, 03, 01 |
| 07 | [Crew assignment](07-crew-assignment.md) | Offer, accept, decline, withdraw | 05, 06 |
| 08 | [Dashboard](08-dashboard.md) | Three role-specific views | 04, 07 |

```
01 seed ──▶ 02 auth ──┬──▶ 03 skills ──┬──▶ 04 missions ──┬──▶ 05 approval ──┐
                      │                │                  │                  │
                      └────────────────┘                  ├──▶ 06 matching ──┤
                                                          │                  ▼
                                                          │            07 assignment
                                                          │                  │
                                                          └──────────────────┴──▶ 08 dashboard
```

**06 before 07 is a soft ordering.** Matching reads assignment data for its load penalty and its
"already offered" exclusion, so it is only fully correct once 07 exists. It is still useful before
then — with no assignments, nothing is excluded and no penalty applies.

## Spec structure

Every spec has the same seven sections: Purpose, Functional requirements (`FR-n`),
Non-functional requirements (`NFR-n`), Business rules (`BR-n`), API, Acceptance criteria, Error
handling.

Where a `BR-n` enforces an invariant from [`../data-model.md`](../data-model.md#invariants) it
says so — *"enforces M3"* — which keeps the specs and the model tied together. Every invariant in
the model is enforced by at least one spec.

Rules without a citation are the ones the model does not speak to: who may call what, how the
matching algorithm scores, and what the dashboard read models mean. Those belong to the spec, not
to the data model.

## Conventions

These apply to every spec and are not repeated in each one.

**Errors** are RFC 9457 `application/problem+json`, produced by
[`GlobalExceptionHandler`](../../backend/src/main/java/com/missioncontrol/platform/GlobalExceptionHandler.java)
for anything raised in a controller, and by `ProblemAuthenticationEntryPoint` /
`ProblemAccessDeniedHandler` for 401s and 403s detected in the security filter chain, which never
reach an `@RestControllerAdvice`. Both paths build their bodies from the same helper so a client
cannot tell which one answered.

| Status | Meaning |
| --- | --- |
| 400 | Malformed or invalid request body or parameters |
| 401 | Missing, expired or revoked token |
| 403 | Authenticated, but the role is not permitted this action |
| 404 | Resource does not exist **or belongs to another organisation** |
| 409 | Request is well-formed but violates a domain rule or state transition |

**Cross-tenant access returns 404, never 403.** A 403 confirms the resource exists somewhere,
which leaks exactly what invariant T2 forbids. To a caller from another organisation, a resource
is indistinguishable from one that was never created.

**Tenancy.** Every request is scoped to the organisation on the caller's JWT. No endpoint accepts
an `organisationId` parameter — supplying one would be an invitation to tamper with it.

**Timestamps** are UTC, ISO-8601 with `Z`, in both directions. The frontend converts for display.

**Enums** travel as strings on the wire (`"PENDING_APPROVAL"`) and are stored as integers. The
pinned integer codes in `data-model.md` are a storage detail and never appear in JSON.

**Lists** accept `page` (0-based) and `size` (default 20, max 100), and return
`{ content, page, size, totalElements, totalPages }`.

**All endpoints** live under `/api`, are documented via springdoc, and reach the frontend through
the generated TypeScript client (`npm run generate:api`).

## Deferred

Capabilities in `product.md` deliberately not covered by any spec yet:

| Capability | Source | Note |
| --- | --- | --- |
| Crew members manage their own profile | §2 Crew Member | Crew profiles and skills are seeded in 01. Self-service editing needs its own spec. |
| Directors manage organisation settings | §2 Director | `Organisation` has only a name; the spec never says what the settings are. |
| Creating and managing users | implied by §2 | Everything is seeded in 01. No user-management API in v1. |
| Org-level metrics on the director dashboard | §3 Dashboard | 08 defines a minimal set; richer analytics are out of scope. |
