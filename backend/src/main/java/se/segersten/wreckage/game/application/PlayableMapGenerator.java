package se.segersten.wreckage.game.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import se.segersten.wreckage.game.domain.Checkpoint;
import se.segersten.wreckage.game.domain.CorridorGenerationParameters;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Direction;
import se.segersten.wreckage.game.domain.MapDefinition;
import se.segersten.wreckage.game.domain.MatchSettings;
import se.segersten.wreckage.game.domain.PlayerCountRange;
import se.segersten.wreckage.game.domain.Position;
import se.segersten.wreckage.game.domain.SpawnPoint;

/** Converts the reusable corridor geometry into complete maps understood by the game engine. */
@Component
public class PlayableMapGenerator {
    static final int MAX_PLACEMENT_ATTEMPTS = 32;
    private static final int CHECKPOINT_COUNT = 4;
    private final OrthogonalCorridorGenerator corridors;
    private final CorridorGeometryValidator geometryValidator;
    private final MapValidator mapValidator;

    public PlayableMapGenerator() {
        this(new OrthogonalCorridorGenerator(), new CorridorGeometryValidator(), new MapValidator());
    }

    @Autowired
    public PlayableMapGenerator(MapValidator mapValidator) {
        this(new OrthogonalCorridorGenerator(), new CorridorGeometryValidator(), mapValidator);
    }

    PlayableMapGenerator(OrthogonalCorridorGenerator corridors, CorridorGeometryValidator geometryValidator,
                         MapValidator mapValidator) {
        this.corridors = Objects.requireNonNull(corridors);
        this.geometryValidator = Objects.requireNonNull(geometryValidator);
        this.mapValidator = Objects.requireNonNull(mapValidator);
    }

    public MapDefinition generate(long seed, int playerCount, CorridorGenerationParameters parameters) {
        MatchSettings settings = MatchSettings.forPlayerCount(playerCount);
        return generate(settings.boardWidth(), settings.boardHeight(), playerCount, seed, parameters);
    }

    public MapDefinition generate(int width, int height, int playerCount, long seed,
                                  CorridorGenerationParameters parameters) {
        Objects.requireNonNull(parameters, "Generation parameters are required");
        MatchSettings settings = MatchSettings.forPlayerCount(playerCount);
        if (width != settings.boardWidth() || height != settings.boardHeight()) {
            throw new IllegalArgumentException("Generated map dimensions " + width + "x" + height
                    + " do not match the " + playerCount + "-player configuration "
                    + settings.boardWidth() + "x" + settings.boardHeight());
        }
        return createMap(corridors.generate(width, height, seed, parameters), playerCount, seed, parameters);
    }

