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
import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameStatus;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Vehicle;
import se.segersten.wreckage.game.domain.VehicleState;

@ActiveProfiles("in-memory")
@SpringBootTest
class InMemoryProfileIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

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
        assertThat(retrieved.getRound().playback()).allSatisfy(event -> {
            assertThat(event.sequence()).isPositive();
            assertThat(event.oldPosition()).isNotNull();
            assertThat(event.newPosition()).isNotNull();
        });
        assertThat(retrieved.getRound().programs().get(alice.player().getId()).orders()).hasSize(5);
        assertThat(retrieved.getVehicleStates()).hasSize(2);
        assertThat(retrieved.getRound().finalVehicleStates()).hasSize(2);
    }

    @Test
    void persistsWallsAndVehicleDamageUsingInMemoryDatabase() {
        var now = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var playerId = java.util.UUID.randomUUID();
        var vehicle = new Vehicle(java.util.UUID.randomUUID(), playerId);
        var state = new VehicleState(vehicle, new Position(1, 1), Direction.EAST, 3);
        var game = new Game(java.util.UUID.randomUUID(),
                java.util.List.of(Player.create(playerId, "Alice", "token")),
                new Board(5, 5, java.util.Set.of(new Position(3, 1))), GameStatus.RUNNING,
                java.util.Map.of(playerId, state), null, GameConfiguration.defaults(), now, now.plusSeconds(60));

        gameRepository.save(game);
        Game retrieved = gameService.getGame(game.getId());

        assertThat(retrieved.getBoard().walls()).containsExactly(new Position(3, 1));
        assertThat(retrieved.getVehicleStates()).extracting(VehicleState::damage).containsExactly(3);
    }
}
