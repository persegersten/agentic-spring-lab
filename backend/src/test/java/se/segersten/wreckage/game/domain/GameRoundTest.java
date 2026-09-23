package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;

class GameRoundTest {
    @Test void assignsDistinctInitialVehiclePositionsOnTheServer() {
        Board board = new Board(5, 5);
        Game game = new Game(UUID.randomUUID(), board);
        Player alice = game.addPlayer("Alice", "a");
        Player bob = game.addPlayer("Bob", "b");

        List<VehicleState> vehicles = game.getVehicleStates();

        assertThat(vehicles).extracting(state -> state.vehicle().playerId())
                .containsExactly(alice.getId(), bob.getId());
        assertThat(vehicles).extracting(VehicleState::position)
                .doesNotHaveDuplicates()
                .allSatisfy(position -> {
                    assertThat(position.x()).isBetween(0, board.width() - 1);
                    assertThat(position.y()).isBetween(0, board.height() - 1);
                });
    }

    @Test void dealsConfiguredNumberOfCardsToEveryPlayer() {
        for (int cardsPerRound : List.of(3, 5, 10)) {
            Game game = configuredGame(cardsPerRound);
            Player alice = game.addPlayer("Alice", "a");
            Player bob = game.addPlayer("Bob", "b");

            Round round = game.startRound(() -> MovementOrder.FORWARD);

            assertThat(round.phase()).isEqualTo(RoundPhase.PLANNING);
            assertThat(round.programs().get(alice.getId()).hand()).hasSize(cardsPerRound);
            assertThat(round.programs().get(bob.getId()).hand()).hasSize(cardsPerRound);
        }
    }

    @Test void validatesDuplicateCardsAndLocksAProgramOnlyOnce() {
        Game game = configuredGame(5);
        Player player = game.addPlayer("Alice", "a");
        var dealt = new java.util.ArrayDeque<>(List.of(MovementOrder.FORWARD, MovementOrder.REVERSE,
                MovementOrder.FORWARD, MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT));
        Round round = game.startRound(dealt::removeFirst);
        List<MovementOrder> selected = List.of(MovementOrder.FORWARD, MovementOrder.TURN_RIGHT,
                MovementOrder.REVERSE, MovementOrder.TURN_LEFT, MovementOrder.FORWARD);

        assertThatThrownBy(() -> round.lock(player.getId(), List.of(MovementOrder.FORWARD,
                MovementOrder.REVERSE, MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> round.lock(player.getId(), List.of(MovementOrder.FORWARD,
                MovementOrder.REVERSE, MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT,
                MovementOrder.TURN_RIGHT))).isInstanceOf(IllegalArgumentException.class);

        round.lock(player.getId(), selected);

        assertThat(round.programs().get(player.getId()).orders()).containsExactlyElementsOf(selected);
        assertThat(round.allReady()).isTrue();
        assertThatThrownBy(() -> round.lock(player.getId(), selected)).isInstanceOf(IllegalStateException.class);
    }
    @Test void resolvesConfiguredNumberOfCardPositionsAndPreservesPlayback() {
        Game game=configuredGame(5); Player player=game.addPlayer("Alice","a");
        Round round=game.startRound(()->MovementOrder.REVERSE);
        round.lock(player.getId(),round.programs().get(player.getId()).hand()); round.resolve(new MovementEngine());
        assertThat(round.phase()).isEqualTo(RoundPhase.PLAYBACK); assertThat(round.playback()).hasSize(4);
        assertThat(round.playback()).extracting(RoundEvent::sequence).containsExactly(1,2,3,4);
        assertThat(round.initialState().vehicleStates().getFirst().position()).isEqualTo(new Position(0,0));
        assertThat(round.finalVehicleStates().getFirst().position()).isEqualTo(new Position(0,4));
    }
    @Test void resolvesEveryPlayersCurrentCardBeforeTheNextCardPosition() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        Vehicle aliceVehicle = new Vehicle(UUID.randomUUID(), aliceId);
        Vehicle bobVehicle = new Vehicle(UUID.randomUUID(), bobId);
        VehicleState alice = new VehicleState(aliceVehicle, new Position(0, 0), Direction.NORTH);
        VehicleState bob = new VehicleState(bobVehicle, new Position(0, 1), Direction.NORTH);
        Map<UUID, PlayerProgram> programs = new java.util.LinkedHashMap<>();
        programs.put(aliceId, lockedProgram(aliceId, MovementOrder.FORWARD, MovementOrder.TURN_RIGHT));
        programs.put(bobId, lockedProgram(bobId, MovementOrder.FORWARD, MovementOrder.FORWARD));
        Round round = new Round(1, programs, new GameState(new Board(5, 5), List.of(alice, bob)));

        round.resolve(new MovementEngine());

        assertThat(round.playback()).hasSize(3);
        assertThat(round.playback()).extracting(RoundEvent::playerId)
                .containsExactly(bobId, aliceId, bobId);
        assertThat(round.playback()).extracting(RoundEvent::sequence).containsExactly(1, 2, 3);
        assertThat(round.finalVehicleStates()).containsExactly(
                new VehicleState(aliceVehicle, new Position(0, 0), Direction.EAST),
                new VehicleState(bobVehicle, new Position(0, 3), Direction.NORTH));
    }
    @Test void waitsForEveryPlayerBeforeResolution() {
        Game game=new Game(UUID.randomUUID(),new Board(5,5)); Player a=game.addPlayer("Alice","a"); game.addPlayer("Bob","b");
        Round round=game.startRound(()->MovementOrder.TURN_LEFT); round.lock(a.getId(),round.programs().get(a.getId()).hand());
        assertThat(round.allReady()).isFalse(); assertThatThrownBy(()->round.resolve(new MovementEngine())).isInstanceOf(IllegalStateException.class);
    }

    private static Game configuredGame(int cardsPerRound) {
        var now = java.time.Instant.parse("2099-01-01T12:00:00Z");
        return new Game(UUID.randomUUID(), List.of(), new Board(5, 5), GameStatus.WAITING_FOR_PLAYERS,
                Map.of(), null, new GameConfiguration(12, 60, cardsPerRound, 30), now, now.plusSeconds(60));
    }

    private static PlayerProgram lockedProgram(UUID playerId, MovementOrder... orders) {
        List<MovementOrder> cards = List.of(orders);
        return new PlayerProgram(playerId, cards, cards);
    }
}
