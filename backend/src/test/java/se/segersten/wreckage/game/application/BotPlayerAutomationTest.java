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
            assertThat(after.subList(0, 3)).isEqualTo(before.subList(0, 3));
            for (int slot = 3; slot < 5; slot++) {
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
        var game = new Game(UUID.randomUUID(), new Board(10, 10));
        game.addPlayer("Human");
        var bot = game.addAutomatedPlayer("Bot", "hash", Instant.EPOCH);
        game.enableContinuous(Instant.EPOCH, 2000, 600000);
        var queue = game.getContinuous().queues.get(bot.getId());
        // This board's second spawn faces south on its open bottom edge.
        queue.append(MovementOrder.FORWARD_1);
        for (int tick = 1; tick <= 4; tick++) game.tick(Instant.EPOCH.plusSeconds(tick * 2));
        assertThat(game.getVehicleStates().get(1).isActive()).isFalse();
        var automation = new BotPlayerAutomation(new Random(42));
        automation.fillBotQueues(game);
        assertThat(queue.snapshot()).containsOnlyNulls();
        game.tick(Instant.EPOCH.plusSeconds(10));
        assertThat(game.getVehicleStates().get(1).isActive()).isTrue();
        automation.fillBotQueues(game);
        assertThat(queue.snapshot().subList(3, 5)).doesNotContainNull();
        queue.clear();
        game.tick(Instant.EPOCH.plusSeconds(600));
        automation.fillBotQueues(game);
        assertThat(queue.snapshot()).containsOnlyNulls();
    }
}
