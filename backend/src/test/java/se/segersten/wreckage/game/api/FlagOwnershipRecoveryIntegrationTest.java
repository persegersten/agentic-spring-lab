package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.*;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

@ActiveProfiles({"in-memory", "sequential-flags-e2e"})
@SpringBootTest(properties = {"wreckage.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:flag-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class FlagOwnershipRecoveryIntegrationTest {
    @Autowired private SequentialFlagFixtureController fixtures;
    @Autowired private GameService service;
    @Autowired private GameRepository repository;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void savedOwnershipSurvivesCrashRespawnAndOpponentReentry() {
        var fixture = fixtures.create(Scenario.RECOVERY);
        var alice = fixture.sessions().getFirst();
        var bob = fixture.sessions().get(1);
        var board = service.getGame(fixture.gameId()).getBoard();

        command(alice, MovementOrder.FORWARD_1);
        assertOwnership(fixture);
        var originalCaptureEvents = service.getGame(fixture.gameId()).getRound().playback();
        assertThat(originalCaptureEvents).filteredOn(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT).hasSize(2);

        command(bob, MovementOrder.FORWARD_1);
        assertOwnership(fixture);
        var crashed = service.getGame(fixture.gameId());
        assertThat(vehicle(crashed, bob.playerId()).status()).isEqualTo(VehicleStatus.CRASHED);
        assertThat(crashed.requirePlayer(bob.playerId()).getCrashes()).isOne();
        assertThat(crashed.getRound().playback()).noneMatch(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT);

        fixtures.tick(fixture.gameId(), 1);
        assertOwnership(fixture);
        var respawned = service.getGame(fixture.gameId());
        assertThat(vehicle(respawned, bob.playerId()).isActive()).isTrue();
        // Alice occupies Bob's assigned spawn, so the existing fallback chooses (0,1).
        assertThat(vehicle(respawned, bob.playerId()).position()).isEqualTo(new Position(0, 1));
        assertThat(respawned.getRound().startEvents()).singleElement()
                .extracting(RoundEvent::type).isEqualTo(RoundEventType.VEHICLE_RESPAWNED);

        command(bob, MovementOrder.FORWARD_1);
        assertOwnership(fixture);
        var reentered = service.getGame(fixture.gameId());
        assertThat(vehicle(reentered, bob.playerId()).position()).isEqualTo(new Position(1, 1));
        assertThat(vehicle(reentered, alice.playerId()).position()).isEqualTo(new Position(2, 1));
        assertThat(reentered.getRound().playback()).noneMatch(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT);
        assertThat(reentered.getBoard()).isEqualTo(board);
    }

    @Test
    void freshAggregateReadsKeepOwnersAndDoNotReapplySavedCapturePlayback() {
        var fixture = fixtures.create(Scenario.RECOVERY);
        command(fixture.sessions().getFirst(), MovementOrder.FORWARD_1);
        var saved = service.getGame(fixture.gameId());
        var savedEvents = saved.getRound().playback();
        long revision = saved.getContinuous().revision;
        for (int read = 0; read < 3; read++) {
            assertOwnership(fixture);
            var restored = new TransactionTemplate(transactions).execute(status -> repository.findById(fixture.gameId()).orElseThrow());
            assertThat(restored).isNotSameAs(saved);
            assertThat(restored.getContinuous().revision).isEqualTo(revision);
            assertThat(restored.getRound().playback()).isEqualTo(savedEvents);
            assertThat(restored.getPlayers()).extracting(Player::getScore).containsExactly(2, 2);
        }
    }

    private void command(SequentialFlagFixtureController.Session session, MovementOrder command) {
        service.appendCommand(session.gameId(), session.playerId(), session.token(), command, UUID.randomUUID());
        fixtures.tick(session.gameId(), 3);
    }

    private void assertOwnership(SequentialFlagFixtureController.Fixture fixture) {
        // Every authenticated read reconstructs a fresh aggregate from the database.
        for (var session : fixture.sessions()) {
            var response = PlayerGameResponse.from(service.getPlayerGame(session.gameId(), session.playerId(), session.token()), session.playerId());
            assertThat(response.players()).extracting(PlayerResponse::visitedCheckpoints)
                    .containsExactly(java.util.Set.of("CP1"), java.util.Set.of("CP2"));
            assertThat(response.players()).extracting(PlayerResponse::capturedCheckpoints).containsExactly(1, 1);
            assertThat(response.players()).extracting(PlayerResponse::nextCheckpoint).containsExactly("CP3", "CP3");
            assertThat(response.board().checkpoints()).extracting(Checkpoint::id).containsExactly("CP1", "CP2", "CP3", "CP4");
        }
    }

    private VehicleState vehicle(Game game, UUID playerId) {
        return game.getVehicleStates().stream().filter(state -> state.vehicle().playerId().equals(playerId)).findFirst().orElseThrow();
    }
}
