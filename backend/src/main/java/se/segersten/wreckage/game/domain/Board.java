package se.segersten.wreckage.game.domain;

public record Board(int width, int height) {

    public boolean isValidPosition(Position position) {
        if (position.x() < 0 || position.x() >= width ||
            position.y() < 0 || position.y() >= height) {
            return false;
        }
        return true;
    }
}
