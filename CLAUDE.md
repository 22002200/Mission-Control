## Development Conventions

### Backend

- Use constructor injection.
- Do not expose persistence entities directly through REST APIs.
- Use lombok Builder and Getter for data models
- Use JUnit 5 for testing.
- Use UTC time in database.
- Store enums as integers in database and map to enums in code.

### Frontend

- Use TypeScript.
- Prefer functional React components.
- Handle local timezones and convert to/from UTC when communication with backend.

### General

- Do not introduce dependencies without a clear reason.
- Keep changes focussed on current task.
- Keep documentation and specs concise.
