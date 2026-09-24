package se.segersten.wreckage.game.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Round {
    private final int number;
    private RoundPhase phase;
    private final Map<UUID, PlayerProgram> programs;
    private final GameState initialState;
    private List<RoundEvent> playback;

    public Round(int number, Map<UUID, PlayerProgram> programs, GameState initialState) {
        this(number, RoundPhase.PLANNING, programs, initialState, List.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 GameState initialState, List<RoundEvent> playback) {
        this.number = number; this.phase = phase;
        this.programs = new LinkedHashMap<>(programs);
        this.initialState = initialState; this.playback = List.copyOf(playback);
    }
    public int number() { return number; }
    public RoundPhase phase() { return phase; }
    public Map<UUID, PlayerProgram> programs() { return Map.copyOf(programs); }
    public GameState initialState() { return initialState; }
    public List<RoundEvent> playback() { return playback; }
    public void lock(UUID playerId, List<MovementOrder> orders) {
        if (phase != RoundPhase.PLANNING) throw new IllegalStateException("Round is not accepting programs");
        var current = programs.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player is not part of this round");
        programs.put(playerId, current.lock(orders));
    }
    public boolean allReady() { return !programs.isEmpty() && programs.values().stream().allMatch(PlayerProgram::ready); }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine) {
        resolve(engine, new se.segersten.wreckage.game.engine.CannonEngine(),
                new se.segersten.wreckage.game.engine.BoardEffectEngine());
    }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine,
                        se.segersten.wreckage.game.engine.CannonEngine cannonEngine) {
        resolve(engine, cannonEngine, new se.segersten.wreckage.game.engine.BoardEffectEngine());
    }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine,
                        se.segersten.wreckage.game.engine.CannonEngine cannonEngine,
                        se.segersten.wreckage.game.engine.BoardEffectEngine boardEffectEngine) {
        if (!allReady()) throw new IllegalStateException("Not all players are ready");
        phase = RoundPhase.MOVEMENT_ACTIONS;
        GameState state = initialState;
        List<RoundEvent> events = new ArrayList<>();
        int cardPositions = programs.values().iterator().next().orders().size();
        for (int index = 0; index < cardPositions; index++) {
            List<VehicleTurn> turns = new ArrayList<>();
            for (VehicleState vehicle : state.vehicleStates()) {
                MovementOrder order = programs.get(vehicle.vehicle().playerId()).orders().get(index);
                turns.add(new VehicleTurn(vehicle, order));
            }
            var result = engine.resolveTurnWithEvents(new Turn(turns), state);
            state = result.state();
            for (RoundEvent event : result.events()) events.add(event.withSequence(events.size() + 1));
        }
        var cannonResult = cannonEngine.resolve(state);
        state = cannonResult.state();
        for (RoundEvent event : cannonResult.events()) events.add(event.withSequence(events.size() + 1));
        phase = RoundPhase.BOARD_EFFECTS;
        var boardEffectResult = boardEffectEngine.resolve(state);
        for (RoundEvent event : boardEffectResult.events()) events.add(event.withSequence(events.size() + 1));
        playback = List.copyOf(events);
        phase = RoundPhase.PLAYBACK;
    }

    public List<VehicleState> finalVehicleStates() {
        Map<UUID, VehicleState> result = new LinkedHashMap<>();
        initialState.vehicleStates().forEach(state -> result.put(state.vehicle().id(), state));
        for (RoundEvent event : playback) {
            VehicleState current = result.get(event.vehicleId());
            if (current == null) continue;
            if (event.type() == RoundEventType.MOVE || event.type() == RoundEventType.TURN
                    || event.type() == RoundEventType.RAM || event.type() == RoundEventType.PUSH) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), event.newPosition(),
                        event.newDirection(), current.damage()));
            } else if (event.type() == RoundEventType.DAMAGE) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), current.position(),
                        current.orientation(), event.newDamage()));
            }
        }
        return List.copyOf(result.values());
    }
}
