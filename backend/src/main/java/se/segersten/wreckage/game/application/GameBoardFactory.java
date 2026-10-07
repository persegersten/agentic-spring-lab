package se.segersten.wreckage.game.application;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.MapDefinition;

public interface GameBoardFactory {

    Board createBoard();
    default Board createBoard(MapDefinition map) { return createBoard(); }
    default Board createBoard(MapDefinition map, int playerCount) { return createBoard(map); }
}
