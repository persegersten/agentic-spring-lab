package se.segersten.wreckage.game.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.RoundEventType;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;

class BoardEffectEngineTest {
    private final BoardEffectEngine engine = new BoardEffectEngine();

    @Test
    void appliesPitEffectToVehicleOnPitWithoutMutatingState() {
        VehicleState vehicle = vehicleAt(new Position(4, 5));
        GameState state = new GameState(new Board(8, 8, Set.of(), Set.of(new Position(4, 5))),
                List.of(vehicle));

        BoardEffectResult result = engine.resolve(state);

        assertThat(result.state()).isSameAs(state);
        assertThat(result.state().vehicleStates()).containsExactly(vehicle);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.PIT);
            assertThat(event.playerId()).isEqualTo(vehicle.vehicle().playerId());
            assertThat(event.vehicleId()).isEqualTo(vehicle.vehicle().id());
            assertThat(event.oldPosition()).isEqualTo(new Position(4, 5));
            assertThat(event.newPosition()).isEqualTo(new Position(4, 5));
        });
    }

    @Test
    void ignoresVehiclesOutsidePitsAndKeepsStableVehicleOrder() {
        VehicleState first = vehicleAt(new Position(4, 5));
        VehicleState outside = vehicleAt(new Position(3, 5));
        VehicleState second = vehicleAt(new Position(6, 5));
        GameState state = new GameState(new Board(8, 8, Set.of(),
                Set.of(new Position(4, 5), new Position(6, 5))), List.of(first, outside, second));

        BoardEffectResult result = engine.resolve(state);

        assertThat(result.events()).extracting(event -> event.vehicleId())
                .containsExactly(first.vehicle().id(), second.vehicle().id());
    }

    @Test
    void rejectsPitOutsideBoard() {
        assertThatThrownBy(() -> new Board(5, 5, Set.of(), Set.of(new Position(4, 5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pits must be inside the board");
    }

    private static VehicleState vehicleAt(Position position) {
        UUID playerId = UUID.randomUUID();
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId), position, Direction.NORTH);
    }
}
