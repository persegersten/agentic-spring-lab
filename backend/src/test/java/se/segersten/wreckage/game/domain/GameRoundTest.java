package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
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

    @Test void dealsThreeCardsToEveryPlayerAndKeepsThemPrivateByPlayer() {
        Game game=new Game(UUID.randomUUID(),new Board(5,5));
        Player a=game.addPlayer("Alice","a"), b=game.addPlayer("Bob","b");
        Round round=game.startRound(()->MovementOrder.FORWARD);
        assertThat(round.phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(round.programs().get(a.getId()).hand()).containsExactly(MovementOrder.FORWARD,MovementOrder.FORWARD,MovementOrder.FORWARD);
        assertThat(round.programs().get(b.getId()).hand()).hasSize(3);
    }
    @Test void validatesCardsAndLocksAProgramOnlyOnce() {
        Game game=new Game(UUID.randomUUID(),new Board(5,5)); Player player=game.addPlayer("Alice","a");
        Round round=game.startRound(()->MovementOrder.FORWARD);
        assertThatThrownBy(()->round.lock(player.getId(),List.of(MovementOrder.REVERSE,MovementOrder.FORWARD,MovementOrder.FORWARD))).isInstanceOf(IllegalArgumentException.class);
        round.lock(player.getId(),List.of(MovementOrder.FORWARD,MovementOrder.FORWARD,MovementOrder.FORWARD));
        assertThat(round.allReady()).isTrue();
        assertThatThrownBy(()->round.lock(player.getId(),List.of(MovementOrder.FORWARD,MovementOrder.FORWARD,MovementOrder.FORWARD))).isInstanceOf(IllegalStateException.class);
    }
    @Test void resolvesThreeSequentialStepsAndPreservesPlayback() {
        Game game=new Game(UUID.randomUUID(),new Board(5,5)); Player player=game.addPlayer("Alice","a");
        Round round=game.startRound(()->MovementOrder.FORWARD);
        round.lock(player.getId(),round.programs().get(player.getId()).hand()); round.resolve(new MovementEngine());
        assertThat(round.phase()).isEqualTo(RoundPhase.PLAYBACK); assertThat(round.playback()).hasSize(3);
        assertThat(round.playback()).extracting(PlaybackStep::index).containsExactly(1,2,3);
        assertThat(round.initialState().vehicleStates().getFirst().position()).isEqualTo(new Position(0,0));
        assertThat(round.playback().get(2).vehicleStates().getFirst().position()).isEqualTo(new Position(0,3));
    }
    @Test void waitsForEveryPlayerBeforeResolution() {
        Game game=new Game(UUID.randomUUID(),new Board(5,5)); Player a=game.addPlayer("Alice","a"); game.addPlayer("Bob","b");
        Round round=game.startRound(()->MovementOrder.TURN_LEFT); round.lock(a.getId(),round.programs().get(a.getId()).hand());
        assertThat(round.allReady()).isFalse(); assertThatThrownBy(()->round.resolve(new MovementEngine())).isInstanceOf(IllegalStateException.class);
    }
}
