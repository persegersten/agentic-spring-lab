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

@Component
@Profile("headless-players")
public class HeadlessPlayerAutomation implements PlayerAutomation {

    @Override
    public void fillLobby(Game game, Instant now) {
        if (game.getPlayers().size() != 1) return;

        int nameIndex = 1;
        while (game.getPlayers().size() < game.getConfiguration().maxPlayers()) {
            String name;
            do {
                name = "Headless " + nameIndex++;
            } while (hasPlayerNamed(game, name));
            game.addPlayer(name, randomTokenHash(), now);
        }
    }

    @Override
    public void lockHeadlessPrograms(Game game) {
        if (game.getRound() == null) return;

        game.getPlayers().stream().skip(1).forEach(player -> {
            var program = game.getRound().programs().get(player.getId());
            if (program != null && !program.ready()) {
                game.getRound().lock(player.getId(), program.hand());
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
