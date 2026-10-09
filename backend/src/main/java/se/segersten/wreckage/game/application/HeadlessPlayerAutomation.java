package se.segersten.wreckage.game.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.MovementOrder;

@Component
@Profile("headless-players")
public class HeadlessPlayerAutomation implements PlayerAutomation {

    @Override
    public boolean addHeadlessPlayer(Game game, Instant now) {
        if (game.getStatus() != se.segersten.wreckage.game.domain.GameStatus.WAITING_FOR_PLAYERS
                || game.getPlayers().size() >= Math.min(9, game.getConfiguration().maxPlayers())) return false;
        int nameIndex = 1;
        String name;
        do {
            name = "Headless " + nameIndex++;
        } while (hasPlayerNamed(game, name));
        game.addAutomatedPlayer(name, randomTokenHash(), now);
        return true;
    }

    @Override
    public void lockHeadlessPrograms(Game game) {
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

    private static boolean hasPlayerNamed(Game game, String name) {
        return game.getPlayers().stream().anyMatch(player -> player.getName().equals(name));
    }

    private static String randomTokenHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
