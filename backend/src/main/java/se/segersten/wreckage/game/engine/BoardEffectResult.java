package se.segersten.wreckage.game.engine;

import java.util.List;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;

public record BoardEffectResult(GameState state, List<RoundEvent> events, List<List<RoundEvent>> atomicSteps) {
    public BoardEffectResult(GameState state, List<RoundEvent> events) {
        this(state, events, events.isEmpty() ? List.of() : List.of(events));
    }

    public BoardEffectResult {
        events = List.copyOf(events);
        atomicSteps = atomicSteps.stream().map(List::copyOf).toList();
    }
}
