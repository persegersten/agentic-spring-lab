package se.segersten.wreckage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.RoundPhase;

@ActiveProfiles({"in-memory", "headless-players"})
@SpringBootTest
class HeadlessPlayersProfileIntegrationTest {

    @Autowired
    private GameService gameService;

    @Test
    void runsAConfiguredMultiplayerGameWithOnlyTheFirstPlayerConnected() {
        var game = gameService.createGame(new GameConfiguration(4, 300, 3, 120));
        var human = gameService.addPlayer(game.getId(), "Alice");

        var persisted = gameService.getPlayerGame(game.getId(), human.player().getId(), human.token());
        assertThat(persisted.getPlayers()).extracting(player -> player.getName())
                .containsExactly("Alice", "Headless 1", "Headless 2", "Headless 3");
        assertThat(persisted.getRound().phase()).isEqualTo(RoundPhase.PLANNING);
        assertThat(persisted.getRound().programs().get(human.player().getId()).ready()).isFalse();
        persisted.getPlayers().stream().skip(1).forEach(player ->
                assertThat(persisted.getRound().programs().get(player.getId()).ready()).isTrue());

        var hand = persisted.getRound().programs().get(human.player().getId()).hand();
        gameService.submitProgram(game.getId(), human.player().getId(), human.token(), hand);

        var resolved = gameService.getPlayerGame(game.getId(), human.player().getId(), human.token());
        assertThat(resolved.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);

        gameService.startRound(game.getId(), human.player().getId(), human.token());

        var nextRound = gameService.getPlayerGame(game.getId(), human.player().getId(), human.token());
        assertThat(nextRound.getRound().number()).isEqualTo(2);
        assertThat(nextRound.getRound().programs().get(human.player().getId()).ready()).isFalse();
        nextRound.getPlayers().stream().skip(1).forEach(player -> {
            var program = nextRound.getRound().programs().get(player.getId());
            assertThat(program.ready()).isTrue();
            assertThat(program.orders()).containsExactlyElementsOf(program.hand());
        });
    }
}
