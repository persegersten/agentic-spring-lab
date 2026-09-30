package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;

class GameRoundTest {
    @Test
    void startsEmptyProgramsAndResolvesCompleteOnes() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(3, now);
        Player player = game.addPlayer("Alice", "a");

        Round round = game.startRound(now);
        assertThat(round.programs().get(player.getId()).commands()).isEmpty();
        assertThat(round.initiative()).containsExactly(player.getId());

        round.lock(player.getId(), List.of(
                MovementOrder.FORWARD_1, MovementOrder.TURN_RIGHT, MovementOrder.WAIT));
        round.resolve(new MovementEngine());

        assertThat(round.phase()).isEqualTo(RoundPhase.PLAYBACK);
    }

    @Test
    void rotatesInitiativeLeftExactlyOnceWhenEachNewRoundStarts() {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        Game game = game(1, now);
        List<UUID> joined = List.of(
                game.addPlayer("A", "a").getId(),
                game.addPlayer("B", "b").getId(),
                game.addPlayer("C", "c").getId(),
                game.addPlayer("D", "d").getId());

        Round first = game.startRound(now);
        assertThat(first.initiative()).containsExactlyElementsOf(joined);
        resolveWithWait(first);

        Round second = game.startRound(now);
        assertThat(second.initiative()).containsExactly(joined.get(1), joined.get(2), joined.get(3), joined.get(0));
        resolveWithWait(second);

        Round third = game.startRound(now);
        assertThat(third.initiative()).containsExactly(joined.get(2), joined.get(3), joined.get(0), joined.get(1));
        resolveWithWait(third);

        Round fourth = game.startRound(now);
        assertThat(fourth.initiative()).containsExactly(joined.get(3), joined.get(0), joined.get(1), joined.get(2));
    }

    @Test
    void resolvesEveryRegisterInInitiativeOrderAndIsDeterministic() {
        UUID leaderId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        VehicleState leader = state(leaderId, 3, 2);
        VehicleState follower = state(followerId, 1, 2);
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        programs.put(followerId, locked(followerId, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1));
        programs.put(leaderId, locked(leaderId, MovementOrder.FORWARD_1, MovementOrder.FORWARD_1));
        List<UUID> initiative = List.of(leaderId, followerId);
        GameState initial = new GameState(new Board(10, 6), List.of(follower, leader));

        Round first = new Round(1, RoundPhase.PLANNING, programs, initiative, initial, List.of());
        Round second = new Round(1, RoundPhase.PLANNING, programs, initiative, initial, List.of());
        first.resolve(new MovementEngine());
        second.resolve(new MovementEngine());

        assertThat(first.playback()).extracting(RoundEvent::playerId)
                .containsExactly(leaderId, followerId, leaderId, followerId);
        assertThat(first.playback()).isEqualTo(second.playback());
        assertThat(first.finalVehicleStates()).isEqualTo(second.finalVehicleStates());
        assertThat(first.playback()).extracting(RoundEvent::sequence).containsExactly(1, 2, 3, 4);
    }

    @Test
    void roundResolutionDoesNotProduceAutomaticCannonOrBoardEffectEvents() {
        UUID playerId = UUID.randomUUID();
        VehicleState vehicle = state(playerId, 2, 2);
        Round round = new Round(1, Map.of(playerId, locked(playerId, MovementOrder.WAIT)),
                List.of(playerId), new GameState(
                        new Board(5, 5, java.util.Set.of(), java.util.Set.of(vehicle.position())),
                        List.of(vehicle)));

        round.resolve(new MovementEngine());

        assertThat(round.playback()).isEmpty();
    }

    @Test
    void crashedVehicleSkipsItsCurrentAndLaterRegisterCommands() {
        UUID pusherId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        VehicleState pusher = state(pusherId, 1, 2);
        VehicleState victim = state(victimId, 2, 2);
        Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
        programs.put(pusherId, locked(pusherId, MovementOrder.FORWARD_1, MovementOrder.WAIT));
        programs.put(victimId, locked(victimId, MovementOrder.TURN_LEFT, MovementOrder.FORWARD_1));
        Round round = new Round(1, RoundPhase.PLANNING, programs, List.of(pusherId, victimId),
                new GameState(new Board(6, 6, java.util.Set.of(),
                        java.util.Set.of(new Position(3, 2))), List.of(pusher, victim)), List.of());

        round.resolve(new MovementEngine());

        assertThat(round.playback()).extracting(RoundEvent::type)
                .containsExactly(RoundEventType.CRASH, RoundEventType.RAM);
        assertThat(round.finalVehicleStates()).filteredOn(state -> state.vehicle().playerId().equals(victimId))
                .singleElement().satisfies(state -> {
                    assertThat(state.status()).isEqualTo(VehicleStatus.CRASHED);
                    assertThat(state.orientation()).isEqualTo(Direction.EAST);
                    assertThat(state.position()).isEqualTo(new Position(3, 2));
                });
    }

    @Test
    void assignsDistinctInitialVehiclePositions() {
        Game game = new Game(UUID.randomUUID(), new Board(5, 5));
        game.addPlayer("Alice", "a");
        game.addPlayer("Bob", "b");
        assertThat(game.getVehicleStates()).extracting(VehicleState::position).doesNotHaveDuplicates();
    }

    private Game game(int programSize, Instant now) {
        return new Game(UUID.randomUUID(), List.of(), new Board(8, 8), GameStatus.RUNNING,
                Map.of(), null, new GameConfiguration(6, 60, programSize, 30),
                now, now.plusSeconds(60));
    }

    private void resolveWithWait(Round round) {
        round.initiative().forEach(playerId -> round.lock(playerId, List.of(MovementOrder.WAIT)));
        round.resolve(new MovementEngine());
    }

    private PlayerProgram locked(UUID playerId, MovementOrder... commands) {
        return new PlayerProgram(playerId, commands.length, List.of(commands), true);
    }

    private VehicleState state(UUID playerId, int x, int y) {
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId),
                new Position(x, y), Direction.EAST, 0);
    }
}
