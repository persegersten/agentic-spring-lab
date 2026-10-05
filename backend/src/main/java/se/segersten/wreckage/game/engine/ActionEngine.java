package se.segersten.wreckage.game.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import se.segersten.wreckage.game.domain.ActionTiming;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.ScheduledAction;

/** Extension point for scheduled actions. Combat effects are intentionally not implemented yet. */
public class ActionEngine {
    public GameState resolve(ActionTiming timing, int registerIndex, GameState state,
                             Map<UUID, PlayerProgram> programs, List<UUID> initiative) {
        for (UUID playerId : initiative) {
            PlayerProgram program = programs.get(playerId);
            ScheduledAction action = program == null ? null : program.scheduledAction();
            if (action != null && action.registerIndex() == registerIndex
                    && action.actionType().timing() == timing
                    && state.vehicleStates().stream().anyMatch(vehicle -> vehicle.isActive()
                            && vehicle.vehicle().playerId().equals(playerId))) {
                encounter(playerId, action, timing, state);
            }
        }
        return state;
    }

    protected void encounter(UUID playerId, ScheduledAction action, ActionTiming timing, GameState state) {
        // Later Combat features implement effects here. Scheduling alone must not alter gameplay.
    }
}
