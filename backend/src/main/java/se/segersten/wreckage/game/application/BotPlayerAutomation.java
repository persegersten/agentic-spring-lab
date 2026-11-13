package se.segersten.wreckage.game.application;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;
import java.util.UUID;
import java.util.random.RandomGenerator;

import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.MovementOrder;

@Component
public class BotPlayerAutomation implements PlayerAutomation {
    private static final MovementOrder[] COMMANDS = Arrays.stream(MovementOrder.values())
            .filter(MovementOrder::isProgrammingCard).toArray(MovementOrder[]::new);
    private final RandomGenerator random;

    public BotPlayerAutomation() {
        this(RandomGenerator.getDefault());
    }

    @Autowired
    public BotPlayerAutomation(RandomGenerator random) {
        this.random = java.util.Objects.requireNonNull(random);
    }

    @Override
    public void fillBotQueues(Game game) {
        var continuous = game.getContinuous();
        if (continuous == null || game.getStatus() != se.segersten.wreckage.game.domain.GameStatus.RUNNING) return;
        for (var player : game.getPlayers()) {
            if (!player.isAutomated() || game.getVehicleStates().stream()
                    .noneMatch(v -> v.vehicle().playerId().equals(player.getId()) && v.isActive())) continue;
            var queue = continuous.queues.get(player.getId());
            for (int slot = 3; slot < 5; slot++) {
                if (queue.snapshot().get(slot) == null) {
                    continuous.append(player.getId(), UUID.randomUUID(), COMMANDS[random.nextInt(COMMANDS.length)]);
                }
            }
        }
    }

    @Override
    public void lockBotPrograms(Game game) {
        if (game.getRound() == null) return;

        game.getPlayers().stream().filter(se.segersten.wreckage.game.domain.Player::isAutomated).forEach(player -> {
            var program = game.getRound().programs().get(player.getId());
            if (program != null && !program.ready()) {
                if (program.hand().isEmpty()) {
                    game.getRound().lock(player.getId(), java.util.Collections.nCopies(
                            game.getConfiguration().programSize(), MovementOrder.WAIT));
                } else {
                    game.getRound().lock(player.getId(), program.hand().subList(0,
                            game.getConfiguration().programSize()));
                }
            }
        });
    }
}
