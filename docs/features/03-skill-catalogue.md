# 03 — Skill Catalogue

## Purpose

Give each organisation a controlled vocabulary of skills. Crew profiles rate skills from this
catalogue and mission requirements ask for them, so matching compares identifiers rather than
strings — free text would break matching silently on a typo or a synonym.

Supports `product.md` §3 *Crew Matching*. Small, but a hard prerequisite: nothing in matching works
until skills exist.

## Functional requirements

- **FR-1** `GET /api/skills` lists the caller's organisation's skills, sorted by name.
- **FR-2** The list filters on `active` and on a `search` term matching the name.
- **FR-3** `GET /api/skills/{id}` returns one skill.
- **FR-4** `POST /api/skills` creates a skill. Directors only.
- **FR-5** `PATCH /api/skills/{id}` updates name, category or description. Directors only.
- **FR-6** `POST /api/skills/{id}/deactivate` and `/activate` toggle `active`. Directors only.
- **FR-7** There is no delete. Skills are deactivated instead.
- **FR-8** Inactive skills are excluded from new crew ratings and new mission requirements, but
  remain readable wherever they are already referenced.

## Non-functional requirements

- **NFR-1** Name uniqueness is enforced case-insensitively in the database, not only in
  application code — a race between two concurrent creates must not produce duplicates.
- **NFR-2** The catalogue is small and read constantly by matching; list responses should be
  cacheable per organisation.
- **NFR-3** Every response is scoped to the caller's organisation.

## Business rules

- **BR-1** `(organisationId, name)` is unique, case-insensitive — *enforces S1*.
- **BR-2** A skill is never deleted; deactivation is the only removal — *enforces S2*.
- **BR-3** A skill belongs to the caller's organisation; one from another organisation is
  invisible — *enforces T1, T2*.
- **BR-4** Only `DIRECTOR` may create, update or change the active flag. Every authenticated role
  may read, because crew and mission leads both need to see the catalogue.
- **BR-5** Deactivating a skill leaves existing `CrewSkill` and `RequiredSkill` rows untouched;
  history stays readable — *enforces S2*.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/skills` | any | List skills |
| GET | `/api/skills/{id}` | any | Get one skill |
| POST | `/api/skills` | DIRECTOR | Create |
| PATCH | `/api/skills/{id}` | DIRECTOR | Update |
| POST | `/api/skills/{id}/deactivate` | DIRECTOR | Retire |
| POST | `/api/skills/{id}/activate` | DIRECTOR | Restore |

**GET `/api/skills`**

- Query — `active` (boolean, optional), `search` (string, optional), `page`, `size`
- Response 200 — paged list of `id`, `name`, `category`, `description`, `active`

**POST `/api/skills`**

- Request — `name` (required, 1–100 chars), `category` (optional), `description` (optional)
- Response 201 — the created skill
- Errors — 400, 403, 409 duplicate name

**PATCH `/api/skills/{id}`**

- Request — any of `name`, `category`, `description`
- Response 200 — the updated skill
- Errors — 400, 403, 404, 409 duplicate name

**POST `/api/skills/{id}/deactivate`** and **`/activate`**

- Request — none
- Response 200 — the updated skill
- Errors — 403, 404

## Acceptance criteria

- [ ] A director can create a skill and it appears in the list.
- [ ] Creating a skill whose name differs only in case is rejected with 409.
- [ ] The same skill name can exist in both organisations independently.
- [ ] A mission lead and a crew member can both read the catalogue.
- [ ] A mission lead attempting to create a skill receives 403.
- [ ] Fetching a skill belonging to another organisation returns 404.
- [ ] There is no endpoint that deletes a skill.
- [ ] A deactivated skill still appears on crew profiles that already rate it.
- [ ] A deactivated skill cannot be added to a new mission requirement.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| Name blank or too long | 400 | `urn:mission-control:validation-failed` |
| Name already used in this organisation | 409 | `urn:mission-control:duplicate-skill` |
| Caller is not a director | 403 | `urn:mission-control:forbidden` |
| Skill absent, or in another organisation | 404 | `urn:mission-control:not-found` |
