## Development Conventions

### Backend

- Use constructor injection.
- Do not expose persistence entities directly through REST APIs.
- Use lombok for entities
- Use java records for DTOs
- Use JUnit 5 for testing.
- Use UTC time in database.
- Store enums as integers in database and map to enums in code.

### Frontend

- Use TypeScript.
- Prefer functional React components.
- Handle local timezones and convert to/from UTC when communication with backend.
- Use MUI components and override styling where applicable.
- Avoid raw HTML and CSS as much as possible.
- Avoid inline styling and create styled component if more than one override.

### General

- Do not introduce dependencies without a clear reason.
- Keep changes focussed on current task.
- Keep documentation and specs concise.
- Include tests that cover main workflows as well as important edge cases.
- Use LF for line endings.
