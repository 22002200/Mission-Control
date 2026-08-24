# 01 — Seed Data

## Purpose

Populate the database with two organisations, users of every role, a skill catalogue, and crew
profiles with skills — so every later feature has something to work against and multi-tenant
isolation can actually be demonstrated rather than asserted.

There is no user-registration or user-management API in v1 (see
[deferred](README.md#deferred)), so seeding is the *only* way users come to exist. Two
organisations, not one, because a single-tenant dataset cannot prove invariant T2.

## Functional requirements

- **FR-1** Seed two organisations with distinct names.
- **FR-2** Seed, for each organisation: one `DIRECTOR`, two `MISSION_LEAD`s, and at least six
  `CREW_MEMBER`s.
- **FR-3** Seed a skill catalogue per organisation. The two catalogues overlap in name but are
  separate rows with separate ids, demonstrating that skills are tenant-scoped (S1).
- **FR-4** Seed a `CrewMember` for every `CREW_MEMBER` user.
- **FR-5** Give each crew member 2–5 `CrewSkill` rows at varied proficiencies, spread so that a
  requirement can produce a genuinely ranked match list — including candidates who sit exactly on
  a minimum and candidates who are over-qualified.
- **FR-6** Every seeded user has a known, documented password so the system can be logged into.
- **FR-7** Seeding is idempotent: running it against an already-seeded database changes nothing
  and does not fail.
- **FR-8** Seed data loads automatically on startup in local and docker profiles.

## Non-functional requirements

- **NFR-1** Delivered as Liquibase changesets under `db/changelog/modules/<module>/`, each owned
  by the module that owns the entity — the same ownership rule as any other migration.
- **NFR-2** Passwords are stored as BCrypt hashes, never plaintext, even in seed data.
- **NFR-3** Seed changesets are tagged so they can be excluded from a production run. Demo data
  must never be a production deployment's problem.
- **NFR-4** All seeded timestamps are UTC.
- **NFR-5** Mission dates are seeded relative to a fixed anchor date, not `now()`, so the data
  stays reproducible and tests are not time-dependent.

## Business rules

- **BR-1** Every seeded row carries the `organisationId` of its owning organisation — *enforces T1*.
- **BR-2** No seeded row references a row in the other organisation — *enforces T2*.
- **BR-3** Seeded emails are unique across both organisations, case-insensitively — *enforces I1*.
- **BR-4** Every seeded user has exactly one role — *enforces I2*.
- **BR-5** Every seeded `CrewMember` maps to a distinct user whose role is `CREW_MEMBER` —
  *enforces C1*.
- **BR-6** No crew member has the same skill twice; all proficiencies are 1–5 — *enforces C2, C3*.
- **BR-7** Skill names are unique within an organisation — *enforces S1*.

## API

None. This feature has no HTTP surface.

## Acceptance criteria

- [ ] A fresh `docker compose up` produces a database containing both organisations.
- [ ] Every seeded user can log in once feature 02 exists.
- [ ] Each organisation has at least one director, two mission leads and six crew members.
- [ ] Every crew member has at least two skills.
- [ ] Running the migrations twice leaves the row counts unchanged.
- [ ] The two organisations' skill catalogues have overlapping names but no shared rows.
- [ ] No row in one organisation references a row in the other.
- [ ] Seed credentials are documented in the project README.

## Error handling

| Condition | Behaviour |
| --- | --- |
| Seed changeset fails validation | Liquibase aborts startup; the application does not come up with a half-seeded database |
| Seed data already present | Changeset is skipped via its checksum; startup proceeds normally |
| Seed changeset edited after being applied | Liquibase fails on the checksum mismatch. Correct this with a new changeset, never by editing an applied one |
