package se.segersten.wreckage.game.domain;

public enum MovementOrder {
    FORWARD_1,
    FORWARD_2,
    FORWARD_3,
    REVERSE_1,
    TURN_LEFT,
    TURN_RIGHT,
    U_TURN,
    WAIT;

    public boolean isProgrammingCard() {
        return this != WAIT;
    }
}
