Wreckage

Web-based turn-based multiplayer vehicle combat game.

Current scope:
- Create game
- Add players
- Read game state

No combat.
No movement.
No authentication.

## Run the backend

PostgreSQL is used by default. To start the backend without an external
database, activate the `in-memory` Spring profile:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=in-memory
```

This profile uses an H2 database that is discarded when the backend process
exits.

Acceptance tests live in `acceptance-tests/` and exercise the running system over
HTTP. See `acceptance-tests/README.md` for how to run them.
