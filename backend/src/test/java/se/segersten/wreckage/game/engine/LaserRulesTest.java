package se.segersten.wreckage.game.engine;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LaserRulesTest {
    private final VehicleState shooter = at(1, 3, Direction.EAST);
    private final VehicleState target = at(3, 3, Direction.NORTH);

    @Test void eachOfFourDirectionsIsPossibleIncludingTheOriginalDirection() {
        for (var direction : Direction.values()) {
            var result = engine(direction).resolveTurnWithEvents(laser(shooter), state(new Board(7, 7)));
            var hit = result.state().vehicleStates().get(1);
            assertThat(hit.position()).isEqualTo(new Position(4, 3));
            assertThat(hit.orientation()).isEqualTo(direction);
            assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                    RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT, RoundEventType.PUSH, RoundEventType.TURN);
            var turn = result.events().getLast();
            assertThat(turn.sourceVehicleId()).isEqualTo(shooter.vehicle().id());
            assertThat(turn.vehicleId()).isEqualTo(target.vehicle().id());
            assertThat(turn.oldDirection()).isEqualTo(Direction.NORTH);
            assertThat(turn.newDirection()).isEqualTo(direction);
            assertThat(turn.actionType()).isEqualTo(ActionType.LASER);
        }
    }

    @Test void followingCommandUsesTheNewDirection() {
        var engine = engine(Direction.WEST);
        var result = engine.resolveTurnWithEvents(new Turn(List.of(new VehicleTurn(shooter, MovementOrder.LASER),
                new VehicleTurn(target, MovementOrder.FORWARD_1))), state(new Board(7, 7)));
        assertThat(result.state().vehicleStates().get(1).position()).isEqualTo(new Position(3, 3));
        assertThat(result.state().vehicleStates().get(1).orientation()).isEqualTo(Direction.WEST);
    }

    @Test void wallAndBoundaryBlockDisplacementButDoNotBlockSpin() {
        for (var board : List.of(new Board(7, 7, Set.of(new Wall(target.position(), Direction.EAST))), new Board(4, 7))) {
            var result = engine(Direction.SOUTH).resolveTurnWithEvents(laser(shooter), state(board));
            assertThat(result.state().vehicleStates().get(1).position()).isEqualTo(target.position());
            assertThat(result.state().vehicleStates().get(1).orientation()).isEqualTo(Direction.SOUTH);
            assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                    RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT, RoundEventType.PUSH_BLOCKED, RoundEventType.TURN);
        }
    }

    @Test void shieldStopsBothEffectsAndDoesNotDrawRandomness() {
        var effects = new RegisterEffects();
        effects.shield(target.vehicle().id());
        var engine = new MovementEngine(new Random() {
            @Override public int nextInt(int bound) { throw new AssertionError("Shield must prevent the draw"); }
        });
        var result = engine.resolveTurnWithEvents(laser(shooter), state(new Board(7, 7)), effects);
        assertThat(result.state().vehicleStates()).containsExactly(shooter, target);
        assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT, RoundEventType.PUSH_BLOCKED);
    }

    @Test void pitCrashesTargetWithoutSpinning() {
        var board = new Board(7, 7, Set.of(), Set.of(new Position(4, 3)));
        var result = engine(Direction.WEST).resolveTurnWithEvents(laser(shooter), state(board));
        assertThat(result.state().vehicleStates().get(1).isActive()).isFalse();
        assertThat(result.state().vehicleStates().get(1).orientation()).isEqualTo(Direction.NORTH);
        assertThat(result.events()).extracting(RoundEvent::type).containsExactly(
                RoundEventType.WEAPON_FIRED, RoundEventType.WEAPON_HIT, RoundEventType.CRASH);
    }

    @Test void chainMovesOneStepAndOnlyTheOriginalTargetSpins() {
        var behind = at(4, 3, Direction.WEST);
        var result = engine(Direction.EAST).resolveTurnWithEvents(laser(shooter),
                new GameState(new Board(7, 7), List.of(shooter, target, behind)));
        assertThat(result.state().vehicleStates().get(1).position()).isEqualTo(new Position(4, 3));
        assertThat(result.state().vehicleStates().get(2).position()).isEqualTo(new Position(5, 3));
        assertThat(result.state().vehicleStates().get(2).orientation()).isEqualTo(Direction.WEST);
        assertThat(result.events()).filteredOn(e -> e.type() == RoundEventType.TURN).singleElement()
                .extracting(RoundEvent::vehicleId).isEqualTo(target.vehicle().id());
    }

    @Test void twoHitsProduceTwoRecordedSpinsInResolutionOrder() {
        var other = at(6, 3, Direction.WEST);
        var result = engine(Direction.EAST).resolveTurnWithEvents(new Turn(List.of(
                new VehicleTurn(shooter, MovementOrder.LASER), new VehicleTurn(other, MovementOrder.LASER))),
                new GameState(new Board(8, 7), List.of(shooter, target, other)));
        assertThat(result.events()).filteredOn(e -> e.type() == RoundEventType.TURN).hasSize(2)
                .allSatisfy(e -> assertThat(e.vehicleId()).isEqualTo(target.vehicle().id()));
        assertThat(result.state().vehicleStates().get(1).position()).isEqualTo(target.position());
    }

    private MovementEngine engine(Direction direction) {
        return new MovementEngine(new Random() {
            @Override public int nextInt(int bound) { assertThat(bound).isEqualTo(4); return direction.ordinal(); }
        });
    }
    private VehicleState at(int x, int y, Direction direction) {
        return new VehicleState(new Vehicle(), new Position(x, y), direction);
    }
    private GameState state(Board board) { return new GameState(board, List.of(shooter, target)); }
    private Turn laser(VehicleState vehicle) { return new Turn(List.of(new VehicleTurn(vehicle, MovementOrder.LASER))); }
}
