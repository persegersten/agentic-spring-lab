package se.segersten.wreckage.game.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ContinuousShieldTest {
    @Test void shieldStartsNextTickProtectsExactlyFiveTicksAndBlocksLaserSpin() {
        var game = game(Set.of());
        var shooter = game.getPlayers().getFirst();
        var target = game.getPlayers().get(1);
        var request = UUID.randomUUID();
        game.activateContinuousShield(target.getId(), request);
        assertThat(game.isShieldAvailable(target.getId())).isFalse();
        assertThat(game.getContinuous().shieldPending(target.getId())).isTrue();
        assertThat(game.getContinuous().shieldTicksRemaining(target.getId())).isEqualTo(5);
        for (int tick = 1; tick <= 6; tick++) {
            head(game, shooter.getId(), MovementOrder.LASER);
            game.tick(Instant.EPOCH.plusSeconds(tick * 2));
            var vehicle = game.getVehicleStates().get(1);
            assertThat(game.getContinuous().shieldTicksRemaining(target.getId())).isEqualTo(Math.max(0, 5 - tick));
            assertThat(game.getRound().playback()).filteredOn(e -> e.type() == RoundEventType.SHIELD_ACTIVATED).hasSize(tick == 1 ? 1 : 0);
            if (tick <= 5) {
                assertThat(vehicle.position()).isEqualTo(new Position(3, 3));
                assertThat(vehicle.orientation()).isEqualTo(Direction.NORTH);
                assertThat(game.getRound().playback()).extracting(RoundEvent::type).contains(RoundEventType.PUSH_BLOCKED).doesNotContain(RoundEventType.TURN);
            } else {
                assertThat(vehicle.position()).isEqualTo(new Position(4, 3));
                assertThat(game.getRound().playback()).extracting(RoundEvent::type).contains(RoundEventType.TURN);
            }
        }
        long revision = game.getContinuous().revision;
        game.activateContinuousShield(target.getId(), request);
        assertThat(game.getContinuous().revision).isEqualTo(revision);
        assertThatThrownBy(() -> game.activateContinuousShield(target.getId(), UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test void shieldAllowsSelfMovementAndCrashDoesNotRefundOrPauseItsDuration() {
        var game = game(Set.of(new Position(3, 4)));
        var player = game.getPlayers().get(1);
        game.activateContinuousShield(player.getId(), UUID.randomUUID());
        head(game, player.getId(), MovementOrder.FORWARD_1);
        game.tick(Instant.EPOCH.plusSeconds(2));
        assertThat(game.getVehicleStates().get(1).isActive()).isFalse();
        assertThat(game.getContinuous().shieldTicksRemaining(player.getId())).isEqualTo(4);
        assertThat(game.isShieldAvailable(player.getId())).isFalse();
        for (int tick = 2; tick <= 5; tick++) game.tick(Instant.EPOCH.plusSeconds(tick * 2));
        assertThat(game.getVehicleStates().get(1).isActive()).isTrue();
        assertThat(game.getContinuous().shieldTicksRemaining(player.getId())).isZero();
        assertThat(game.isShieldAvailable(player.getId())).isFalse();
    }

    @Test void persistenceRetainsPendingActiveAndExpiredReceiptsAndReadsOldPayloads() {
        var game = game(Set.of());
        var player = game.getPlayers().getFirst();
        var request = UUID.randomUUID();
        var c = game.getContinuous();
        assertThat(ContinuousExecution.decode(c.encode()).shieldTicksRemaining(player.getId())).isZero();
        game.activateContinuousShield(player.getId(), request);
        c = ContinuousExecution.decode(c.encode());
        assertThat(c.shieldPending(player.getId())).isTrue();
        assertThat(c.duplicateShield(player.getId(), request)).isTrue();
        c.tick = 2;
        c = ContinuousExecution.decode(c.encode());
        assertThat(c.shieldPending(player.getId())).isFalse();
        assertThat(c.shieldTicksRemaining(player.getId())).isEqualTo(3);
        c.tick = 10;
        c = ContinuousExecution.decode(c.encode());
        assertThat(c.duplicateShield(player.getId(), request)).isTrue();
        assertThat(c.shieldTicksRemaining(player.getId())).isZero();
        var restored = c;
        assertThatThrownBy(() -> restored.duplicate(player.getId(), request, MovementOrder.LASER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> restored.scheduleShield(player.getId(), UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    private Game game(Set<Position> pits) {
        var board = new Board(7, 7, Set.of(), pits, Set.of(), List.of(
                new SpawnPoint(new Position(1, 3), Direction.EAST), new SpawnPoint(new Position(3, 3), Direction.NORTH)));
        var game = new Game(UUID.randomUUID(), board);
        game.addPlayer("Shooter"); game.addPlayer("Target");
        game.enableContinuous(Instant.EPOCH, 2000, 600000);
        return game;
    }
    private void head(Game game, UUID player, MovementOrder command) {
        var queue = game.getContinuous().queues.get(player);
        queue.clear(); queue.append(command);
        for (int i = 0; i < 3; i++) queue.shift();
    }
}
