package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.*;

class EliminatedPlayerResponseTest {
    @Test void damageDoesNotRemoveAPlayerFromV2Planning() {
        Game original = new Game(UUID.randomUUID(), new Board(5, 5));
        Player spectator = original.addPlayer("Spectator", "a");
        original.addPlayer("Alice", "b");
        original.addPlayer("Bob", "c");
        var states = new LinkedHashMap<UUID, VehicleState>();
        original.getVehicleStates().forEach(v -> states.put(v.vehicle().playerId(),
                new VehicleState(v.vehicle(), v.position(), v.orientation(),
                        v.vehicle().playerId().equals(spectator.getId()) ? 3 : 0)));
        Instant now = Instant.now();
        Game game = new Game(original.getId(), original.getPlayers(), original.getBoard(),
                GameStatus.RUNNING, states, null, new GameConfiguration(3, 60, 3, 30),
                now, now.plusSeconds(60));
        game.startRound(java.time.Instant.now());

        PlayerGameResponse response = PlayerGameResponse.from(game, spectator.getId());

        assertThat(response.round().program()).isEmpty();
        assertThat(response.round().state().ready()).hasSize(3).containsKey(spectator.getId());
        assertThat(response.round().state().initialVehicles()).hasSize(3);
        assertThat(response.players()).hasSize(3);
    }
}
