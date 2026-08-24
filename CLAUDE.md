## Development Conventions

### Backend

- Use constructor injection.
- Do not expose persistence entities directly through REST APIs.
- Use lombok for entities.
- Use lombok for class constructors.
- Use Slf4j for logging.
- Use java records for DTOs
- Use JUnit 5 for testing.
- Use UTC time in database.
- Store enums as integers in database and map to enums in code.
- Avoid introducing N+1 database queries.
- Rebuild the image before every containerised test run to avoid stale images.
- Treat a -Dtest= filter that prints no per-class lines as a failure rather than a pass.

### Frontend

- Use TypeScript.
- Prefer functional React components.
- Handle local timezones and convert to/from UTC when communication with backend.
- Use MUI components and override styling where applicable.
- Avoid raw HTML and CSS as much as possible.
- Avoid inline MUI styling and create a styled component if more than one override.
- Rebuild image before typecheck to avoid stale images.

### General

- Do not introduce dependencies without a clear reason.
- Keep changes focussed on current task.
- Keep documentation and specs concise.
- Include tests that cover main workflows as well as important edge cases.
- Use LF for line endings.
- If not specified and required, ask about UI design.
- Do not read, search, summarize, modify, or use files under `/transcripts` unless the user explicitly
asks you to work with a transcript. These are not part of the application source or documentation.
- Do not read or search files under `/frontend/node_modules` or `/backend/target` unless absolutely
necessary, and consult the user before you do so.
