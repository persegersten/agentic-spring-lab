package se.segersten.wreckage.game.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import se.segersten.wreckage.game.domain.*;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

/** Saved copies execute the same real tick-15 theft before any recovery checks. */
public final class LaserFlagRecoveryScenarios {
    private LaserFlagRecoveryScenarios() {}

    public static boolean supports(Scenario scenario) {
        return scenario.name().startsWith("THEFT_RECOVERY_") || scenario.name().startsWith("THEFT_RESPAWN_")
                || scenario == Scenario.THEFT_FINISHED;
    }

    public static Game configure(Game original, Scenario scenario) {
        Game base = LaserFlagBoundaryScenarios.configure(original, Scenario.THEFT_CHAIN_15);
        boolean respawn = scenario.name().startsWith("THEFT_RESPAWN_");
        var bob = base.getPlayers().stream().filter(p -> p.getName().equals("Bob")).findFirst().orElseThrow();
        var carol = base.getPlayers().stream().filter(p -> p.getName().equals("Carol")).findFirst().orElseThrow();
        var states = new LinkedHashMap<java.util.UUID, VehicleState>();
        for (var state : base.getVehicleStates()) {
            if (respawn && state.vehicle().playerId().equals(bob.getId())) {
                state = new VehicleState(new Vehicle(state.vehicle().id(), bob.getId(), new Position(0, 2), Direction.EAST),
                        state.position(), state.orientation(), VehicleStatus.ACTIVE, true);
            }
            states.put(state.vehicle().playerId(), state);
        }
        var board = base.getBoard();
        if (respawn) board = new Board(board.width(), board.height(), board.walls(), Set.of(new Position(1, 1)),
                board.checkpoints(), states.values().stream().map(v -> new SpawnPoint(v.vehicle().spawnPoint(), v.vehicle().spawnOrientation())).toList(),
                List.of(new Conveyor(new Position(0, 1), Direction.EAST)), List.of(), Set.of());
        Game game = new Game(base.getId(), base.getPlayers(), board, GameStatus.RUNNING, states, null,
                base.getConfiguration(), base.getCreatedAt(), base.getJoinDeadline(), base.getHostTokenHash(), base.getHostPlayerId());
        game.enableContinuous(java.time.Instant.parse("2099-01-01T00:00:00Z"), 2000, 600000);
        var clock = game.getContinuous();
        clock.tick = 14;
        clock.nextTick = clock.nextTick.plusMillis(14 * clock.intervalMillis);
        clock.queues.put(bob.getId(), CommandQueue.decode("LASER,-,-,-,-"));
        if (scenario == Scenario.THEFT_FINISHED) clock.deadline = clock.nextTick;
        game.tick(clock.nextTick); // Bob steals CP1 at 15; optionally crashes on the conveyor afterwards.
        if (scenario != Scenario.THEFT_FINISHED) {
            int hitTick = Integer.parseInt(scenario.name().substring(scenario.name().lastIndexOf('_') + 1));
            while (clock.tick < hitTick - 1) game.tick(clock.nextTick);
            clock.queues.put(carol.getId(), CommandQueue.decode("LASER,-,-,-,-"));
        }
        return game;
    }
}
