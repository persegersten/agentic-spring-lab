läs alltid docs/game-spec.md, docs/game-rules.md samt den aktuella feature-specen, men implementera inte framtida features.
Följ alltid arkitekturen i docs/architectire.md. Om den är omöjlig att följa konsultera människan.

## Testing

Each feature must include tests at the lowest appropriate level.

- Game engine rules should primarily use unit tests.
- Backend/API behaviour should use integration tests.
- User-visible multiplayer behaviour should use Playwright E2E tests.
- Existing E2E tests must remain green.

For features that change observable GUI behaviour,
add or update Playwright tests covering the relevant acceptance criteria.

Do not test game-engine rules exclusively through the GUI. 