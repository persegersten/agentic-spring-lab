package se.segersten.wreckage.game.api;

import se.segersten.wreckage.game.domain.Board;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Wall;
import se.segersten.wreckage.game.domain.Checkpoint;
import java.util.Set;

public record BoardResponse(int width, int height, Set<Wall> walls, Set<Position> pits, Set<Checkpoint> checkpoints) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.width(), board.height(), board.walls(), board.pits(), board.checkpoints());
    }
}
