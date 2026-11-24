package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.engine.MovementEngine;

class ExclusiveFirstFlagTest {
    private final Player alice = Player.create(UUID.randomUUID(), "Alice", "a");
    private final Player bob = Player.create(UUID.randomUUID(), "Bob", "b");
    private final Board board = new Board(6, 4, Set.of(), Set.of(), Set.of(
            new Checkpoint("first", 1, new Position(1, 1)),
            new Checkpoint("second", 2, new Position(2, 1)),
            new Checkpoint("third", 3, new Position(3, 1)),
            new Checkpoint("fourth", 4, new Position(4, 1))));

    @Test
    void ownerOutsideTheActiveInitiativeStillPreventsAnotherClaim() {
        alice.visitCheckpoint("first");
        Round round = move(bob, new Position(0, 1), Direction.EAST, MovementOrder.FORWARD_1);

        assertThat(alice.getVisitedCheckpoints()).containsExactly("first");
        assertThat(bob.getVisitedCheckpoints()).isEmpty();
        assertThat(bob.getScore()).isZero();
        assertThat(round.playback()).noneMatch(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT);
    }

    @Test
    void laterFlagsAwardNothingBeforeOrAfterFirstCapture() {
        move(bob, new Position(1, 1), Direction.EAST, MovementOrder.FORWARD_1);
        assertThat(bob.getVisitedCheckpoints()).isEmpty();
        move(alice, new Position(0, 1), Direction.EAST, MovementOrder.FORWARD_1);
        for (int x = 1; x <= 3; x++) {
            move(alice, new Position(x, 1), Direction.EAST, MovementOrder.FORWARD_1);
            move(bob, new Position(x, 1), Direction.EAST, MovementOrder.FORWARD_1);
        }
        assertThat(alice.getVisitedCheckpoints()).containsExactly("first");
        assertThat(alice.getScore()).isEqualTo(GameConfiguration.DEFAULT_CHECKPOINT_SCORE);
        assertThat(bob.getVisitedCheckpoints()).isEmpty();
        assertThat(bob.getScore()).isZero();
    }

    @Test
    void standingOnOrTurningOnFirstFlagDoesNotCaptureIt() {
        move(alice, new Position(1, 1), Direction.EAST, MovementOrder.WAIT);
        move(alice, new Position(1, 1), Direction.EAST, MovementOrder.TURN_LEFT);
        assertThat(alice.getVisitedCheckpoints()).isEmpty();
    }

    @Test
    void firstEntrantWinsAccordingToInitiative() {
        for (boolean aliceFirst : List.of(true, false)) {
            Player first = Player.create(UUID.randomUUID(), "First", "f");
            Player second = Player.create(UUID.randomUUID(), "Second", "s");
            var a = state(first, new Position(0, 1), Direction.EAST);
            var b = state(second, new Position(1, 2), Direction.SOUTH);
            Round round = new Round(1, Map.of(first.getId(), program(first, MovementOrder.FORWARD_1),
                    second.getId(), program(second, MovementOrder.FORWARD_1)),
                    aliceFirst ? List.of(first.getId(), second.getId()) : List.of(second.getId(), first.getId()),
                    new GameState(board, List.of(a, b)));
            round.resolve(new MovementEngine(), List.of(first, second), GameConfiguration.defaults());
            assertThat((aliceFirst ? first : second).getVisitedCheckpoints()).containsExactly("first");
            assertThat((aliceFirst ? second : first).getVisitedCheckpoints()).isEmpty();
            assertThat(round.playback()).filteredOn(event -> event.scoreReason() == ScoreChangeReason.CHECKPOINT)
                    .hasSize(1);
        }
    }

    private Round move(Player player, Position position, Direction direction, MovementOrder order) {
        Round round = new Round(1, Map.of(player.getId(), program(player, order)), List.of(player.getId()),
                new GameState(board, List.of(state(player, position, direction))));
        round.resolve(new MovementEngine(), List.of(alice, bob), GameConfiguration.defaults());
        return round;
    }

    private PlayerProgram program(Player player, MovementOrder command) {
        return new PlayerProgram(player.getId(), 1, List.of(command), true);
    }

    private VehicleState state(Player player, Position position, Direction direction) {
        return new VehicleState(new Vehicle(UUID.randomUUID(), player.getId()), position, direction);
    }
}
