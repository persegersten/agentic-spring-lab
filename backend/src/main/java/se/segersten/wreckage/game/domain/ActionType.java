package se.segersten.wreckage.game.domain;

public enum ActionType {
    LASER(ActionTiming.POST_MOVEMENT),
    REPULSOR(ActionTiming.POST_MOVEMENT),
    ROCKET(ActionTiming.POST_MOVEMENT),
    TURBO(ActionTiming.POST_MOVEMENT),
    SHIELD(ActionTiming.PRE_MOVEMENT),
    ANCHOR(ActionTiming.PRE_MOVEMENT),
    SIDE_STEP_LEFT(ActionTiming.POST_MOVEMENT),
    SIDE_STEP_RIGHT(ActionTiming.POST_MOVEMENT);

    private final ActionTiming timing;

    ActionType(ActionTiming timing) {
        this.timing = timing;
    }

    public ActionTiming timing() {
        return timing;
    }

    public boolean isWeapon() {
        return this == LASER || this == REPULSOR || this == ROCKET;
    }

    public int weaponRange() {
        return switch (this) {
            case LASER -> 6;
            case REPULSOR -> 3;
            case ROCKET -> 5;
            default -> throw new IllegalStateException(this + " is not a weapon");
        };
    }

    public int weaponDamage() {
        return switch (this) {
            case LASER -> 1;
            case REPULSOR -> 0;
            case ROCKET -> 2;
            default -> throw new IllegalStateException(this + " is not a weapon");
        };
    }
}
