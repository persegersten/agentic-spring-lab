package se.segersten.wreckage.game.api;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ContinuousSnapshotTest {
    @Test void reportsConfirmedCommandsIncludingNopBlockedMovementAndCrashes() {
        for (boolean wall : List.of(false, true)) {
            var board = new Board(10, 10, wall ? Set.of(new Wall(new Position(0, 0), Direction.SOUTH)) : Set.of());
            var game = new Game(UUID.randomUUID(), board);
            var player = game.addPlayer("A");
            game.addPlayer("B");
            game.enableContinuous(Instant.EPOCH, 2000, 600000);
            assertThat(PlayerGameResponse.from(game, player.getId()).continuous().lastExecutedCommand()).isNull();
            game.getContinuous().append(player.getId(), UUID.randomUUID(), MovementOrder.FORWARD_1);
            for (int tick = 1; tick <= 4; tick++) {
                game.tick(Instant.EPOCH.plusSeconds(tick * 2));
                var snapshot = PlayerGameResponse.from(game, player.getId()).continuous();
                assertThat(snapshot.tick()).isEqualTo(tick);
                assertThat(snapshot.lastExecutedCommand()).isEqualTo(tick == 4 ? MovementOrder.FORWARD_1 : MovementOrder.WAIT);
            }
            assertThat(game.getVehicleStates().getFirst().isActive()).isEqualTo(wall);
            assertThat(game.getVehicleStates().getFirst().position()).isEqualTo(wall ? new Position(0, 0) : new Position(0, -1));
            game.tick(Instant.EPOCH.plusSeconds(10));
            assertThat(PlayerGameResponse.from(game, player.getId()).continuous().lastExecutedCommand()).isEqualTo(MovementOrder.WAIT);
        }
    }
}
