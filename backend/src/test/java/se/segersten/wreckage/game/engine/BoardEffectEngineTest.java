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
import se.segersten.wreckage.game.domain.VehicleStatus;
import se.segersten.wreckage.game.domain.Conveyor;import se.segersten.wreckage.game.domain.Rotator;import se.segersten.wreckage.game.domain.Rotation;

class BoardEffectEngineTest {
    private final BoardEffectEngine engine = new BoardEffectEngine();

    @Test
    void defensivelyCrashesAnActiveVehicleAlreadyOnAPit() {
        VehicleState vehicle = vehicleAt(new Position(4, 5));
        GameState state = new GameState(new Board(8, 8, Set.of(), Set.of(new Position(4, 5))),
                List.of(vehicle));

        BoardEffectResult result = engine.resolve(state);

        assertThat(result.state()).isNotSameAs(state);
        assertThat(result.state().vehicleStates()).singleElement()
                .extracting(VehicleState::status).isEqualTo(VehicleStatus.CRASHED);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.CRASH);
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
    @Test void conveyorPushesRespectsWallsCrashesAndRotates(){var a=vehicleAt(new Position(0,1));var b=vehicleAt(new Position(1,1));var base=new Board(4,3);var board=new Board(4,3,Set.of(),Set.of(new Position(3,1)),Set.of(),base.spawnPoints(),List.of(new Conveyor(new Position(0,1),Direction.EAST),new Conveyor(new Position(1,1),Direction.EAST)),List.of(new Rotator(new Position(2,1),Rotation.CLOCKWISE)));assertThat(engine.resolve(new GameState(board,List.of(a,b))).events()).extracting(e->e.type()).contains(RoundEventType.CONVEYOR_PUSH,RoundEventType.CONVEYOR_RAM,RoundEventType.ROTATOR_TURN);var wall=new Board(4,3,Set.of(new se.segersten.wreckage.game.domain.Wall(new Position(0,1),Direction.EAST)),Set.of(),Set.of(),base.spawnPoints(),List.of(new Conveyor(new Position(0,1),Direction.EAST)),List.of());assertThat(engine.resolve(new GameState(wall,List.of(a))).events()).isEmpty();}
    @Test void conveyorCanCrashAtOpenEdge(){var base=new Board(2,2);var board=new Board(2,2,Set.of(),Set.of(),Set.of(),base.spawnPoints(),List.of(new Conveyor(new Position(1,0),Direction.EAST)),List.of());assertThat(engine.resolve(new GameState(board,List.of(vehicleAt(new Position(1,0))))).events()).extracting(e->e.type()).containsExactly(RoundEventType.CONVEYOR_CRASH);}

    @Test
    void shieldBlocksAnEntireConveyorPushChain() {
        VehicleState conveyorVehicle = vehicleAt(new Position(0, 1));
        VehicleState anchored = vehicleAt(new Position(1, 1));
        Board base = new Board(4, 3);
        Board board = new Board(4, 3, Set.of(), Set.of(), Set.of(), base.spawnPoints(),
                List.of(new Conveyor(new Position(0, 1), Direction.EAST)), List.of());
        RegisterEffects effects = new RegisterEffects();
        effects.shield(anchored.vehicle().id());

        BoardEffectResult result = engine.resolve(new GameState(board, List.of(conveyorVehicle, anchored)), effects);

        assertThat(result.state().vehicleStates()).containsExactly(conveyorVehicle, anchored);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(RoundEventType.PUSH_BLOCKED);
            assertThat(event.vehicleId()).isEqualTo(anchored.vehicle().id());
        });
    }

    @Test
    void shieldAlsoBlocksTheProtectedVehicleFromBeingMovedByItsConveyor() {
        VehicleState anchored = vehicleAt(new Position(0, 1));
        Board base = new Board(4, 3);
        Board board = new Board(4, 3, Set.of(), Set.of(), Set.of(), base.spawnPoints(),
                List.of(new Conveyor(new Position(0, 1), Direction.EAST)), List.of());
        RegisterEffects effects = new RegisterEffects();
        effects.shield(anchored.vehicle().id());

        BoardEffectResult result = engine.resolve(new GameState(board, List.of(anchored)), effects);

        assertThat(result.state().vehicleStates()).containsExactly(anchored);
        assertThat(result.events()).extracting(event -> event.type()).containsExactly(RoundEventType.PUSH_BLOCKED);
    }

    private static VehicleState vehicleAt(Position position) {
        UUID playerId = UUID.randomUUID();
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId), position, Direction.NORTH);
    }
}
