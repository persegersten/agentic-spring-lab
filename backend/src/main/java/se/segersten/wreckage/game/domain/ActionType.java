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
}
