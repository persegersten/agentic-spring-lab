package se.segersten.wreckage.game.engine;

import java.util.List;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;

public record BoardEffectResult(GameState state, List<RoundEvent> events) {
    public BoardEffectResult {
        events = List.copyOf(events);
    }
}
