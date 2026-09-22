package se.segersten.wreckage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import se.segersten.wreckage.game.application.GameService;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameConfiguration;
import se.segersten.wreckage.game.domain.RoundPhase;

@ActiveProfiles("in-memory")
@SpringBootTest
class InMemoryProfileIntegrationTest {

    @Autowired
    private GameService gameService;

    @Test
    void createsAndRetrievesGameUsingInMemoryDatabase() {
        Game created = gameService.createGame();

        Game retrieved = gameService.getGame(created.getId());

        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getBoard()).isEqualTo(created.getBoard());
        assertThat(retrieved.getPlayers()).isEmpty();
    }

    @Test
    void persistsResolvedProgramsAndPlaybackUsingInMemoryDatabase() {
        Game game = gameService.createGame(new GameConfiguration(2, 300, 5, 120));
        var alice = gameService.addPlayer(game.getId(), "Alice");
        var bob = gameService.addPlayer(game.getId(), "Bob");

        Game aliceView = gameService.getPlayerGame(game.getId(), alice.player().getId(), alice.token());
        Game bobView = gameService.getPlayerGame(game.getId(), bob.player().getId(), bob.token());
        gameService.submitProgram(game.getId(), alice.player().getId(), alice.token(),
                aliceView.getRound().programs().get(alice.player().getId()).hand());
        gameService.submitProgram(game.getId(), bob.player().getId(), bob.token(),
                bobView.getRound().programs().get(bob.player().getId()).hand());

        Game retrieved = gameService.getGame(game.getId());
        assertThat(retrieved.getRound().phase()).isEqualTo(RoundPhase.PLAYBACK);
        assertThat(retrieved.getRound().allReady()).isTrue();
        assertThat(retrieved.getRound().playback()).hasSize(5);
        assertThat(retrieved.getRound().programs().get(alice.player().getId()).orders()).hasSize(5);
        assertThat(retrieved.getVehicleStates()).hasSize(2);
        assertThat(retrieved.getRound().playback().getLast().vehicleStates()).hasSize(2);
    }
}
