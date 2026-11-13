package se.segersten.wreckage.game.domain;

public enum MovementOrder {
    FORWARD_1,
    FORWARD_2,
    FORWARD_3,
    REVERSE_1,
    TURN_LEFT,
    TURN_RIGHT,
    U_TURN,
    LASER,
    WAIT;

    public boolean isProgrammingCard() {
        return this != WAIT;
    }

    /** Available for new input; retired cards remain readable in persisted programs. */
    public boolean isAvailableCommand() {
        return isProgrammingCard() && this != FORWARD_2 && this != FORWARD_3;
    }
}
