package se.segersten.wreckage.game.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.MapDefinition;

@Component
@Profile("!deterministic-e2e")
public class DefaultGameBoardFactory implements GameBoardFactory {
    private final MapRepository maps;
    public DefaultGameBoardFactory(MapRepository maps) { this.maps = maps; }

    @Override
    public Board createBoard() {
        return maps.defaultFor(9).toBoard();
    }
    @Override public Board createBoard(MapDefinition map) { return map.toBoard(); }
}
