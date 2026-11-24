package se.segersten.wreckage.game.domain;

import java.util.List;
import java.util.Objects;

/** Atomic batches are transient resolution metadata; playback remains a flat event list. */
public record MovementResult(GameState state, List<RoundEvent> events, List<List<RoundEvent>> atomicSteps) {
    public MovementResult(GameState state, List<RoundEvent> events) {
        this(state, events, events.isEmpty() ? List.of() : List.of(events));
    }

    public MovementResult {
        Objects.requireNonNull(state); events = List.copyOf(events);
        atomicSteps = atomicSteps.stream().map(List::copyOf).toList();
    }
}
