# 02 — Authentication

## Purpose

Let a user exchange an email and password for a JWT, and make that token the basis of every
authorisation decision in the system: who you are, which organisation you belong to, and what your
role permits.

Delivers `product.md` §3 *Authentication*. This is the feature that turns invariants T1 and T2
from documentation into enforcement, so every later spec depends on it.

## Functional requirements

- **FR-1** `POST /api/auth/login` accepts an email and password and returns a signed JWT.
- **FR-2** The JWT carries the user id (`sub`), organisation id, role, and an expiry.
- **FR-3** Tokens expire 8 hours after issue. There is no refresh token — the user logs in again.
- **FR-4** `POST /api/auth/logout` revokes the caller's tokens by setting `tokensValidFrom` to now.
- **FR-5** Any token issued before its user's `tokensValidFrom` is rejected as unauthenticated.
- **FR-6** `GET /api/auth/me` returns the authenticated user's identity and role.
- **FR-7** Every `/api/**` endpoint except login requires a valid token.
- **FR-8** The organisation on the token scopes every query. No endpoint reads an organisation id
  from a request body, path or query parameter.
- **FR-9** Endpoints declare a required role; a valid token with the wrong role is rejected.
- **FR-10** Login is rejected for users whose status is not `ACTIVE`.

## Non-functional requirements

- **NFR-1** Passwords are verified against BCrypt hashes. Plaintext passwords are never stored or
  logged.
- **NFR-2** Login responds in the same time envelope whether the email exists or not, and returns
  an identical error either way — so the endpoint cannot be used to enumerate accounts.
- **NFR-3** Tokens are signed with a secret from configuration, never a compiled-in default.
- **NFR-4** No token, password or password hash is ever written to a log.
- **NFR-5** Authentication adds no database write to the request path except at login and logout.
- **NFR-6** Requires two changes to existing code: `access-token-ttl` in `application.yml` moves
  from `PT15M` to `PT8H`, and the `permitAll()` in `SecurityConfig` is replaced with the real
  policy — the work its existing `TODO(auth)` marks.

## Business rules

- **BR-1** Email is matched case-insensitively and is unique system-wide, so it resolves to exactly
  one user — *enforces I1*.
- **BR-2** Only `ACTIVE` users may authenticate — *enforces I4*.
- **BR-3** A user's organisation comes from their record and is fixed for the token's life —
  *enforces I3, T3*.
- **BR-4** A user has exactly one role, so authorisation is a single comparison, never a set
  intersection — *enforces I2*.
- **BR-5** Every authenticated request is scoped to the token's organisation — *enforces T1*.
- **BR-6** A resource belonging to another organisation is reported as 404, never 403 —
  *enforces T2*.

## API

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | public | Exchange credentials for a token |
| POST | `/api/auth/logout` | any | Revoke the caller's tokens |
| GET | `/api/auth/me` | any | Current user's identity |

**POST `/api/auth/login`**

- Request — `email` (string, required), `password` (string, required)
- Response 200 — `token` (string), `expiresAt` (instant), `user` (see `/me`)
- Errors — 400 missing fields, 401 invalid credentials, 403 user not active

**POST `/api/auth/logout`**

- Request — none
- Response 204 — no body
- Errors — 401

Logout invalidates **every** token for that user, not just the one presented. See
[deferred](README.md#deferred).

**GET `/api/auth/me`**

- Response 200 — `id`, `fullName`, `email`, `role`, `organisationId`, `organisationName`
- Errors — 401

## Acceptance criteria

- [ ] A seeded user can log in and receives a token.
- [ ] A wrong password returns 401 with no indication of whether the email exists.
- [ ] An unknown email returns the same 401 as a wrong password.
- [ ] A disabled user cannot log in.
- [ ] `/api/auth/me` returns the caller's own record.
- [ ] Any `/api/**` call without a token returns 401.
- [ ] A token surviving past its 8-hour expiry is rejected.
- [ ] After logout, the token used to log out is rejected.
- [ ] After logout, a *different* token issued earlier to the same user is also rejected.
- [ ] A user from organisation A requesting a resource in organisation B receives 404, not 403.
- [ ] No password, hash or token appears in application logs.

## Error handling

| Condition | Status | Error type |
| --- | --- | --- |
| Missing or malformed body | 400 | `urn:mission-control:validation-failed` |
| Unknown email or wrong password | 401 | `urn:mission-control:invalid-credentials` |
| User is not `ACTIVE` | 403 | `urn:mission-control:account-disabled` |
| Absent, malformed or expired token | 401 | `urn:mission-control:unauthenticated` |
| Token predates `tokensValidFrom` | 401 | `urn:mission-control:unauthenticated` |
| Valid token, insufficient role | 403 | `urn:mission-control:forbidden` |

Unknown email and wrong password deliberately share one error type and message. Distinguishing
them would turn login into an account-enumeration oracle.
