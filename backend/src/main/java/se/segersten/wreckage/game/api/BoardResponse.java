package se.segersten.wreckage.game.api;

import se.segersten.wreckage.game.domain.Board;

public record BoardResponse(int width, int height) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.width(), board.height());
    }
}
