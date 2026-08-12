# Acceptance tests

Tests here exercise Wreckage as a black box over HTTP.

Start PostgreSQL and the backend, then run:

```bash
cd acceptance-tests
npm test
```

The backend is expected at `http://localhost:8080`. Override it when needed:

```bash
BASE_URL=http://localhost:9000 npm test
```
