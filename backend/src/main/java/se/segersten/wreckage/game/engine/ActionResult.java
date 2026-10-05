package se.segersten.wreckage.game.engine;

import java.util.List;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.RoundEvent;

public record ActionResult(GameState state, List<RoundEvent> events) {
    public ActionResult {
        events = List.copyOf(events);
    }
}
