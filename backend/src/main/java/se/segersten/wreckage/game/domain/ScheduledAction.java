package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record ScheduledAction(ActionType actionType, int registerIndex) {
    public ScheduledAction {
        Objects.requireNonNull(actionType, "actionType must not be null");
        if (registerIndex < 1) throw new IllegalArgumentException("registerIndex must be positive");
    }
}
