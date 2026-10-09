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
    private final List<RoundEvent> startEvents;

    public Round(int number, Map<UUID, PlayerProgram> programs, List<UUID> initiative, GameState initialState) {
        this(number, RoundPhase.PLANNING, programs, initiative, initialState, List.of(), Instant.EPOCH, Map.of(), List.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback) {
        this(number, phase, programs, initiative, initialState, playback, Instant.EPOCH, Map.of(), List.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline) {
        this(number, phase, programs, initiative, initialState, playback, planningDeadline, Map.of(), List.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline, Map<UUID, Integer> initialScores) {
        this(number, phase, programs, initiative, initialState, playback, planningDeadline, initialScores, List.of());
    }
    public Round(int number, RoundPhase phase, Map<UUID, PlayerProgram> programs,
                 List<UUID> initiative, GameState initialState, List<RoundEvent> playback,
                 Instant planningDeadline, Map<UUID, Integer> initialScores, List<RoundEvent> startEvents) {
        this.number = number; this.phase = phase;
        this.programs = new LinkedHashMap<>(programs);
        this.initiative = List.copyOf(initiative);
        if (this.initiative.size() != this.programs.size()
                || !new java.util.LinkedHashSet<>(this.initiative).equals(this.programs.keySet())) {
            throw new IllegalArgumentException("Initiative must contain every active player exactly once");
        }
        this.initialState = initialState; this.playback = List.copyOf(playback);this.planningDeadline=planningDeadline;
        this.initialScores = Map.copyOf(initialScores);
        this.startEvents = List.copyOf(startEvents);
    }
    public int number() { return number; }
    public RoundPhase phase() { return phase; }
    public Map<UUID, PlayerProgram> programs() { return Map.copyOf(programs); }
    public List<UUID> initiative() { return initiative; }
    public GameState initialState() { return initialState; }
    public List<RoundEvent> playback() { return playback; }
    public Instant planningDeadline(){return planningDeadline;}
    public Map<UUID, Integer> initialScores() { return initialScores; }
    public List<RoundEvent> startEvents() { return startEvents; }
    public void reorder(UUID playerId, List<MovementOrder> orders) {
        reorder(playerId, orders, programs.get(playerId) == null ? null : programs.get(playerId).scheduledAction());
    }
    public void reorder(UUID playerId, List<MovementOrder> orders, ScheduledAction action) {
        if (phase != RoundPhase.PLANNING) throw new IllegalStateException("Round is not accepting programs");
        var current = programs.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player is not part of this round");
        programs.put(playerId, current.edit(orders, action));
    }
    public void lock(UUID playerId, List<MovementOrder> orders) {
        lock(playerId, orders, programs.get(playerId) == null ? null : programs.get(playerId).scheduledAction());
    }
    public void lock(UUID playerId, List<MovementOrder> orders, ScheduledAction action) {
        if (phase != RoundPhase.PLANNING) throw new IllegalStateException("Round is not accepting programs");
        var current = programs.get(playerId);
        if (current == null) throw new IllegalArgumentException("Player is not part of this round");
        programs.put(playerId, current.lock(orders, action));
    }
    public boolean allReady() { return !programs.isEmpty() && programs.values().stream().allMatch(PlayerProgram::ready); }
    public boolean completeTimedOutPrograms(Instant now){if(phase!=RoundPhase.PLANNING||now.isBefore(planningDeadline))return false;programs.replaceAll((id,p)->p.completeWithWait());return true;}
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine) {
        resolve(engine,new se.segersten.wreckage.game.engine.BoardEffectEngine(engine),List.of(),GameConfiguration.defaults());
    }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine,
                        List<Player> players, GameConfiguration configuration) {
        resolve(engine,new se.segersten.wreckage.game.engine.BoardEffectEngine(engine),players,configuration);
    }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine,se.segersten.wreckage.game.engine.BoardEffectEngine effects,List<Player> players,GameConfiguration configuration){
        resolve(engine,effects,new se.segersten.wreckage.game.engine.ActionEngine(engine),players,configuration);
    }
    public void resolve(se.segersten.wreckage.game.engine.MovementEngine engine,
                        se.segersten.wreckage.game.engine.BoardEffectEngine effects,
                        se.segersten.wreckage.game.engine.ActionEngine actions,
                        List<Player> players,GameConfiguration configuration){
        if (!allReady()) throw new IllegalStateException("Not all players are ready");
        phase = RoundPhase.RESOLVING;
        GameState state = initialState;
        List<RoundEvent> events = new ArrayList<>();
        Map<UUID, Player> playersById = players.stream().collect(java.util.stream.Collectors.toMap(Player::getId, p -> p));
        int cardPositions = programs.values().iterator().next().commands().size();
        boolean checkpointVictory = false;
        resolution:
        for (int index = 0; index < cardPositions; index++) {
            int registerIndex = index + 1;
            var registerEffects = new se.segersten.wreckage.game.engine.RegisterEffects();
            var preActions = actions.resolve(ActionTiming.PRE_MOVEMENT, registerIndex, state, programs, initiative,
                    registerEffects);
            state = preActions.state();
            checkpointVictory = addScoredEvents(events, preActions.events(), playersById, configuration, false, true, registerIndex);
            if (checkpointVictory) break;
            for (UUID playerId : initiative) {
                VehicleState vehicle = state.vehicleStates().stream()
                        .filter(candidate -> candidate.vehicle().playerId().equals(playerId))
                        .filter(VehicleState::isActive)
                        .findFirst().orElse(null);
                if (vehicle != null) {
                    var result = engine.resolveTurnWithEvents(
                            new Turn(List.of(new VehicleTurn(vehicle, programs.get(playerId).commands().get(index)))),
                            state, registerEffects);
                    state = result.state();
                    checkpointVictory = addScoredEvents(events,result.events(),playersById,configuration,true, false, registerIndex);
                    if (checkpointVictory) break resolution;
                }
            }
            var postActions = actions.resolve(ActionTiming.POST_MOVEMENT, registerIndex, state, programs, initiative,
                    registerEffects);
            state = postActions.state();
            checkpointVictory = addScoredEvents(events, postActions.events(), playersById, configuration, true, true, registerIndex);
            if (checkpointVictory) break;
            var result=effects.resolve(state, registerEffects);state=result.state();addScoredEvents(events,result.events(),playersById,configuration,false, false, registerIndex);
            checkpointVictory = playersById.values().stream().anyMatch(player -> player.hasCompletedCheckpoints(initialState.board()));
            if (checkpointVictory) break;
        }
        if (!checkpointVictory) awardControlPoints(events, state, playersById, configuration);
        playback = List.copyOf(events);
        phase = RoundPhase.PLAYBACK;
    }

    private void awardControlPoints(List<RoundEvent> target, GameState state, Map<UUID, Player> players,
                                    GameConfiguration configuration) {
        Map<UUID, VehicleState> activeByPlayer = state.vehicleStates().stream()
                .filter(VehicleState::isActive)
                .collect(java.util.stream.Collectors.toMap(vehicle -> vehicle.vehicle().playerId(), vehicle -> vehicle));
        for (UUID playerId : initiative) {
            VehicleState vehicle = activeByPlayer.get(playerId);
            Player player = players.get(playerId);
            if (vehicle == null || player == null || !state.board().isControlPoint(vehicle.position())) continue;
            int oldScore = player.getScore();
            int newScore = player.changeScore(configuration.controlPointScore());
            RoundEvent scoreEvent = RoundEvent.scoreChanged(playerId, vehicle.vehicle().id(), playerId,
                    vehicle.vehicle().id(), vehicle.position(), vehicle.orientation(), oldScore, newScore,
                    ScoreChangeReason.CONTROL_POINT, null);
            target.add(scoreEvent.withSequence(target.size() + 1));
        }
    }

    private boolean addScoredEvents(List<RoundEvent> target, List<RoundEvent> movementEvents,
                                 Map<UUID,Player>players,GameConfiguration configuration,boolean rewardPush,
                                 boolean rewardWeapon, int registerIndex) {
        java.util.Set<UUID> rewardedCrashes = new java.util.HashSet<>();
        for (RoundEvent event : movementEvents) {
            event = event.withRegister(registerIndex);
            target.add(event.withSequence(target.size() + 1));
            Player subject = players.get(event.playerId());
            if(subject!=null&&(event.type()==RoundEventType.MOVE||event.type()==RoundEventType.SIDE_STEP||event.type()==RoundEventType.RAM||event.type()==RoundEventType.PUSH||event.type()==RoundEventType.CONVEYOR_MOVE||event.type()==RoundEventType.CONVEYOR_RAM||event.type()==RoundEventType.CONVEYOR_PUSH)){
                Checkpoint checkpoint = initialState.board().checkpointAt(event.newPosition());
                if (checkpoint != null && subject.captureCheckpoint(checkpoint, initialState.board())) {
                    addScoreEvent(target, event, subject, configuration.checkpointScore(),
                            ScoreChangeReason.CHECKPOINT, checkpoint.id());
                    if (subject.hasCompletedCheckpoints(initialState.board())) return true;
                }
            }
            if(subject!=null&&(event.type()==RoundEventType.CRASH||event.type()==RoundEventType.CONVEYOR_CRASH
                    || event.type()==RoundEventType.VEHICLE_CRASHED)){
                subject.recordCrash();
                addScoreEvent(target, event, subject, configuration.crashPenalty(),
                        ScoreChangeReason.CRASH_PENALTY, null);
                boolean weaponEvent = event.actionType() != null && event.actionType().isWeapon();
                if(rewardPush&&!weaponEvent&&!event.playerId().equals(event.sourcePlayerId())&&rewardedCrashes.add(event.playerId())){
                    Player source = players.get(event.sourcePlayerId());
                    if (source != null) addScoreEvent(target, event, source, configuration.pushCrashScore(),
                            ScoreChangeReason.PUSH_CRASH, null);
                }
                if(rewardWeapon&&weaponEvent&&!event.playerId().equals(event.sourcePlayerId())&&rewardedCrashes.add(event.playerId())){
                    Player source = players.get(event.sourcePlayerId());
                    if (source != null) addScoreEvent(target, event, source, configuration.weaponCrashScore(),
                            ScoreChangeReason.WEAPON_CRASH, null);
                }
            }
        }
        return false;
    }

    private void addScoreEvent(List<RoundEvent> target, RoundEvent cause, Player player, int delta,
                               ScoreChangeReason reason, String checkpointId) {
        int oldScore = player.getScore();
        int newScore = player.changeScore(delta);
        UUID vehicleId = player.getId().equals(cause.playerId()) ? cause.vehicleId() : cause.sourceVehicleId();
        RoundEvent scoreEvent = RoundEvent.scoreChanged(player.getId(), vehicleId, cause.sourcePlayerId(),
                cause.sourceVehicleId(), cause.newPosition(), cause.newDirection(), oldScore, newScore,
                reason, checkpointId);
        if (cause.registerIndex() != null) scoreEvent = scoreEvent.withRegister(cause.registerIndex());
        target.add(scoreEvent.withSequence(target.size() + 1));
    }

    public List<VehicleState> finalVehicleStates() {
        Map<UUID, VehicleState> result = new LinkedHashMap<>();
        initialState.vehicleStates().forEach(state -> result.put(state.vehicle().id(), state));
        for (RoundEvent event : playback) {
            VehicleState current = result.get(event.vehicleId());
            if (current == null) continue;
            if (event.type() == RoundEventType.MOVE || event.type() == RoundEventType.TURN
                    ||event.type()==RoundEventType.SIDE_STEP||event.type()==RoundEventType.RAM||event.type()==RoundEventType.PUSH||event.type()==RoundEventType.CONVEYOR_MOVE||event.type()==RoundEventType.CONVEYOR_RAM||event.type()==RoundEventType.CONVEYOR_PUSH||event.type()==RoundEventType.ROTATOR_TURN) {
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), event.newPosition(),
                        event.newDirection(), current.status(), current.damage(), current.rocketAmmo()));
            }else if(event.type()==RoundEventType.DAMAGE_APPLIED){
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), current.position(),
                        current.orientation(), current.status(), event.newDamage(), current.rocketAmmo()));
            }else if(event.type()==RoundEventType.AMMO_CHANGED){
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), current.position(),
                        current.orientation(), current.status(), current.damage(), event.newAmmo()));
            }else if(event.type()==RoundEventType.CRASH||event.type()==RoundEventType.CONVEYOR_CRASH
                    || event.type()==RoundEventType.VEHICLE_CRASHED){
                result.put(event.vehicleId(), new VehicleState(current.vehicle(), event.newPosition(),
                        event.newDirection(), VehicleStatus.CRASHED,
                        event.newDamage() == null ? current.damage() : event.newDamage(), current.rocketAmmo()));
            }
        }
        return List.copyOf(result.values());
    }
}
