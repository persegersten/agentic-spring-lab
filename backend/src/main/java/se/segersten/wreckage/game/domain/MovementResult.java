package se.segersten.wreckage.game.domain;

import java.util.List;
import java.util.Objects;

public record MovementResult(GameState state, List<RoundEvent> events) {
    public MovementResult {
        Objects.requireNonNull(state); events = List.copyOf(events);
    }
}
