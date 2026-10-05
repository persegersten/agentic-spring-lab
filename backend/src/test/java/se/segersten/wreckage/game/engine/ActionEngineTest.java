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

    @Test
    void repulsorPushesFirstTargetAndChainWithoutMovingShooter() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState first = vehicle(2, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        VehicleState second = vehicle(3, 1, Direction.WEST, 0, VehicleStatus.ACTIVE);

        ActionResult result = fire(ActionType.REPULSOR, new Board(7, 4), shooter, first, second);

        assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT,
                RoundEventType.PUSH, RoundEventType.PUSH);
        assertThat(state(result, shooter).position()).isEqualTo(new Position(0, 1));
        assertThat(state(result, first).position()).isEqualTo(new Position(3, 1));
        assertThat(state(result, second).position()).isEqualTo(new Position(4, 1));
        assertThat(result.events()).allMatch(event -> event.actionType() == ActionType.REPULSOR);
    }

    @Test
    void repulsorIsRangeThreeAndWallBlocksWholeChain() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState target = vehicle(3, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        Board base = new Board(7, 4);
        Board blocked = new Board(7, 4, Set.of(new Wall(new Position(3, 1), Direction.EAST)),
                Set.of(), Set.of(), base.spawnPoints());
        ActionResult result = fire(ActionType.REPULSOR, blocked, shooter, target);
        assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT, RoundEventType.PUSH_BLOCKED);
        assertThat(state(result, target).position()).isEqualTo(target.position());

        VehicleState outOfRange = vehicle(4, 2, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        VehicleState otherShooter = new VehicleState(shooter.vehicle(), new Position(0, 2), Direction.EAST);
        ActionResult miss = fire(ActionType.REPULSOR, new Board(7, 4), otherShooter, outOfRange);
        assertThat(miss.events()).extracting(RoundEvent::type).containsExactly(RoundEventType.WEAPON_FIRED);
    }

    @Test
    void repulsorCrashesChainEndInPitAndOpenEdge() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState first = vehicle(1, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        VehicleState second = vehicle(2, 1, Direction.WEST, 0, VehicleStatus.ACTIVE);
        Board base = new Board(5, 4);
        Board pit = new Board(5, 4, Set.of(), Set.of(new Position(3, 1)), Set.of(), base.spawnPoints());
        ActionResult pitResult = fire(ActionType.REPULSOR, pit, shooter, first, second);
        assertThat(state(pitResult, second).status()).isEqualTo(VehicleStatus.CRASHED);
        assertThat(pitResult.events()).extracting(RoundEvent::type).contains(
                RoundEventType.CRASH, RoundEventType.PUSH);

        VehicleState edgeShooter = vehicle(1, 2, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState edgeTarget = vehicle(2, 2, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        ActionResult edge = fire(ActionType.REPULSOR, new Board(3, 3), edgeShooter, edgeTarget);
        assertThat(state(edge, edgeTarget).status()).isEqualTo(VehicleStatus.CRASHED);
    }

    @Test
    void rocketDealsTwoDamageConsumesOnHitAndMissAndCannotFireAtZeroAmmo() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState target = vehicle(5, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        ActionResult hit = fire(ActionType.ROCKET, new Board(8, 4), shooter, target);
        assertThat(state(hit, target).damage()).isEqualTo(2);
        assertThat(state(hit, shooter).rocketAmmo()).isZero();
        assertThat(hit.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.AMMO_CHANGED,
                RoundEventType.WEAPON_HIT, RoundEventType.DAMAGE_APPLIED);

        ActionResult stale = fire(ActionType.ROCKET, hit.state().board(), state(hit, shooter), state(hit, target));
        assertThat(stale.events()).isEmpty();
        VehicleState missShooter = vehicle(0, 2, Direction.EAST, 0, VehicleStatus.ACTIVE);
        ActionResult miss = fire(ActionType.ROCKET, new Board(8, 4), missShooter);
        assertThat(state(miss, missShooter).rocketAmmo()).isZero();
        assertThat(miss.events()).extracting(RoundEvent::type)
                .containsExactly(RoundEventType.WEAPON_FIRED, RoundEventType.AMMO_CHANGED);
    }

    @Test
    void rocketCrashesTargetAtThreeWithoutSplash() {
        VehicleState shooter = vehicle(0, 1, Direction.EAST, 0, VehicleStatus.ACTIVE);
        VehicleState target = vehicle(2, 1, Direction.NORTH, 1, VehicleStatus.ACTIVE);
        VehicleState covered = vehicle(3, 1, Direction.NORTH, 0, VehicleStatus.ACTIVE);
        ActionResult result = fire(ActionType.ROCKET, new Board(8, 4), shooter, target, covered);
        assertThat(state(result, target).status()).isEqualTo(VehicleStatus.CRASHED);
        assertThat(state(result, target).damage()).isEqualTo(3);
        assertThat(state(result, covered).damage()).isZero();
    }

    private ActionResult fire(Board board, VehicleState shooter, VehicleState... others) {
        return fire(ActionType.LASER, board, shooter, others);
    }

    private ActionResult fire(ActionType actionType, Board board, VehicleState shooter, VehicleState... others) {
        var states = new java.util.ArrayList<VehicleState>();
        states.add(shooter); states.addAll(List.of(others));
        PlayerProgram program = new PlayerProgram(shooter.vehicle().playerId(), 1,
                List.of(MovementOrder.WAIT), true, new ScheduledAction(actionType, 1));
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
