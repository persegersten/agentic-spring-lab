package se.segersten.wreckage.game.domain;

public enum SpecialAbility {
    TURBO,
    SHIELD,
    SIDE_STEP,
    ANCHOR;

    public boolean permits(ActionType action) {
        if (action == null) return false;
        return this == SIDE_STEP
                ? action == ActionType.SIDE_STEP_LEFT || action == ActionType.SIDE_STEP_RIGHT
                : action.name().equals(name());
    }
}
