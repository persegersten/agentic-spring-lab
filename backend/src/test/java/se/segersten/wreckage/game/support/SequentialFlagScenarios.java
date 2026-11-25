package se.segersten.wreckage.game.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import se.segersten.wreckage.game.domain.*;

/** Reproducible local scenarios shared by engine tests and the test-only HTTP fixture. */
public final class SequentialFlagScenarios {
    public enum Scenario { STANDING, PUSH, REVERSED_PUSH, BLOCKED_PUSH, LASER, CONVEYOR, RECOVERY, FINAL_TIE, FINAL_UNEQUAL }

    private SequentialFlagScenarios() {}

    public static Game configure(Game original, Scenario scenario) {
        Player alice = original.getPlayers().getFirst();
        Player bob = original.getPlayers().get(1);
        Position first = new Position(1, 1);
        Position second = new Position(2, 1);
        Position third = new Position(6, 5);
        Position fourth = new Position(7, 5);
        Position alicePosition = new Position(0, 1);
        Position bobPosition = new Position(1, 1);
        List<Conveyor> conveyors = List.of();
        List<Rotator> rotators = List.of();
        Set<Wall> walls = Set.of();
        Set<Position> pits = Set.of();
        switch (scenario) {
            case STANDING -> bobPosition = second;
            case BLOCKED_PUSH -> walls = Set.of(new Wall(new Position(1, 1), Direction.EAST));
            case RECOVERY -> pits = Set.of(new Position(3, 1));
            case LASER -> {
                first = new Position(3, 1);
                second = new Position(4, 1);
                bobPosition = new Position(2, 1);
            }
            case FINAL_TIE, FINAL_UNEQUAL -> {
                first = new Position(6, 1);
                second = new Position(6, 2);
                third = new Position(6, 3);
                fourth = new Position(1, 1);
                bobPosition = new Position(0, 3);
                conveyors = List.of(new Conveyor(new Position(1, 3), Direction.EAST));
                rotators = List.of(new Rotator(new Position(2, 3), Rotation.CLOCKWISE));
                alice.visitCheckpoint("CP1");
                (scenario == Scenario.FINAL_TIE ? bob : alice).visitCheckpoint("CP2");
                bob.visitCheckpoint("CP3");
                alice.changeScore(-300);
                bob.changeScore(9000);
                for (int crash = 0; crash < 5; crash++) bob.recordCrash();
            }
            case CONVEYOR -> {
                first = new Position(1, 5);
                second = new Position(3, 1);
                bobPosition = new Position(2, 1);
                conveyors = List.of(new Conveyor(bobPosition, Direction.EAST));
                alice.visitCheckpoint("CP1");
                alice.changeScore(original.getConfiguration().checkpointScore());
            }
            default -> { }
        }
        Board board = new Board(10, 10, walls, pits, Set.of(
                new Checkpoint("CP1", 1, first), new Checkpoint("CP2", 2, second),
                new Checkpoint("CP3", 3, third),
                new Checkpoint("CP4", 4, fourth)),
                List.of(new SpawnPoint(alicePosition, Direction.EAST), new SpawnPoint(bobPosition, Direction.EAST)),
                conveyors, rotators, Set.of());
        List<VehicleState> vehicles = new ArrayList<>();
        vehicles.add(vehicle(original, alice, alicePosition));
        vehicles.add(vehicle(original, bob, bobPosition));
        if (scenario == Scenario.REVERSED_PUSH) Collections.reverse(vehicles);
        Map<UUID, VehicleState> states = new LinkedHashMap<>();
        vehicles.forEach(vehicle -> states.put(vehicle.vehicle().playerId(), vehicle));
        Game game = new Game(original.getId(), original.getPlayers(), board, GameStatus.RUNNING, states, null,
                original.getConfiguration(), original.getCreatedAt(), original.getJoinDeadline(),
                original.getHostTokenHash(), original.getHostPlayerId());
        // Only the fixture's explicit tick request advances time. Normal scheduling cannot race the demo.
        game.enableContinuous(Instant.parse("2099-01-01T00:00:00Z"), 2000, 600000);
        return game;
    }

    private static VehicleState vehicle(Game original, Player player, Position position) {
        var old = original.getVehicleStates().stream()
                .filter(vehicle -> vehicle.vehicle().playerId().equals(player.getId())).findFirst().orElseThrow();
        return new VehicleState(new Vehicle(old.vehicle().id(), player.getId(), position, Direction.EAST),
                position, Direction.EAST, VehicleStatus.ACTIVE, true);
    }
}
