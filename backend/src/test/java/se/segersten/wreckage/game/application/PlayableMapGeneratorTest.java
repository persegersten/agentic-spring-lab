package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.segersten.wreckage.game.domain.Checkpoint;
import se.segersten.wreckage.game.domain.CorridorGenerationParameters;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.MapDefinition;
import se.segersten.wreckage.game.domain.MatchSettings;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.MovementResult;
import se.segersten.wreckage.game.domain.Player;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.Turn;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleTurn;
import se.segersten.wreckage.game.engine.MovementEngine;

class PlayableMapGeneratorTest {
    private static final CorridorGenerationParameters PARAMETERS = CorridorGenerationParameters.defaults();
    private final PlayableMapGenerator generator = new PlayableMapGenerator();
    private final MapValidator mapValidator = new MapValidator();
    private final CorridorGeometryValidator geometryValidator = new CorridorGeometryValidator();

    @Test void supportedConfigurationsProduceCompleteValidMaps() {
        for (int players = 2; players <= 10; players++) {
            for (long seed : List.of(0L, 42L, 998_122L)) {
                String context = "seed=" + seed + ", players=" + players + ", parameters=" + PARAMETERS;
                MapDefinition map = generator.generate(seed, players, PARAMETERS);
                MatchSettings settings = MatchSettings.forPlayerCount(players);
                assertThat(map.width()).as(context).isEqualTo(settings.boardWidth());
                assertThat(map.height()).as(context).isEqualTo(settings.boardHeight());
                assertThat(map.players().min()).as(context).isEqualTo(players);
                assertThat(map.players().max()).as(context).isEqualTo(players);
                assertThat(map.starts()).as(context).hasSize(players);
                assertThat(map.checkpoints()).as(context).extracting(Checkpoint::id)
                        .containsExactly("CP1", "CP2", "CP3", "CP4");
                assertThat(map.checkpoints()).as(context).extracting(Checkpoint::order)
                        .containsExactly(1, 2, 3, 4);
                mapValidator.validate(map);

                CorridorNetwork network = networkOf(map);
                geometryValidator.validate(network);
                assertThat(map.obstacles()).as(context)
                        .containsExactlyElementsOf(network.blockedPositionsInRowMajorOrder());
                Set<Position> checkpoints = map.checkpoints().stream().map(Checkpoint::position)
                        .collect(java.util.stream.Collectors.toSet());
                assertThat(checkpoints).as(context).hasSize(4);
                assertThat(map.starts()).as(context).allSatisfy(start -> {
                    assertThat(network.isTraversable(start.position())).isTrue();
                    assertThat(checkpoints).doesNotContain(start.position());
                    assertThat(network.orthogonalTraversableNeighbors(start.position())).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(network.isTraversable(start.position().move(start.orientation()))).isTrue();
                });
                assertThat(map.starts()).extracting(start -> start.position()).doesNotHaveDuplicates();

                Map<Position, Integer> distances = distances(network, map.checkpoints().getFirst().position());
                int minimum = map.starts().stream().mapToInt(start -> distances.get(start.position())).min().orElseThrow();
                int maximum = map.starts().stream().mapToInt(start -> distances.get(start.position())).max().orElseThrow();
                assertThat(maximum - minimum).as(context + ", CP1 distance spread").isLessThanOrEqualTo(2);
            }
        }
    }

    @Test void completeGenerationIsDeterministicAndSeedSensitive() {
        MapDefinition first = generator.generate(771_992L, 6, PARAMETERS);
        assertThat(generator.generate(771_992L, 6, PARAMETERS)).isEqualTo(first);
        assertThat(generator.generate(771_993L, 6, PARAMETERS)).isNotEqualTo(first);
    }

