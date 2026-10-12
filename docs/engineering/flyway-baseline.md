# Flyway baseline

## Version and prerequisites

The current baseline is `B22__baseline.sql`. Version 22 is the latest released
schema migration in this repository. Spring Boot 4.1.0 resolves Flyway 12.4.0,
whose core module includes support for `B`-prefixed baseline migrations. No
Flyway setting or dependency override is required.

The baseline describes the effective PostgreSQL schema after V22. It includes
the retained legacy tables and columns because removing them is a separate data
migration concern. V1 through V22 remain the authoritative upgrade history and
must remain immutable.

## Initialization paths

On an empty database, normal Flyway migration discovery selects B22 instead of
executing V1 through V22. Any future versioned migration newer than 22 is then
applied normally.

On a database that already has versioned entries in `flyway_schema_history`,
Flyway continues from those entries. It does not execute B22 over an existing
history. In particular, an existing V22 database remains at V22 until a newer V
migration is released.

Do not enable `baselineOnMigrate`, invoke the `baseline`, `repair`, or `clean`
commands against an application database, or re-baseline an existing database.
The B22 file is only an alternative initialization path for an empty schema.

## Verification

`FlywayBaselineIntegrationTest` uses two independent disposable
`postgres:16-alpine` databases:

1. The historical database is first targeted at V1 using a temporary migration
   location containing only V1. This is necessary because Flyway selects B22 on
   an empty database even when the target is V1. The test verifies that exactly
   V1 was applied, then migrates normally through V2 to V22.
2. The baseline database is migrated normally from empty and must apply only
   B22 at the current repository version.
3. PostgreSQL catalog snapshots are compared without excluding structural
   differences. They include tables, ordered visible columns, exact types and lengths,
   nullability, defaults, identity attributes, sequence settings and ownership,
   named constraints and their PostgreSQL definitions, referential actions, and
   indexes.
4. All application tables are checked for reference rows. Neither path contains
   any because V1 through V22 define no seeded reference data.
5. Flyway history is checked independently: the historical database contains
   V1 through V22 and no B22 entry, while the fresh database contains B22. A
   second normal migration of the historical database applies nothing.
6. Spring Boot is started against each migrated database with Flyway disabled
   and `spring.jpa.hibernate.ddl-auto=validate`, proving Hibernate accepts both
   schemas without changing them.

Run the focused check from `backend` with:

```text
./mvnw -Dtest=FlywayBaselineIntegrationTest test
```

Then run the full backend suite with `./mvnw test`. Both commands require a
working Docker environment for PostgreSQL Testcontainers. The focused test is
required to fail when Docker is unavailable; a run without both PostgreSQL
databases is not schema equivalence verification.

### Verification status

The baseline initializes successfully under the H2 PostgreSQL profile, passes
Hibernate schema validation, and passes all seven in-memory persistence tests.
The PostgreSQL equivalence test and the complete PostgreSQL-backed suite compile
in the current development environment, but their October 12, 2026 local run
could not start because that environment could not access a Docker socket. Run the
focused test with Docker before release; its successful completion is the
recorded equivalence result because any catalog difference fails the test with
both normalized snapshots in the assertion output.

## Known limitations

- B22 is intended only for empty schemas. It does not convert or validate
  application data from an older database.
- The baseline deliberately retains physical legacy structures, including
  `vehicle_segment`, nullable legacy vehicle links, and the damage, ammunition,
  weapon, and ability columns.
- PostgreSQL 16 is the equivalence authority. H2 PostgreSQL mode remains useful
  for application tests but is not used to decide schema equivalence.
- Generated object identifiers, current sequence values, database ownership,
  and Flyway's own history rows are environment or path state rather than
  application-schema structure. The test compares sequence definitions and
  ownership while checking Flyway history separately. PostgreSQL leaves a gap
  in internal attribute numbers when V16 drops the first `vehicle.damage`
  column; the comparison normalizes that invisible gap while preserving visible
  column order and comparing the replacement column added by V18 in full.
