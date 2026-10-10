package se.segersten.wreckage.game.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import se.segersten.wreckage.game.engine.BoardEffectEngine;
import se.segersten.wreckage.game.engine.MovementEngine;
import se.segersten.wreckage.game.engine.RegisterEffects;

public final class Round {
    private final int number;
    private RoundPhase phase;
    private final Map<UUID, PlayerProgram> programs;
    private final List<UUID> initiative;
    private final GameState initialState;
    private List<RoundEvent> playback;
    private final Instant planningDeadline;
    private final List<RoundEvent> startEvents;

    public Round(int number, Map<UUID, PlayerProgram> programs, List<UUID> initiative, GameState initialState) {
        this(number, RoundPhase.PLANNING, programs, initiative, initialState, List.of(), Instant.EPOCH, List.of());
    }

    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback) {
        this(number, phase, programs, initiative, initialState, playback, Instant.EPOCH, List.of());
    }

    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline) {
        this(number, phase, programs, initiative, initialState, playback, planningDeadline, List.of());
    }

    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline, List<RoundEvent> startEvents) {
        this.number = number; this.phase = phase; this.programs = new LinkedHashMap<>(programs);
        this.initiative = List.copyOf(initiative); this.initialState = initialState;
        this.playback = List.copyOf(playback); this.planningDeadline = planningDeadline;
        this.startEvents = List.copyOf(startEvents);
        if (this.initiative.size() != this.programs.size()
                || !new java.util.LinkedHashSet<>(this.initiative).equals(this.programs.keySet()))
            throw new IllegalArgumentException("Initiative must contain every active player exactly once");
    }

    public int number() { return number; }
    public RoundPhase phase() { return phase; }
    public Map<UUID, PlayerProgram> programs() { return Map.copyOf(programs); }
    public List<UUID> initiative() { return initiative; }
    public GameState initialState() { return initialState; }
    public List<RoundEvent> playback() { return playback; }
    public Instant planningDeadline() { return planningDeadline; }
    public List<RoundEvent> startEvents() { return startEvents; }

    public void reorder(UUID playerId, List<MovementOrder> orders, boolean shieldSelected) {
        requirePlanningProgram(playerId);
        programs.put(playerId, programs.get(playerId).edit(orders, shieldSelected));
    }

    public void lock(UUID playerId, List<MovementOrder> orders, boolean shieldSelected) {
        requirePlanningProgram(playerId);
        programs.put(playerId, programs.get(playerId).lock(orders, shieldSelected));
    }

    public void lock(UUID playerId, List<MovementOrder> orders) { lock(playerId, orders, false); }

    private void requirePlanningProgram(UUID playerId) {
        if (phase != RoundPhase.PLANNING) throw new IllegalStateException("Round is not accepting programs");
        if (!programs.containsKey(playerId)) throw new IllegalArgumentException("Player is not part of this round");
    }

    public boolean allReady() { return !programs.isEmpty() && programs.values().stream().allMatch(PlayerProgram::ready); }

    public boolean completeTimedOutPrograms(Instant now) {
        if (phase != RoundPhase.PLANNING || now.isBefore(planningDeadline)) return false;
        programs.replaceAll((id, program) -> program.completeWithWait());
        return true;
    }

    public Set<UUID> shieldedPlayers() {
        return programs.values().stream().filter(PlayerProgram::shieldSelected)
                .map(PlayerProgram::playerId).collect(Collectors.toUnmodifiableSet());
    }

    public void resolve(MovementEngine movement, List<Player> players, GameConfiguration configuration) {
        resolve(movement, new BoardEffectEngine(movement), players, configuration);
    }

    public void resolve(MovementEngine movement, BoardEffectEngine boardEffects,
                        List<Player> players, GameConfiguration configuration) {
        if (!allReady()) throw new IllegalStateException("Not all players are ready");
        phase = RoundPhase.RESOLVING;
        GameState state = initialState;
        List<RoundEvent> events = new ArrayList<>();
        Map<UUID, Player> playersById = players.stream().collect(Collectors.toMap(Player::getId, player -> player));
        Set<UUID> shieldedVehicleIds = state.vehicleStates().stream()
                .filter(vehicle -> shieldedPlayers().contains(vehicle.vehicle().playerId()))
                .map(vehicle -> vehicle.vehicle().id()).collect(Collectors.toUnmodifiableSet());

        for (UUID playerId : initiative) {
            if (!shieldedPlayers().contains(playerId)) continue;
            VehicleState vehicle = activeVehicle(state, playerId);
            if (vehicle != null) add(events, new RoundEvent(0, RoundEventType.SHIELD_ACTIVATED,
                    playerId, vehicle.vehicle().id(), vehicle.position(), vehicle.position(),
                    vehicle.orientation(), vehicle.orientation()), null);
        }

        resolution:
        for (int index = 0; index < programs.values().iterator().next().commands().size(); index++) {
            int registerIndex = index + 1;
            RegisterEffects effects = new RegisterEffects();
            shieldedVehicleIds.forEach(effects::shield);
            for (UUID playerId : initiative) {
                VehicleState vehicle = activeVehicle(state, playerId);
                if (vehicle == null) continue;
                var result = movement.resolveTurnWithEvents(
                        new Turn(List.of(new VehicleTurn(vehicle, programs.get(playerId).commands().get(index)))),
                        state, effects);
                state = result.state();
                if (capture(events, result.events(), playersById, registerIndex)) break resolution;
            }
            var result = boardEffects.resolve(state, effects);
            state = result.state();
            if (capture(events, result.events(), playersById, registerIndex)) break;
        }
        playback = List.copyOf(events);
        phase = RoundPhase.PLAYBACK;
    }

    private VehicleState activeVehicle(GameState state, UUID playerId) {
        return state.vehicleStates().stream().filter(VehicleState::isActive)
                .filter(vehicle -> vehicle.vehicle().playerId().equals(playerId)).findFirst().orElse(null);
    }

    private boolean capture(List<RoundEvent> target, List<RoundEvent> source,
                            Map<UUID, Player> players, int registerIndex) {
        for (RoundEvent raw : source) {
            RoundEvent event = raw.withRegister(registerIndex); add(target, event, null);
            Player player = players.get(event.playerId());
            if (player != null && (event.type() == RoundEventType.CRASH || event.type() == RoundEventType.CONVEYOR_CRASH))
                player.recordCrash();
            if (player == null || !isCheckpointMovement(event.type())) continue;
            Checkpoint checkpoint = initialState.board().checkpointAt(event.newPosition());
            if (checkpoint != null && player.captureCheckpoint(checkpoint, initialState.board())) {
                add(target, RoundEvent.checkpointCaptured(event, checkpoint.id()), registerIndex);
                if (player.hasCompletedCheckpoints(initialState.board())) return true;
            }
        }
        return false;
    }

    private boolean isCheckpointMovement(RoundEventType type) {
        return type == RoundEventType.MOVE || type == RoundEventType.RAM || type == RoundEventType.PUSH
                || type == RoundEventType.CONVEYOR_MOVE || type == RoundEventType.CONVEYOR_RAM
                || type == RoundEventType.CONVEYOR_PUSH;
    }

    private void add(List<RoundEvent> events, RoundEvent event, Integer register) {
        if (register != null) event = event.withRegister(register);
        events.add(event.withSequence(events.size() + 1));
    }

    public List<VehicleState> finalVehicleStates() {
        Map<UUID, VehicleState> result = new LinkedHashMap<>();
        initialState.vehicleStates().forEach(state -> result.put(state.vehicle().id(), state));
        for (RoundEvent event : playback) {
            VehicleState current = result.get(event.vehicleId());
            if (current == null) continue;
            if (event.type() == RoundEventType.MOVE || event.type() == RoundEventType.TURN
                    || event.type() == RoundEventType.RAM || event.type() == RoundEventType.PUSH
                    || event.type() == RoundEventType.CONVEYOR_MOVE || event.type() == RoundEventType.CONVEYOR_RAM
                    || event.type() == RoundEventType.CONVEYOR_PUSH || event.type() == RoundEventType.ROTATOR_TURN) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), event.newPosition(),
                        event.newDirection(), current.status()));
            } else if (event.type() == RoundEventType.CRASH || event.type() == RoundEventType.CONVEYOR_CRASH) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), event.newPosition(),
                        event.newDirection(), VehicleStatus.CRASHED));
            }
        }
        return List.copyOf(result.values());
    }
}
