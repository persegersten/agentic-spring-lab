package se.segersten.wreckage.game.engine;

import java.util.List;
import java.util.Objects;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;

public record CannonResult(GameState state, List<RoundEvent> events) {
    public CannonResult {
        Objects.requireNonNull(state);
        events = List.copyOf(events);
    }
}