    @Test void rejectsUnsupportedDimensionsAndImpossiblePlacementsClearly() {
        assertThatThrownBy(() -> generator.generate(12, 12, 3, 1L, PARAMETERS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("do not match");

        Set<Position> shortLine = Set.of(new Position(0, 0), new Position(1, 0), new Position(2, 0),
                new Position(3, 0), new Position(4, 0));
        assertThatThrownBy(() -> generator.createMap(new CorridorNetwork(10, 10, shortLine), 2, 9L, PARAMETERS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed=9", "players=2", "after 5 attempts", "reason=");
    }

    @Test void generatedMapInitializesGameAndUsesExistingMovementRules() {
        MapDefinition map = generator.generate(123_456L, 2, PARAMETERS);
        Game game = new Game(UUID.randomUUID(), map.toBoard());
        Player first = game.addPlayer("one");
        Player second = game.addPlayer("two");
        assertThat(game.getBoard().width()).isEqualTo(10);
        assertThat(game.getBoard().height()).isEqualTo(10);
        assertThat(game.getVehicleStates()).extracting(VehicleState::position)
                .containsExactly(map.starts().get(0).position(), map.starts().get(1).position());
        assertThat(game.getVehicleStates()).extracting(VehicleState::orientation)
                .containsExactly(map.starts().get(0).orientation(), map.starts().get(1).orientation());
        assertThat(game.getBoard().orderedCheckpoints()).containsExactlyElementsOf(map.checkpoints());

        VehicleState vehicle = game.getVehicleStates().stream()
                .filter(state -> state.vehicle().playerId().equals(first.getId())).findFirst().orElseThrow();
        MovementResult move = new MovementEngine().resolveTurnWithEvents(
                new Turn(List.of(new VehicleTurn(vehicle, MovementOrder.FORWARD_1))),
                new se.segersten.wreckage.game.domain.GameState(game.getBoard(), List.of(vehicle)));
        assertThat(move.state().vehicleStates().getFirst().position())
                .isEqualTo(vehicle.position().move(vehicle.orientation()));

        Position obstacle = map.obstacles().stream().filter(blocked -> java.util.Arrays.stream(Direction.values())
                .map(blocked::move).anyMatch(position -> game.getBoard().isValidPosition(position)
                        && !game.getBoard().isObstacle(position))).findFirst().orElseThrow();
        Direction intoObstacle = java.util.Arrays.stream(Direction.values())
                .filter(direction -> !game.getBoard().isObstacle(obstacle.move(direction.reverse()))
                        && game.getBoard().isValidPosition(obstacle.move(direction.reverse())))
                .findFirst().orElseThrow();
        Position besideObstacle = obstacle.move(intoObstacle.reverse());
        VehicleState blockedVehicle = new VehicleState(vehicle.vehicle(), besideObstacle, intoObstacle,
                vehicle.status(), vehicle.shieldAvailable());
        MovementResult blockedMove = new MovementEngine().resolveTurnWithEvents(
                new Turn(List.of(new VehicleTurn(blockedVehicle, MovementOrder.FORWARD_1))),
                new se.segersten.wreckage.game.domain.GameState(game.getBoard(), List.of(blockedVehicle)));
        assertThat(blockedMove.state().vehicleStates().getFirst().position()).isEqualTo(besideObstacle);

        assertThat(second.getCapturedCheckpointCount(game.getBoard())).isZero();
        for (Checkpoint checkpoint : game.getBoard().orderedCheckpoints()) {
            assertThat(second.captureCheckpoint(checkpoint, game.getBoard())).isTrue();
        }
        assertThat(second.hasCompletedCheckpoints(game.getBoard())).isTrue();
    }

    private CorridorNetwork networkOf(MapDefinition map) {
        Set<Position> blocked = Set.copyOf(map.obstacles());
        Set<Position> traversable = new HashSet<>();
        for (int y = 0; y < map.height(); y++) for (int x = 0; x < map.width(); x++) {
            Position position = new Position(x, y);
            if (!blocked.contains(position)) traversable.add(position);
        }
        return new CorridorNetwork(map.width(), map.height(), traversable);
    }

    private Map<Position, Integer> distances(CorridorNetwork network, Position origin) {
        Map<Position, Integer> result = new HashMap<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        result.put(origin, 0);
        queue.add(origin);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            for (Position next : network.orthogonalTraversableNeighbors(current)) {
                if (result.putIfAbsent(next, result.get(current) + 1) == null) queue.add(next);
            }
        }
        return result;
    }
}
