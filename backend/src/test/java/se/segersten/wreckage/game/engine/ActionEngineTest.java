package se.segersten.wreckage.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.*;

class ActionEngineTest {
    private final ActionEngine engine = new ActionEngine();

    @Test
    void laserHitsOnlyFirstActiveVehicleAndAppliesOneDamage() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState first = vehicle(2, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        VehicleState covered = vehicle(4, 1, Direction.WEST, 0, VehicleStatus.ACTIVE);

        ActionResult result = fire(new Board(8, 4), shooter, first, covered);

        assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT, RoundEventType.DAMAGE_APPLIED);
        assertThat(state(result, first).damage()).isEqualTo(1);
        assertThat(state(result, covered).damage()).isZero();
        assertThat(result.events().getFirst().oldPosition()).isEqualTo(shooter.position());
        assertThat(result.events().getFirst().newPosition()).isEqualTo(first.position());
    }

    @Test
    void wallBlocksLaserFromEitherEdgeRepresentation() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState target = vehicle(2, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        Board base = new Board(8, 4);
        for (Wall wall : List.of(new Wall(new Position(1, 1), Direction.EAST),
                new Wall(new Position(2, 1), Direction.WEST))) {
            Board board = new Board(8, 4, Set.of(wall), Set.of(), Set.of(), base.spawnPoints());
            ActionResult result = fire(board, shooter, target);
            assertThat(result.events()).extracting(RoundEvent::type).containsExactly(RoundEventType.WEAPON_FIRED);
            assertThat(state(result, target).damage()).isZero();
            assertThat(result.events().getFirst().newPosition()).isEqualTo(new Position(1, 1));
        }
    }

    @Test
    void laserStopsAtRangeSixAndBoardBoundary() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState atSix = vehicle(6, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        assertThat(state(fire(new Board(8, 4), shooter, atSix), atSix).damage()).isEqualTo(1);

        VehicleState atSeven = vehicle(7, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        ActionResult outOfRange = fire(new Board(8, 4), shooter, atSeven);
        assertThat(state(outOfRange, atSeven).damage()).isZero();
        assertThat(outOfRange.events().getFirst().newPosition()).isEqualTo(new Position(6, 1));

        VehicleState north = vehicle(1, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        ActionResult boundary = fire(new Board(3, 3), north);
        assertThat(boundary.events().getFirst().newPosition()).isEqualTo(new Position(1, 2));
    }

    @Test
    void crashedVehicleIsIgnoredAndDamageAccumulatesUntilThirdHitCrashes() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState crashedCover = vehicle(1, 1, Direction.NORTH, 2, VehicleStatus.CRASHED);
        VehicleState target = vehicle(3, 1, Direction.NORTH, 1, VehicleStatus.ACTIVE);
        ActionResult secondHit = fire(new Board(8, 4), shooter, crashedCover, target);
        assertThat(state(secondHit, target).damage()).isEqualTo(2);

        ActionResult thirdHit = fire(secondHit.state().board(), shooter, crashedCover, state(secondHit, target));
        VehicleState result = state(thirdHit, target);
        assertThat(result.damage()).isEqualTo(3);
        assertThat(result.status()).isEqualTo(VehicleStatus.CRASHED);
        assertThat(thirdHit.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT,
                RoundEventType.DAMAGE_APPLIED, RoundEventType.VEHICLE_CRASHED);
    }

    private ActionResult fire(Board board, VehicleState shooter, VehicleState... others) {
        var states = new java.util.ArrayList<VehicleState>();
        states.add(shooter); states.addAll(List.of(others));
        PlayerProgram program = new PlayerProgram(shooter.vehicle().playerId(), 1,
                List.of(MovementOrder.WAIT), true, new ScheduledAction(ActionType.LASER, 1));
        return engine.resolve(ActionTiming.POST_MOVEMENT, 1, new GameState(board, states),
                Map.of(shooter.vehicle().playerId(), program), List.of(shooter.vehicle().playerId()));
    }

    private VehicleState state(ActionResult result, VehicleState expected) {
        return result.state().vehicleStates().stream()
                .filter(candidate -> candidate.vehicle().id().equals(expected.vehicle().id())).findFirst().orElseThrow();
    }

    private VehicleState vehicle(int x, int y, Direction direction, int damage, VehicleStatus status) {
        UUID playerId = UUID.randomUUID();
        return new VehicleState(new Vehicle(UUID.randomUUID(), playerId), new Position(x, y), direction, status, damage);
    }
}
