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
    private List<PlaybackStep> playback;

    public Round(int number, Map<UUID, PlayerProgram> programs, GameState initialState) {
        this(number, RoundPhase.PROGRAMMING, programs, initialState, List.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 GameState initialState, List<PlaybackStep> playback) {
        this.number = number; this.phase = phase;
        this.programs = new LinkedHashMap<>(programs);
        this.initialState = initialState; this.playback = List.copyOf(playback);
    }
    public int number() { return number; }
    public RoundPhase phase() { return phase; }
    public Map<UUID, PlayerProgram> programs() { return Map.copyOf(programs); }
    public GameState initialState() { return initialState; }
    public List<PlaybackStep> playback() { return playback; }
    public void lock(UUID playerId, List<MovementOrder> orders) {
        if (phase != RoundPhase.PROGRAMMING) throw new IllegalStateException("Round is not accepting programs");
        var current = programs.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player is not part of this round");
        programs.put(playerId, current.lock(orders));
    }
    public boolean allReady() { return !programs.isEmpty() && programs.values().stream().allMatch(PlayerProgram::ready); }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine) {
        if (!allReady()) throw new IllegalStateException("Not all players are ready");
        phase = RoundPhase.RESOLVING;
        GameState state = initialState;
        List<PlaybackStep> steps = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            List<VehicleTurn> turns = new ArrayList<>();
            Map<UUID, MovementOrder> commands = new LinkedHashMap<>();
            for (VehicleState vehicle : state.vehicleStates()) {
                MovementOrder order = programs.get(vehicle.vehicle().playerId()).orders().get(index);
                commands.put(vehicle.vehicle().playerId(), order);
                turns.add(new VehicleTurn(vehicle, order));
            }
            state = engine.resolveTurn(new Turn(turns), state);
            steps.add(new PlaybackStep(index + 1, commands, state.vehicleStates()));
        }
        playback = List.copyOf(steps);
        phase = RoundPhase.PLAYBACK;
    }
}
