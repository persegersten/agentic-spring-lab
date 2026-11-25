package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.*;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

@ActiveProfiles({"in-memory", "sequential-flags-e2e"})
@SpringBootTest(properties = {"wreckage.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:laser-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class LaserFlagRecoveryIntegrationTest {
    @Autowired SequentialFlagFixtureController fixtures;
    @Autowired GameService service;

    @ParameterizedTest
    @EnumSource(value = Scenario.class, names = {"THEFT_RECOVERY_16", "THEFT_RECOVERY_20", "THEFT_RECOVERY_21",
            "THEFT_RESPAWN_16", "THEFT_RESPAWN_20", "THEFT_RESPAWN_21", "THEFT_FINISHED"})
    void restoredTheftKeepsItsClockAcrossCrashRespawnAndCompletion(Scenario scenario) {
        var fixture = fixtures.create(scenario);
        var bob = fixture.sessions().get(1);
        var carol = fixture.sessions().get(2);
        var saved = service.getGame(fixture.gameId());
        for (var session : fixture.sessions()) {
            var restored = service.getPlayerGame(session.gameId(), session.playerId(), session.token());
            assertThat(restored).isNotSameAs(saved);
            assertThat(restored.requirePlayer(bob.playerId()).getFlagAcquisitionTicks()).isEqualTo(Map.of("CP1", 15L));
            assertThat(restored.getPlayers().stream().filter(p -> !p.getId().equals(bob.playerId())))
                    .allSatisfy(p -> {
                        assertThat(p.getFlagAcquisitionTicks()).isEmpty();
                        assertThat(p.getVisitedCheckpoints()).isEmpty();
                    });
            assertThat(restored.getRound().playback()).isEqualTo(saved.getRound().playback());
            assertThat(restored.getContinuous().tick).isEqualTo(saved.getContinuous().tick);
        }
        if (scenario == Scenario.THEFT_FINISHED) {
            assertThat(saved.getStatus()).isEqualTo(GameStatus.FINISHED);
            fixtures.tick(fixture.gameId(), 1);
            var finished = service.getGame(fixture.gameId());
            assertThat(GameResponse.from(finished).placements()).isEqualTo(GameResponse.from(saved).placements());
            assertThat(finished.getContinuous().tick).isEqualTo(15);
            return;
        }
        int hitTick = Integer.parseInt(scenario.name().substring(scenario.name().lastIndexOf('_') + 1));
        if (scenario.name().startsWith("THEFT_RESPAWN")) {
            assertThat(saved.requirePlayer(bob.playerId()).getCrashes()).isOne();
            var state = saved.getVehicleStates().stream().filter(v -> v.vehicle().playerId().equals(bob.playerId())).findFirst().orElseThrow();
            assertThat(state.status()).isEqualTo(hitTick == 16 ? VehicleStatus.CRASHED : VehicleStatus.ACTIVE);
        }
        fixtures.tick(fixture.gameId(), 1);
        var result = service.getGame(fixture.gameId());
        var owner = hitTick <= 20 ? carol : bob;
        assertThat(result.requirePlayer(owner.playerId()).getFlagAcquisitionTicks())
                .isEqualTo(Map.of("CP1", hitTick <= 20 ? (long) hitTick : 15L));
        assertThat(result.getPlayers()).filteredOn(p -> p.getVisitedCheckpoints().contains("CP1")).hasSize(1);
        assertThat(result.getRound().playback()).filteredOn(e -> e.type() == RoundEventType.FLAG_TRANSFERRED)
                .hasSize(hitTick <= 20 ? 1 : 0);
        assertThat(result.getRound().playback()).filteredOn(e -> e.type() == RoundEventType.WEAPON_HIT).hasSize(1);
    }
}