    MapDefinition createMap(CorridorNetwork network, int playerCount, long seed,
                            CorridorGenerationParameters parameters) {
        Objects.requireNonNull(network, "Corridor network is required");
        Objects.requireNonNull(parameters, "Generation parameters are required");
        MatchSettings settings = MatchSettings.forPlayerCount(playerCount);
        if (network.width() != settings.boardWidth() || network.height() != settings.boardHeight()) {
            throw new IllegalArgumentException("Corridor dimensions " + network.width() + "x" + network.height()
                    + " do not match the " + playerCount + "-player configuration "
                    + settings.boardWidth() + "x" + settings.boardHeight());
        }
        geometryValidator.validate(network);

        RuntimeException lastFailure = null;
        int attempts = Math.min(MAX_PLACEMENT_ATTEMPTS, network.traversable().size());
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                List<Position> checkpointPositions = placeCheckpoints(network, seed, attempt);
                List<SpawnPoint> starts = placeStarts(network, checkpointPositions, playerCount, seed, attempt);
                List<Checkpoint> checkpoints = java.util.stream.IntStream.range(0, CHECKPOINT_COUNT)
                        .mapToObj(index -> new Checkpoint("CP" + (index + 1), index + 1,
                                checkpointPositions.get(index))).toList();
                String suffix = Long.toUnsignedString(mix(seed ^ parameters.hashCode()), 36);
                MapDefinition map = new MapDefinition("generated-" + playerCount + "-" + suffix,
                        "Generated Wreckage " + suffix, network.width(), network.height(),
                        new PlayerCountRange(playerCount, playerCount), starts, checkpoints,
                        network.blockedPositionsInRowMajorOrder(), List.of(), List.of(), List.of(), List.of(), List.of());
                mapValidator.validate(map);
                return map;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                lastFailure = failure;
            }
        }
        throw new IllegalStateException("Unable to place a playable generated map after " + attempts
                + " attempts: seed=" + seed + ", players=" + playerCount + ", dimensions="
                + network.width() + "x" + network.height() + ", parameters=" + parameters
                + (lastFailure == null ? "" : ", reason=" + lastFailure.getMessage()), lastFailure);
    }

    private List<Position> placeCheckpoints(CorridorNetwork network, long seed, int attempt) {
        List<Position> all = stablePositions(network, seed, 11L);
        List<Position> preferred = all.stream().filter(position -> degree(network, position) >= 2).toList();
        List<Position> candidates = preferred.size() >= CHECKPOINT_COUNT ? preferred : all;
        if (candidates.size() < CHECKPOINT_COUNT)
            throw new IllegalArgumentException("fewer than four traversable checkpoint tiles");

        Map<Position, Map<Position, Integer>> distances = distances(network, candidates);
        List<Position> starts = candidates.stream().sorted(Comparator
                .comparingInt((Position position) -> eccentricity(distances.get(position)))
                .thenComparing(Comparator.comparingInt((Position position) -> degree(network, position)).reversed())
                .thenComparingLong(position -> rank(seed, position, 13L))).toList();
        Position first = starts.get(attempt % starts.size());
        List<Position> selected = new ArrayList<>();
        selected.add(first);
        while (selected.size() < CHECKPOINT_COUNT) {
            Position next = candidates.stream().filter(position -> !selected.contains(position))
                    .max(Comparator.comparingInt((Position position) -> checkpointScore(
                                    position, selected, distances, network))
                            .thenComparingLong(position -> rank(seed, position, 17L + attempt)))
                    .orElseThrow(() -> new IllegalArgumentException("not enough checkpoint candidates"));
            selected.add(next);
        }
        return List.copyOf(selected);
    }

    private int checkpointScore(Position position, List<Position> selected,
                                Map<Position, Map<Position, Integer>> distances, CorridorNetwork network) {
        int minimum = selected.stream().mapToInt(other -> distance(distances, position, other)).min().orElse(0);
        int total = selected.stream().mapToInt(other -> distance(distances, position, other)).sum();
        return minimum * 1_000 + total * 10 + Math.min(degree(network, position), 3);
    }

    private List<SpawnPoint> placeStarts(CorridorNetwork network, List<Position> checkpoints, int playerCount,
                                         long seed, int attempt) {
        Position cp1 = checkpoints.getFirst();
        Map<Position, Integer> fromCp1 = shortestPaths(network, cp1);
        Set<Position> excluded = Set.copyOf(checkpoints);
        List<Position> candidates = stablePositions(network, seed, 31L + attempt).stream()
                .filter(position -> !excluded.contains(position))
                .filter(position -> degree(network, position) >= 2)
                .sorted(Comparator.comparingInt((Position position) -> fromCp1.get(position))
                        .thenComparingLong(position -> rank(seed, position, 37L + attempt))).toList();
        if (candidates.size() < playerCount)
            throw new IllegalArgumentException("not enough non-dead-end starting tiles for " + playerCount + " players");

        List<Position> pool = balancedPool(candidates, fromCp1, playerCount);
        List<Position> selected = new ArrayList<>();
        while (selected.size() < playerCount) {
            Position next = pool.stream().filter(position -> !selected.contains(position))
                    .max(Comparator.comparingInt((Position position) -> startScore(network, position, selected))
                            .thenComparingLong(position -> rank(seed, position, 41L + attempt)))
                    .orElseThrow(() -> new IllegalArgumentException("unable to select unique starting tiles"));
            selected.add(next);
        }
        selected.sort(Comparator.comparingLong(position -> rank(seed, position, 43L)));
        return selected.stream().map(position -> new SpawnPoint(position,
                orientationToward(network, position, fromCp1, seed))).toList();
    }

    private List<Position> balancedPool(List<Position> candidates, Map<Position, Integer> distances, int count) {
        int bestStart = -1;
        int bestSpan = Integer.MAX_VALUE;
        int bestDistance = -1;
        for (int start = 0; start + count <= candidates.size(); start++) {
            int end = start + count - 1;
            int span = distances.get(candidates.get(end)) - distances.get(candidates.get(start));
            int distance = distances.get(candidates.get(start));
            if (span < bestSpan || span == bestSpan && distance > bestDistance) {
                bestStart = start;
                bestSpan = span;
                bestDistance = distance;
            }
        }
        int maximumDistance = distances.get(candidates.get(bestStart)) + bestSpan;
        int minimumDistance = distances.get(candidates.get(bestStart));
        return candidates.stream().filter(position -> distances.get(position) >= minimumDistance
                && distances.get(position) <= maximumDistance).toList();
    }

    private int startScore(CorridorNetwork network, Position position, List<Position> selected) {
        if (selected.isEmpty()) return degree(network, position);
        int nearest = selected.stream().mapToInt(other -> shortestPaths(network, position).get(other)).min().orElse(0);
        int adjacentPenalty = selected.stream().anyMatch(other -> network.orthogonalTraversableNeighbors(position)
                .contains(other)) ? 10_000 : 0;
        return nearest * 100 + degree(network, position) - adjacentPenalty;
    }

    private Direction orientationToward(CorridorNetwork network, Position position,
                                        Map<Position, Integer> fromCp1, long seed) {
        return java.util.Arrays.stream(Direction.values())
                .filter(direction -> network.isTraversable(position.move(direction)))
                .min(Comparator.comparingInt((Direction direction) -> fromCp1.get(position.move(direction)))
                        .thenComparingLong(direction -> rank(seed, position.move(direction), 47L)))
                .orElseThrow(() -> new IllegalArgumentException("start has no traversable exit at " + position));
    }

    private Map<Position, Map<Position, Integer>> distances(CorridorNetwork network, List<Position> positions) {
        Map<Position, Map<Position, Integer>> result = new HashMap<>();
        for (Position position : positions) result.put(position, shortestPaths(network, position));
        return result;
    }

    private Map<Position, Integer> shortestPaths(CorridorNetwork network, Position origin) {
        Map<Position, Integer> result = new HashMap<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        result.put(origin, 0);
        queue.add(origin);
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            int nextDistance = result.get(current) + 1;
            for (Position next : network.orthogonalTraversableNeighbors(current)) {
                if (result.putIfAbsent(next, nextDistance) == null) queue.add(next);
            }
        }
        return result;
    }

    private int distance(Map<Position, Map<Position, Integer>> distances, Position from, Position to) {
        Integer result = distances.get(from).get(to);
        if (result == null) throw new IllegalArgumentException("disconnected placement candidates");
        return result;
    }

    private int eccentricity(Map<Position, Integer> distances) {
        return distances.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private int degree(CorridorNetwork network, Position position) {
        return network.orthogonalTraversableNeighbors(position).size();
    }

    private List<Position> stablePositions(CorridorNetwork network, long seed, long salt) {
        return network.positionsInRowMajorOrder().stream()
                .sorted(Comparator.comparingLong(position -> rank(seed, position, salt)))
                .toList();
    }

    private long rank(long seed, Position position, long salt) {
        return mix(seed ^ salt ^ ((long) position.x() << 32) ^ Integer.toUnsignedLong(position.y()));
    }

    private long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
