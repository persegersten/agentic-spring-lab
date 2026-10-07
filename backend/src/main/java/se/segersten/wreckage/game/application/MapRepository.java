package se.segersten.wreckage.game.application;

import java.util.List;
import se.segersten.wreckage.game.domain.MapDefinition;
import se.segersten.wreckage.game.domain.MatchSettings;

public interface MapRepository {
    MapDefinition get(String id);
    List<MapDefinition> findAll();

    default MapDefinition defaultFor(int playerCount) {
        MatchSettings settings = MatchSettings.forPlayerCount(playerCount);
        return findAll().stream()
                .filter(map -> map.players().supports(playerCount))
                .filter(map -> map.width() == settings.boardWidth() && map.height() == settings.boardHeight())
                .filter(map -> map.id().startsWith("default-"))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "No default map supports " + playerCount + " players on "
                                + settings.boardWidth() + "x" + settings.boardHeight()));
    }
}
