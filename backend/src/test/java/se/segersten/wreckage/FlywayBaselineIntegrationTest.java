package se.segersten.wreckage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FlywayBaselineIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final String[] APPLICATION_TABLES = {
            "game", "game_round", "player", "vehicle", "vehicle_segment"
    };

    @Container
    private static final PostgreSQLContainer HISTORICAL_DATABASE = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Container
    private static final PostgreSQLContainer BASELINE_DATABASE = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    void baselineMatchesHistoricalSchemaAndPreservesMigrationPaths(@TempDir Path temporaryDirectory) throws Exception {
        Path v1OnlyLocation = temporaryDirectory.resolve("v1-only-migrations");
        Files.createDirectories(v1OnlyLocation);
        Path v1Migration = v1OnlyLocation.resolve("V1__create_initial_schema.sql");
        try (InputStream source = FlywayBaselineIntegrationTest.class
                .getResourceAsStream("/db/migration/V1__create_initial_schema.sql")) {
            Files.copy(source, v1Migration);
        }

        Flyway historicalToV1 = Flyway.configure()
                .dataSource(HISTORICAL_DATABASE.getJdbcUrl(), HISTORICAL_DATABASE.getUsername(),
                        HISTORICAL_DATABASE.getPassword())
                .locations("filesystem:" + v1OnlyLocation)
                .validateMigrationNaming(true)
                .target(MigrationVersion.fromVersion("1"))
                .load();
        assertThat(historicalToV1.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(historicalToV1.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(List.of(historicalToV1.info().applied()))
                .extracting(info -> info.getScript())
                .containsExactly("V1__create_initial_schema.sql");

        Flyway historical = flyway(HISTORICAL_DATABASE, null);
        assertThat(historical.migrate().migrationsExecuted).isEqualTo(21);
        assertHistoricalPath(historical);

        Flyway baseline = flyway(BASELINE_DATABASE, null);
        assertThat(baseline.migrate().migrationsExecuted).isEqualTo(1);
        assertBaselinePath(baseline);

        assertThat(schemaSnapshot(BASELINE_DATABASE)).isEqualTo(schemaSnapshot(HISTORICAL_DATABASE));
        assertNoReferenceData(HISTORICAL_DATABASE);
        assertNoReferenceData(BASELINE_DATABASE);

        assertThat(historical.migrate().migrationsExecuted).isZero();
        assertHistoricalPath(historical);

        validateHibernateSchema(HISTORICAL_DATABASE);
        validateHibernateSchema(BASELINE_DATABASE);
    }

    private static Flyway flyway(PostgreSQLContainer database, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void assertHistoricalPath(Flyway flyway) {
        var applied = List.of(flyway.info().applied());
        assertThat(applied)
                .extracting(info -> info.getScript())
                .contains("V1__create_initial_schema.sql", "V22__programming_cards.sql")
                .doesNotContain("B22__baseline.sql");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
        assertThat(flyway.info().pending()).isEmpty();
    }

    private static void assertBaselinePath(Flyway flyway) {
        var applied = List.of(flyway.info().applied());
        assertThat(applied)
                .extracting(info -> info.getScript())
                .containsExactly("B22__baseline.sql");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
        assertThat(flyway.info().pending()).isEmpty();
    }

    private static List<String> schemaSnapshot(PostgreSQLContainer database) throws SQLException {
        try (Connection connection = connection(database)) {
            var snapshot = new ArrayList<String>();
            snapshot.addAll(query(connection, """
                    SELECT 'table', table_name, table_type
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name <> 'flyway_schema_history'
                    ORDER BY table_name
                    """));
            snapshot.addAll(query(connection, """
                    SELECT 'column', table_name,
                           row_number() OVER (PARTITION BY table_name ORDER BY ordinal_position)::text,
                           column_name, data_type,
                           COALESCE(character_maximum_length::text, ''),
                           COALESCE(numeric_precision::text, ''), COALESCE(numeric_scale::text, ''),
                           is_nullable, COALESCE(column_default, ''), is_identity,
                           COALESCE(identity_generation, '')
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name <> 'flyway_schema_history'
                    ORDER BY table_name, ordinal_position
                    """));
            snapshot.addAll(query(connection, """
                    SELECT 'constraint', table_name, constraint_name, constraint_type,
                           pg_get_constraintdef(pc.oid, true)
                    FROM information_schema.table_constraints tc
                    JOIN pg_namespace pn ON pn.nspname = tc.constraint_schema
                    JOIN pg_constraint pc ON pc.conname = tc.constraint_name
                                         AND pc.connamespace = pn.oid
                    WHERE tc.table_schema = 'public'
                      AND tc.table_name <> 'flyway_schema_history'
                    ORDER BY table_name, constraint_name
                    """));
            snapshot.addAll(query(connection, """
                    SELECT 'index', tablename, indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND tablename <> 'flyway_schema_history'
                    ORDER BY tablename, indexname
                    """));
            snapshot.addAll(query(connection, """
                    SELECT 'sequence', sequence.relname,
                           settings.seqstart::text, settings.seqincrement::text,
                           settings.seqmin::text, settings.seqmax::text,
                           settings.seqcache::text, settings.seqcycle::text,
                           owned_table.relname, owned_column.attname, dependency.deptype::text
                    FROM pg_class sequence
                    JOIN pg_namespace namespace ON namespace.oid = sequence.relnamespace
                    JOIN pg_sequence settings ON settings.seqrelid = sequence.oid
                    JOIN pg_depend dependency ON dependency.objid = sequence.oid
                    JOIN pg_class owned_table ON owned_table.oid = dependency.refobjid
                    JOIN pg_attribute owned_column
                      ON owned_column.attrelid = owned_table.oid
                     AND owned_column.attnum = dependency.refobjsubid
                    WHERE namespace.nspname = 'public'
                      AND sequence.relkind = 'S'
                      AND owned_table.relname <> 'flyway_schema_history'
                      AND dependency.deptype IN ('a', 'i')
                    ORDER BY sequence.relname
                    """));
            return snapshot;
        }
    }

    private static List<String> query(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = result.getMetaData();
            var rows = new ArrayList<String>();
            while (result.next()) {
                var values = new ArrayList<String>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    values.add(result.getString(column));
                }
                rows.add(String.join("|", values));
            }
            return rows;
        }
    }

    private static void assertNoReferenceData(PostgreSQLContainer database) throws SQLException {
        try (Connection connection = connection(database)) {
            for (String table : APPLICATION_TABLES) {
                try (var statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).as("rows in %s", table).isZero();
                }
            }
        }
    }

    private static Connection connection(PostgreSQLContainer database) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private static void validateHibernateSchema(PostgreSQLContainer database) {
        try (var context = new SpringApplicationBuilder(WreckageApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + database.getJdbcUrl(),
                        "spring.datasource.username=" + database.getUsername(),
                        "spring.datasource.password=" + database.getPassword(),
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "wreckage.scheduling.enabled=false")
                .run()) {
            assertThat(context.isActive()).isTrue();
        }
    }
}
