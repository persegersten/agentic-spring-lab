package se.segersten.wreckage.game.application;

import org.springframework.stereotype.Component;

import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.MovementOrder;

@Component
public class BotPlayerAutomation implements PlayerAutomation {

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
