package se.segersten.wreckage.game.api;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Position;
import java.util.Set;

public record BoardResponse(int width, int height, Set<Position> walls) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.width(), board.height(), board.walls());
    }
}
