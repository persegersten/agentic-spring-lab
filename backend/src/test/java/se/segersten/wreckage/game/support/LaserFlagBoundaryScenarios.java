package se.segersten.wreckage.game.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import se.segersten.wreckage.game.domain.*;
import se.segersten.wreckage.game.support.SequentialFlagScenarios.Scenario;

/** Slice 2 fixtures: real shots, with no random maps or direction-dependent follow-up shots. */
public final class LaserFlagBoundaryScenarios {
    private LaserFlagBoundaryScenarios() {}

    public static boolean supports(Scenario scenario) {
        return scenario == Scenario.THEFT_MIXED || scenario.name().startsWith("THEFT_CAPTURE_")
                || scenario.name().startsWith("THEFT_CHAIN_");
    }

    public static Game configure(Game original, Scenario scenario) {
        boolean chain = scenario.name().startsWith("THEFT_CHAIN_");
        boolean mixed = scenario == Scenario.THEFT_MIXED;
        int hitTick = mixed ? 15 : Integer.parseInt(scenario.name().substring(scenario.name().lastIndexOf('_') + 1));
        Player alice = named(original, "Alice");
        Player bob = named(original, "Bob");
        alice.acquireFlag("CP1", 10);
        if (mixed) {
            alice.acquireFlag("CP2", 14);
            alice.acquireFlag("CP3", 9);
        }
        var states = new LinkedHashMap<UUID, VehicleState>();
        for (Player player : original.getPlayers()) {
            Position position = player == alice ? new Position(2, 1)
                    : player == bob ? new Position(0, 1) : new Position(0, 0);
            Direction direction = player.getName().equals("Carol") ? Direction.NORTH : Direction.EAST;
            var old = original.getVehicleStates().stream()
                    .filter(v -> v.vehicle().playerId().equals(player.getId())).findFirst().orElseThrow();
            states.put(player.getId(), new VehicleState(new Vehicle(old.vehicle().id(), player.getId(), position, direction),
                    position, direction, VehicleStatus.ACTIVE, true));
        }
        Board board = new Board(10, 10, Set.of(), Set.of(), Set.of(
                new Checkpoint("CP1", 1, new Position(6, 1)), new Checkpoint("CP2", 2, new Position(6, 2)),
                new Checkpoint("CP3", 3, new Position(6, 3)), new Checkpoint("CP4", 4, new Position(7, 5))),
                states.values().stream().map(v -> new SpawnPoint(v.position(), v.orientation())).toList(),
                List.of(), List.of(), Set.of());
        Game game = new Game(original.getId(), original.getPlayers(), board, GameStatus.RUNNING, states, null,
                original.getConfiguration(), original.getCreatedAt(), original.getJoinDeadline(),
                original.getHostTokenHash(), original.getHostPlayerId());
        Instant start = Instant.parse("2099-01-01T00:00:00Z");
        game.enableContinuous(start, 2000, 600000);
        var clock = game.getContinuous();
        clock.tick = (chain ? 15 : hitTick) - 1;
        clock.nextTick = start.plusMillis((clock.tick + 1) * clock.intervalMillis);
        clock.queues.put(bob.getId(), CommandQueue.decode("LASER,-,-,-,-"));
        if (chain) {
            // Join order Alice, Carol, Bob rotates to Bob, Alice, Carol at tick 15.
            Player carol = named(original, "Carol");
            if (hitTick > 15) {
                // Execute the first theft and idle ticks normally; no ownership is fabricated for Bob.
                while (clock.tick < hitTick - 1) game.tick(clock.nextTick);
            }
            clock.queues.put(carol.getId(), CommandQueue.decode("LASER,-,-,-,-"));
        }
        return game;
    }

    private static Player named(Game game, String name) {
        return game.getPlayers().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }
}
