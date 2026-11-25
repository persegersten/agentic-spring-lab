package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;
import se.segersten.wreckage.game.support.SequentialFlagScenarios;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

class HardMatchLimitTest {
    private final List<Scenario> scenarios = List.of(Scenario.LIMIT_TIE, Scenario.LIMIT_ZERO,
            Scenario.LIMIT_UNEQUAL, Scenario.LIMIT_FINAL);

    @Test
    void finalDueTickFinishesAtTheUnchangedDeadlineWithOwnershipOnlyStandings() {
        for (Scenario scenario : scenarios) {
            Game game = fixture(scenario);
            var clock = game.getContinuous();
            Instant deadline = clock.deadline;
            assertThat(deadline).isEqualTo(Instant.parse("2099-01-01T00:10:00Z"));
            assertThat(clock.intervalMillis).isEqualTo(2000);
            game.tick(deadline.minusNanos(1));
            assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
            assertThat(clock.tick).isEqualTo(299);
            game.tick(deadline);
            assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
            assertThat(clock.tick).isEqualTo(300);
            assertThat(clock.deadline).isEqualTo(deadline);
            assertStandings(game, scenario);
            assertThat(game.getRound().playback()).extracting(RoundEvent::type)
                    .contains(RoundEventType.CONVEYOR_MOVE, RoundEventType.ROTATOR_TURN);
            assertThat(game.getRound().playback()).filteredOn(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT)
                    .hasSize(scenario == Scenario.LIMIT_FINAL ? 1 : 0);
            assertThat(FlagProgress.allCaptured(game.getBoard(), game.getPlayers())).isEqualTo(scenario == Scenario.LIMIT_FINAL);

            long revision = clock.revision;
            Round finished = game.getRound();
            var vehicles = game.getVehicleStates();
            var placements = game.getPlacements();
            game.tick(deadline.plusSeconds(100));
            assertThat(clock.tick).isEqualTo(300);
            assertThat(clock.revision).isEqualTo(revision);
            assertThat(game.getRound()).isSameAs(finished);
            assertThat(game.getVehicleStates()).isEqualTo(vehicles);
            assertThat(game.getPlacements()).isEqualTo(placements);
        }
    }

    @Test
    void aLateSchedulerFinishesThePendingFinalTickOnceWithoutExtendingTheDeadline() {
        Game game = fixture(Scenario.LIMIT_FINAL);
        var clock = game.getContinuous();
        Instant deadline = clock.deadline;
        game.tick(deadline.plusMillis(250));
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(clock.tick).isEqualTo(300);
        assertThat(clock.deadline).isEqualTo(deadline);
        assertThat(game.getRound().playback()).filteredOn(event -> "CP4".equals(event.checkpointId())).hasSize(1);
    }

    @Test
    void aTickScheduledPastTheDeadlineNeverExecutes() {
        Game game = fixture(Scenario.LIMIT_ZERO);
        var clock = game.getContinuous();
        clock.nextTick = clock.deadline.plusMillis(1);
        var vehicles = game.getVehicleStates();
        long revision = clock.revision;
        game.tick(clock.deadline);
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(clock.tick).isEqualTo(299);
        assertThat(clock.revision).isEqualTo(revision + 1);
        assertThat(game.getVehicleStates()).isEqualTo(vehicles);
        assertThat(game.getRound()).isNull();
        assertStandings(game, Scenario.LIMIT_ZERO);
    }

    @Test
    void aDeadlineBetweenTicksEndsWithoutInventingAnExtraRound() {
        Game game = fixture(Scenario.LIMIT_TIE);
        Instant start = Instant.parse("2099-01-01T00:00:00Z");
        game.enableContinuous(start, 2000, 5000);
        game.tick(start.plusSeconds(2));
        game.tick(start.plusSeconds(4));
        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        Round last = game.getRound();
        game.tick(start.plusSeconds(5));
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getContinuous().tick).isEqualTo(2);
        assertThat(game.getRound()).isSameAs(last);
        assertStandings(game, Scenario.LIMIT_TIE);
    }

    @Test
    void registerModeFinishesItsExistingFinalRoundWithTheSameStandings() {
        for (Scenario scenario : scenarios) {
            Game source = fixture(scenario);
            int limit = MatchSettings.forPlayerCount(source.getPlayers().size()).roundLimit();
            var configuration = source.getConfiguration().withRoundLimit(limit);
            Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
            Map<UUID, VehicleState> states = new LinkedHashMap<>();
            source.getVehicleStates().forEach(state -> states.put(state.vehicle().playerId(), state));
            source.getPlayers().forEach(player -> programs.put(player.getId(), new PlayerProgram(player.getId(), 5,
                    List.of(MovementOrder.FORWARD_1, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT, MovementOrder.WAIT), true)));
            Round round = new Round(limit, programs, source.getPlayers().stream().map(Player::getId).toList(),
                    new GameState(source.getBoard(), source.getVehicleStates()));
            Game game = new Game(source.getId(), source.getPlayers(), source.getBoard(), GameStatus.RUNNING,
                    states, round, configuration, source.getCreatedAt(), source.getJoinDeadline());
            round.resolve(new MovementEngine(), game.getPlayers(), configuration);
            assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
            assertThat(round.playback()).filteredOn(event -> event.type() == RoundEventType.ROTATOR_TURN).hasSize(5);
            game.completeRound();
            assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
            assertThat(round.number()).isEqualTo(limit);
            assertStandings(game, scenario);
            assertThatThrownBy(() -> game.startRound(Instant.now())).isInstanceOf(IllegalStateException.class)
                    .hasMessage("The game is finished");
            var placements = game.getPlacements();
            game.completeRound();
            assertThat(game.getRound()).isSameAs(round);
            assertThat(game.getPlacements()).isEqualTo(placements);
        }
    }

    private Game fixture(Scenario scenario) {
        Game original = new Game(UUID.randomUUID(), new Board(10, 10));
        original.addPlayer("Alice"); original.addPlayer("Bob");
        return SequentialFlagScenarios.configure(original, scenario);
    }

    private void assertStandings(Game game, Scenario scenario) {
        var counts = switch (scenario) {
            case LIMIT_ZERO -> List.of(0, 0);
            case LIMIT_UNEQUAL -> List.of(2, 1);
            case LIMIT_FINAL -> List.of(2, 2);
            default -> List.of(1, 1);
        };
        assertThat(game.getPlacements()).extracting(GamePlacement::checkpointsVisited).containsExactlyElementsOf(counts);
        assertThat(game.getPlacements()).extracting(GamePlacement::placement)
                .containsExactlyElementsOf(scenario == Scenario.LIMIT_UNEQUAL ? List.of(1, 2) : List.of(1, 1));
        assertThat(game.getPlacements()).filteredOn(GamePlacement::winner).hasSize(scenario == Scenario.LIMIT_UNEQUAL ? 1 : 2);
        assertThat(game.getPlacements()).extracting(GamePlacement::distanceToNextCheckpoint).containsOnlyNulls();
    }
}
