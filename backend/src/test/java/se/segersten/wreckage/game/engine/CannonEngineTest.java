package se.segersten.wreckage.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

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

class CannonEngineTest {
    private final CannonEngine engine = new CannonEngine();

    @Test void hitsFirstVehicleInLineAndAppliesOneDamage() {
        VehicleState shooter = state(1, 2, Direction.EAST);
        VehicleState target = state(4, 2, Direction.NORTH);

        CannonResult result = engine.resolve(new GameState(new Board(7, 5), List.of(shooter, target)));

        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.FIRE, RoundEventType.HIT, RoundEventType.DAMAGE,
                        RoundEventType.FIRE);
        assertThat(result.events().get(0).oldPosition()).isEqualTo(shooter.position());
        assertThat(result.events().get(0).newPosition()).isEqualTo(target.position());
        assertThat(result.events().get(1).sourceVehicleId()).isEqualTo(shooter.vehicle().id());
        assertThat(result.events().get(1).vehicleId()).isEqualTo(target.vehicle().id());
        assertThat(result.events().get(2).oldDamage()).isZero();
        assertThat(result.events().get(2).newDamage()).isOne();
        assertThat(result.state().vehicleStates().get(1).damage()).isOne();
        assertThat(target.damage()).isZero();
    }

    @Test void wallBlocksShotBeforeVehicle() {
        VehicleState shooter = state(1, 2, Direction.EAST);
        VehicleState target = state(5, 2, Direction.WEST);
        Board board = new Board(7, 5, Set.of(new Position(3, 2)));

        CannonResult result = engine.resolve(new GameState(board, List.of(shooter, target)));

        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoundEventType.FIRE, RoundEventType.FIRE);
        assertThat(result.events().getFirst().newPosition()).isEqualTo(new Position(3, 2));
        assertThat(result.state().vehicleStates()).extracting(VehicleState::damage).containsOnly(0);
    }

    @Test void nearestVehicleShieldsVehiclesBehindIt() {
        VehicleState shooter = state(0, 1, Direction.EAST);
        VehicleState nearest = state(2, 1, Direction.NORTH);
        VehicleState farthest = state(4, 1, Direction.NORTH);

        CannonResult result = engine.resolve(new GameState(new Board(6, 4), List.of(shooter, nearest, farthest)));

        assertThat(result.state().vehicleStates()).extracting(VehicleState::damage)
                .containsExactly(0, 1, 0);
    }

    @Test void firesToBoundaryInEveryDirection() {
        List<VehicleState> vehicles = List.of(state(2, 2, Direction.NORTH), state(4, 2, Direction.EAST),
                state(2, 4, Direction.SOUTH), state(0, 2, Direction.WEST));

        assertThat(vehicles.stream().map(vehicle -> engine.resolve(
                        new GameState(new Board(5, 5), List.of(vehicle))).events().getFirst().newPosition()))
                .containsExactly(new Position(2, 4), new Position(4, 2),
                        new Position(2, 0), new Position(0, 2));
    }

    private VehicleState state(int x, int y, Direction direction) {
        UUID playerId = UUID.randomUUID();
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId), new Position(x, y), direction, 0);
    }
}
