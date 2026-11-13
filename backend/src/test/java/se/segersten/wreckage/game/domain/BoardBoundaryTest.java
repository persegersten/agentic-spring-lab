package se.segersten.wreckage.game.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BoardBoundaryTest {
    @Test void everyOuterEdgeIsAnImplicitWallInBothDirections() {
        for (var board : List.of(new Board(1, 1), new Board(7, 3))) {
            assertThat(board.walls()).isEmpty();
            for (int x = 0; x < board.width(); x++) for (int y = 0; y < board.height(); y++) {
                var cell = new Position(x, y);
                for (var direction : Direction.values()) {
                    var adjacent = cell.move(direction);
                    boolean boundary = !board.isValidPosition(adjacent);
                    assertThat(board.hasWall(cell, direction)).isEqualTo(boundary);
                    assertThat(board.hasWall(adjacent, direction.reverse())).isEqualTo(boundary);
                }
            }
        }
    }

    @Test void blockedEdgeCommandKeepsProgressAndExecutesTheNextQueuedCommandNormally() {
        var board = new Board(4, 4, Set.of(), Set.of(), Set.of(
                new Checkpoint("CP1", 1, new Position(3, 3)), new Checkpoint("CP2", 2, new Position(3, 2))));
        var game = new Game(UUID.randomUUID(), board);
        var player = game.addPlayer("Driver");
        game.addPlayer("Other");
        player.visitCheckpoint("CP1");
        player.changeScore(7);
        var before = game.getVehicleStates().getFirst();
        game.enableContinuous(Instant.EPOCH, 2000, 600000);
        var queue = game.getContinuous().queues.get(player.getId());
        queue.append(MovementOrder.FORWARD_1);
        queue.append(MovementOrder.TURN_LEFT);
        for (int tick = 1; tick <= 3; tick++) game.tick(Instant.EPOCH.plusSeconds(tick * 2));
        assertThat(game.getVehicleStates().getFirst()).isEqualTo(before);
        assertThat(player.getCrashes()).isZero();
        assertThat(player.getScore()).isEqualTo(7);
        assertThat(player.getVisitedCheckpoints()).containsExactly("CP1");
        assertThat(player.getCapturedCheckpointCount(board)).isEqualTo(1);
        assertThat(queue.head()).isEqualTo(MovementOrder.TURN_LEFT);
        game.tick(Instant.EPOCH.plusSeconds(8));
        assertThat(game.getVehicleStates().getFirst().position()).isEqualTo(before.position());
        assertThat(game.getVehicleStates().getFirst().orientation()).isEqualTo(before.orientation().turnLeft());
        assertThat(game.getVehicleStates().getFirst().isActive()).isTrue();
    }
}
