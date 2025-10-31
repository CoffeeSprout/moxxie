# Repository Guidelines

## Project Structure & Module Organization
Core code lives under `src/main/java/com/coffeesprout`, with `api` exposing REST resources, `service` holding business logic, `scheduler` covering Quartz jobs, and `federation` handling multi-site providers. Shared configs and SQL assets sit in `src/main/resources`, while Quarkus test fixtures live in `src/test/java` with overrides in `src/test/resources`. Supporting references reside in `docs/`, helper automation in `scripts/`, and runnable samples in `examples/`.

## Build, Test, and Development Commands
- `./mvnw quarkus:dev` — launches hot-reload development mode with Dev Services for Postgres.
- `./mvnw test` — runs the unit and integration test suites.
- `./mvnw verify` — executes tests plus PMD and formatting checks; use before raising a PR.
- `./mvnw spotless:apply` — auto-formats Java sources to the enforced style.
- `./mvnw package [-Dnative …]` — creates the runnable JAR or native binary in `target/`.

## Coding Style & Naming Conventions
Spotless enforces four-space indentation, trimmed trailing whitespace, and the `java|javax,jakarta` import order; run it before committing. Java classes follow UpperCamelCase, services stay in `service`, and REST endpoints prefer `<Subject>Resource`. Constants belong in `constants/` (upper snake case), DTOs live under `api/dto`, and public methods need concise Javadoc when behavior is non-obvious. PMD runs during `verify`, so remove dead code instead of suppressing rules.

## Testing Guidelines
Tests extend Quarkus' JUnit 5 stack via `@QuarkusTest`, live in `src/test/java`, and end with `*Test`. Use Dev Services defaults and only override `application.properties` when bespoke wiring is essential. Cover both API and service layers—new endpoints need a resource test plus focused service coverage, and scheduler changes should assert job registration through the service interfaces.

## Commit & Pull Request Guidelines
Recent history favors concise, imperative summaries (for example, `Refactor unit conversions and fix configuration issues` or `Fix: Remove @Transactional from private method`). Follow that tone, optionally leading with a scoped tag (`Fix:`, `Feat:`), and keep details in the body wrapped near 100 columns. Pull requests should link issues, note config or schema impacts, list the verification commands run, and include API evidence when behavior changes. Update `docs/` or `examples/` whenever workflows shift.

## Security & Configuration Tips
Never commit live credentials; use environment variables or local `application.properties` overrides with sanitized samples. Ensure new scripts or tests in `scripts/` do not assume privileged Proxmox access. When documenting configuration, reference `CONFIGURATION.md` and note schema updates in `src/main/resources/db` so operators can track migrations.
