package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.BoardEffectEngine;
import se.segersten.wreckage.game.engine.MovementEngine;
import se.segersten.wreckage.game.support.SequentialFlagScenarios;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

class LaserFlagTheftTest {
    @Test
    void unshieldedHitTransfersBeforeDisplacementAndTurning() { assertHit(Scenario.THEFT); }

    @Test
    void shieldPreventsTheftDisplacementAndTurning() { assertHit(Scenario.THEFT_SHIELD); }

    @Test
    void blockedDisplacementDoesNotUndoTheftOrPreventTurning() { assertHit(Scenario.THEFT_BLOCKED); }

    @Test
    void pitCrashDoesNotUndoTheftAndCrashedTargetDoesNotTurn() { assertHit(Scenario.THEFT_PIT); }

    private void assertHit(Scenario scenario) {
        var original = new Game(UUID.randomUUID(), new Board(10, 10));
        original.addPlayer("Alice"); original.addPlayer("Bob");
        Game game = SequentialFlagScenarios.configure(original, scenario);
        Player alice = game.getPlayers().getFirst();
        Player bob = game.getPlayers().get(1);
        boolean shield = scenario == Scenario.THEFT_SHIELD;
        Instant due = game.getContinuous().nextTick;
        game.tick(due);
        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getContinuous().tick).isEqualTo(11);
        assertThat(alice.getVisitedCheckpoints()).isEqualTo(shield ? Set.of("CP1") : Set.of());
        assertThat(bob.getVisitedCheckpoints()).isEqualTo(shield ? Set.of() : Set.of("CP1"));
        assertThat(shield ? alice.getFlagAcquisitionTicks() : bob.getFlagAcquisitionTicks())
                .containsEntry("CP1", shield ? 10L : 11L);
        assertThat(FlagProgress.nextAvailable(game.getBoard(), game.getPlayers()).id()).isEqualTo("CP2");
        assertThat(bob.getScore()).isZero(); // Theft is ownership transfer, not a new board capture.
        var events = game.getRound().playback();
        var transfers = events.stream().filter(event -> event.type() == RoundEventType.FLAG_TRANSFERRED).toList();
        assertThat(transfers).hasSize(shield ? 0 : 1);
        if (!shield) {
            RoundEvent transfer = transfers.getFirst();
            assertThat(transfer.playerId()).isEqualTo(alice.getId());
            assertThat(transfer.sourcePlayerId()).isEqualTo(bob.getId());
            assertThat(transfer.checkpointId()).isEqualTo("CP1");
            assertThat(transfer.actionType()).isEqualTo(ActionType.LASER);
            var hit = events.stream().filter(event -> event.type() == RoundEventType.WEAPON_HIT).findFirst().orElseThrow();
            assertThat(transfer.sequence()).isEqualTo(hit.sequence() + 1);
            assertThat(events.stream().filter(event -> Set.of(RoundEventType.PUSH, RoundEventType.PUSH_BLOCKED,
                    RoundEventType.CRASH, RoundEventType.TURN).contains(event.type())))
                    .allMatch(event -> event.sequence() > transfer.sequence());
        }
        var target = game.getVehicleStates().stream().filter(state -> state.vehicle().playerId().equals(alice.getId())).findFirst().orElseThrow();
        assertThat(target.position()).isEqualTo(new Position(shield || scenario == Scenario.THEFT_BLOCKED ? 2 : 3, 1));
        assertThat(target.status()).isEqualTo(scenario == Scenario.THEFT_PIT ? VehicleStatus.CRASHED : VehicleStatus.ACTIVE);
        assertThat(events.stream().filter(event -> event.type() == RoundEventType.TURN)).hasSize(shield || scenario == Scenario.THEFT_PIT ? 0 : 1);
        assertThat(alice.getScore()).isEqualTo(game.getConfiguration().checkpointScore()
                + (scenario == Scenario.THEFT_PIT ? game.getConfiguration().crashPenalty() : 0));
        long revision = game.getContinuous().revision;
        game.tick(due); // An early duplicate callback must not repeat the transfer.
        assertThat(game.getContinuous().revision).isEqualTo(revision);
    }

    @Test
    void aRealCellEntryRecordsItsContinuousCaptureTick() {
        Player player = Player.create(UUID.randomUUID(), "Alice", "a");
        Board board = new Board(6, 4, Set.of(), Set.of(), Set.of(new Checkpoint("CP1", 1, new Position(1, 1))));
        var vehicle = new VehicleState(new Vehicle(UUID.randomUUID(), player.getId()), new Position(0, 1), Direction.EAST);
        Round round = new Round(10, Map.of(player.getId(), new PlayerProgram(player.getId(), 1, List.of(MovementOrder.FORWARD_1), true)),
                List.of(player.getId()), new GameState(board, List.of(vehicle)));
        var engine = new MovementEngine();
        round.resolve(engine, new BoardEffectEngine(engine), List.of(player), GameConfiguration.defaults(), true);
        assertThat(player.getFlagAcquisitionTicks()).containsExactlyEntriesOf(Map.of("CP1", 10L));
    }
}
