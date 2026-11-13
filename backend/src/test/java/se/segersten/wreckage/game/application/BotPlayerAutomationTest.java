package se.segersten.wreckage.game.application;

import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BotPlayerAutomationTest {
    @Test void fillsOnlyFreeBotSlotsWithEveryValidCommandAndPreservesLockedSlots() {
        var game = new Game(UUID.randomUUID(), new Board(10, 10));
        var human = game.addPlayer("Human");
        var bot = game.addAutomatedPlayer("Bot", "hash", Instant.EPOCH);
        game.enableContinuous(Instant.EPOCH, 2000, 600000);
        var automation = new BotPlayerAutomation(new Random(42));
        var expectedRandom = new Random(42);
        var commands = Arrays.stream(MovementOrder.values()).filter(MovementOrder::isAvailableCommand).toList();
        var queue = game.getContinuous().queues.get(bot.getId());
        var seen = EnumSet.noneOf(MovementOrder.class);
        for (int pass = 0; pass < 50; pass++) {
            var before = queue.snapshot();
            automation.fillBotQueues(game);
            var after = queue.snapshot();
            assertThat(after.subList(0, 2)).isEqualTo(before.subList(0, 2));
            for (int slot = 2; slot < 5; slot++) {
                if (before.get(slot) == null) {
                    assertThat(after.get(slot)).isEqualTo(commands.get(expectedRandom.nextInt(commands.size())));
                    seen.add(after.get(slot));
                } else assertThat(after.get(slot)).isEqualTo(before.get(slot));
            }
            long revision = game.getContinuous().revision;
            automation.fillBotQueues(game);
            assertThat(game.getContinuous().revision).isEqualTo(revision);
            queue.shift();
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(commands);
        assertThat(game.getContinuous().queues.get(human.getId()).snapshot()).containsOnlyNulls();
    }

    @Test void skipsCrashedBotsThenRefillsAfterRespawnAndStopsAtDeadline() {
        var board = new Board(10, 10, Set.of(), Set.of(new Position(1, 0)), Set.of(), List.of(
                new SpawnPoint(new Position(0, 1), Direction.SOUTH), new SpawnPoint(new Position(1, 1), Direction.SOUTH)));
        var game = new Game(UUID.randomUUID(), board);
        game.addPlayer("Human");
        var bot = game.addAutomatedPlayer("Bot", "hash", Instant.EPOCH);
        game.enableContinuous(Instant.EPOCH, 2000, 600000);
        var queue = game.getContinuous().queues.get(bot.getId());
        // The bot's first command enters the pit below its spawn.
        queue.append(MovementOrder.FORWARD_1);
        for (int tick = 1; tick <= 3; tick++) game.tick(Instant.EPOCH.plusSeconds(tick * 2));
        assertThat(game.getVehicleStates().get(1).isActive()).isFalse();
        var automation = new BotPlayerAutomation(new Random(42));
        automation.fillBotQueues(game);
        assertThat(queue.snapshot()).containsOnlyNulls();
        game.tick(Instant.EPOCH.plusSeconds(8));
        assertThat(game.getVehicleStates().get(1).isActive()).isTrue();
        automation.fillBotQueues(game);
        assertThat(queue.snapshot().subList(2, 5)).doesNotContainNull();
        queue.clear();
        game.tick(Instant.EPOCH.plusSeconds(600));
        automation.fillBotQueues(game);
        assertThat(queue.snapshot()).containsOnlyNulls();
    }
}
