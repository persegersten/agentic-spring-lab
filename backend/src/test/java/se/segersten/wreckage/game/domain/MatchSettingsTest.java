package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class MatchSettingsTest {

    @ParameterizedTest
    @CsvSource({
            "2, 10, 10, 7",
            "3, 10, 10, 7",
            "4, 12, 12, 6",
            "6, 12, 12, 6",
            "7, 16, 16, 5",
            "10, 16, 16, 5"
    })
    void derivesSettingsFromActualPlayerCount(int players, int width, int height, int rounds) {
        assertThat(MatchSettings.forPlayerCount(players))
                .isEqualTo(new MatchSettings(width, height, rounds));
    }

    @Test
    void rejectsUnsupportedPlayerCounts() {
        assertThatThrownBy(() -> MatchSettings.forPlayerCount(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchSettings.forPlayerCount(11)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gameUsesActualPlayersInsteadOfMaximumAndFreezesSettingsAtStart() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Game game = new Game(UUID.randomUUID(), List.of(), new Board(20, 20),
                GameStatus.WAITING_FOR_PLAYERS, Map.of(), null,
                new GameConfiguration(10, 60, 5, 30), created, created.plusSeconds(60));
        game.addPlayer("A", "a", created);
        game.addPlayer("B", "b", created);
        game.addPlayer("C", "c", created);
        game.addPlayer("D", "d", created);

        game.start(created.plusSeconds(60));
        assertThat(game.getBoard().width()).isEqualTo(12);
        assertThat(game.getBoard().height()).isEqualTo(12);
        assertThat(game.getConfiguration().roundLimit()).isEqualTo(6);

        game.getRound().initiative().forEach(id -> game.getRound().lock(id, java.util.Collections.nCopies(5, MovementOrder.WAIT)));
        game.getRound().resolve(new se.segersten.wreckage.game.engine.MovementEngine(), game.getPlayers(), game.getConfiguration());
        game.completeRound();
        game.startRound(created.plusSeconds(61));

        assertThat(game.getBoard().width()).isEqualTo(12);
        assertThat(game.getConfiguration().roundLimit()).isEqualTo(6);
    }
}
