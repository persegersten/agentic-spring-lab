package se.segersten.wreckage.game.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

public final class Round {
    private final int number;
    private RoundPhase phase;
    private final Map<UUID, PlayerProgram> programs;
    private final List<UUID> initiative;
    private final GameState initialState;
    private List<RoundEvent> playback;
    private final Instant planningDeadline;
    private final Map<UUID, Integer> initialScores;

    public Round(int number, Map<UUID, PlayerProgram> programs, List<UUID> initiative, GameState initialState) {
        this(number, RoundPhase.PLANNING, programs, initiative, initialState, List.of(), Instant.EPOCH, Map.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback) {
        this(number, phase, programs, initiative, initialState, playback, Instant.EPOCH, Map.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline) {
        this(number, phase, programs, initiative, initialState, playback, planningDeadline, Map.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline, Map<UUID, Integer> initialScores) {
        this.number = number; this.phase = phase;
        this.programs = new LinkedHashMap<>(programs);
        this.initiative = List.copyOf(initiative);
        if (this.initiative.size() != this.programs.size()
                || !new java.util.LinkedHashSet<>(this.initiative).equals(this.programs.keySet())) {
            throw new IllegalArgumentException("Initiative must contain every active player exactly once");
        }
        this.initialState = initialState; this.playback = List.copyOf(playback);this.planningDeadline=planningDeadline;
        this.initialScores = Map.copyOf(initialScores);
    }
    public int number() { return number; }
    public RoundPhase phase() { return phase; }
    public Map<UUID, PlayerProgram> programs() { return Map.copyOf(programs); }
    public List<UUID> initiative() { return initiative; }
    public GameState initialState() { return initialState; }
    public List<RoundEvent> playback() { return playback; }
    public Instant planningDeadline(){return planningDeadline;}
    public Map<UUID, Integer> initialScores() { return initialScores; }
    public void reorder(UUID playerId, List<MovementOrder> orders) {
        if (phase != RoundPhase.PLANNING) throw new IllegalStateException("Round is not accepting programs");
        var current = programs.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player is not part of this round");
        programs.put(playerId, current.edit(orders));
    }
    public void lock(UUID playerId, List<MovementOrder> orders) {
        if (phase != RoundPhase.PLANNING) throw new IllegalStateException("Round is not accepting programs");
        var current = programs.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player is not part of this round");
        programs.put(playerId, current.lock(orders));
    }
    public boolean allReady() { return !programs.isEmpty() && programs.values().stream().allMatch(PlayerProgram::ready); }
    public boolean completeTimedOutPrograms(Instant now){if(phase!=RoundPhase.PLANNING||now.isBefore(planningDeadline))return false;programs.replaceAll((id,p)->p.completeWithWait());return true;}
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine) {
        resolve(engine, List.of(), GameConfiguration.defaults());
    }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine,
                        List<Player> players, GameConfiguration configuration) {
        if (!allReady()) throw new IllegalStateException("Not all players are ready");
        phase = RoundPhase.RESOLVING;
        GameState state = initialState;
        List<RoundEvent> events = new ArrayList<>();
        Map<UUID, Player> playersById = players.stream().collect(java.util.stream.Collectors.toMap(Player::getId, p -> p));
        int cardPositions = programs.values().iterator().next().commands().size();
        for (int index = 0; index < cardPositions; index++) {
            for (UUID playerId : initiative) {
                VehicleState vehicle = state.vehicleStates().stream()
                        .filter(candidate -> candidate.vehicle().playerId().equals(playerId))
                        .filter(VehicleState::isActive)
                        .findFirst().orElse(null);
                if (vehicle != null) {
                    var result = engine.resolveTurnWithEvents(
                            new Turn(List.of(new VehicleTurn(vehicle, programs.get(playerId).commands().get(index)))), state);
                    state = result.state();
                    addScoredEvents(events, result.events(), playersById, configuration);
                }
            }
        }
        playback = List.copyOf(events);
        phase = RoundPhase.PLAYBACK;
    }

    private void addScoredEvents(List<RoundEvent> target, List<RoundEvent> movementEvents,
                                 Map<UUID, Player> players, GameConfiguration configuration) {
        java.util.Set<UUID> rewardedCrashes = new java.util.HashSet<>();
        for (RoundEvent event : movementEvents) {
            target.add(event.withSequence(target.size() + 1));
            Player subject = players.get(event.playerId());
            if (subject != null && (event.type() == RoundEventType.MOVE || event.type() == RoundEventType.RAM
                    || event.type() == RoundEventType.PUSH)) {
                Checkpoint checkpoint = initialState.board().checkpointAt(event.newPosition());
                if (checkpoint != null && subject.visitCheckpoint(checkpoint.id())) {
                    addScoreEvent(target, event, subject, configuration.checkpointScore(),
                            ScoreChangeReason.CHECKPOINT, checkpoint.id());
                }
            }
            if (subject != null && event.type() == RoundEventType.CRASH) {
                addScoreEvent(target, event, subject, configuration.crashPenalty(),
                        ScoreChangeReason.CRASH_PENALTY, null);
                if (!event.playerId().equals(event.sourcePlayerId()) && rewardedCrashes.add(event.playerId())) {
                    Player source = players.get(event.sourcePlayerId());
                    if (source != null) addScoreEvent(target, event, source, configuration.pushCrashScore(),
                            ScoreChangeReason.PUSH_CRASH, null);
                }
            }
        }
    }

    private void addScoreEvent(List<RoundEvent> target, RoundEvent cause, Player player, int delta,
                               ScoreChangeReason reason, String checkpointId) {
        int oldScore = player.getScore();
        int newScore = player.changeScore(delta);
        UUID vehicleId = player.getId().equals(cause.playerId()) ? cause.vehicleId() : cause.sourceVehicleId();
        RoundEvent scoreEvent = RoundEvent.scoreChanged(player.getId(), vehicleId, cause.sourcePlayerId(),
                cause.sourceVehicleId(), cause.newPosition(), cause.newDirection(), oldScore, newScore,
                reason, checkpointId);
        target.add(scoreEvent.withSequence(target.size() + 1));
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
                        event.newDirection(), current.damage(), current.status()));
            } else if (event.type() == RoundEventType.DAMAGE) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), current.position(),
                        current.orientation(), event.newDamage(), current.status()));
            } else if (event.type() == RoundEventType.CRASH) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), event.newPosition(),
                        event.newDirection(), current.damage(), VehicleStatus.CRASHED));
            }
        }
        return List.copyOf(result.values());
    }
}
