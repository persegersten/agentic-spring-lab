package se.segersten.wreckage.game.domain;

public enum Direction {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    public Direction turnLeft() {
        return switch (this) {
            case NORTH -> WEST;
            case EAST  -> NORTH;
            case SOUTH -> EAST;
            case WEST  -> SOUTH;
        };
    }

    public Direction turnRight() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST  -> SOUTH;
            case SOUTH -> WEST;
            case WEST  -> NORTH;
        };
    }

    public Direction modeForward() {
        return this;
    }
}
