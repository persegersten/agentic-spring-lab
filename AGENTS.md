# Wreckage – Codex Development Instructions

## Project

Wreckage is a multiplayer robot-programming game.

- Backend: Java, Spring Boot.
- Frontend: React and Vite.
- Deployment: Docker Compose on Debian Linux.

Inspect existing code and configuration before assuming implementation details.

## Development principles

- Prefer simple solutions consistent with the existing architecture.
- Keep changes focused on the requested feature.
- Preserve existing behavior unless explicitly asked to change it.
- Keep the backend authoritative for game state and rules.
- Make game logic deterministic and independently testable.
- Add or update relevant tests when changing behavior.
- Use Flyway for database schema changes. Never modify previously applied migrations.
- Do not introduce new frameworks or dependencies without a concrete reason.

## Project documentation

- Consult `docs/game-rules.md` when changing game mechanics.
- Consult architecture documentation when changing module boundaries.
- Consult deployment documentation when changing production infrastructure.
- Read only the documentation relevant to the current task.

## Verification

- Run relevant automated tests after code changes.
- Fix failures caused by the change.
- Report the tests actually executed and their results.
- Explain any verification that could not be performed.

## Boundaries

- Never deploy to the production VPS without explicit authorization.
- Never access or modify production secrets.
- Do not introduce changes unrelated to the task.
- Do not commit or push unless explicitly requested.

For substantial changes, use a reviewable plan before implementation. For small, well-defined changes, implement directly.
