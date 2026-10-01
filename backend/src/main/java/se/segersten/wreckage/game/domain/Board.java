package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.stream.IntStream;

public record Board(int width, int height, Set<Wall> walls, Set<Position> pits,
                    Set<Checkpoint> checkpoints, List<SpawnPoint> spawnPoints) {

    public Board(int width, int height) {
        this(width, height, Set.of(), Set.of(), Set.of(), defaultSpawnPoints(width, height));
    }

    public Board(int width, int height, Set<Wall> walls) {
        this(width, height, walls, Set.of(), Set.of(), defaultSpawnPoints(width, height));
    }

    public Board(int width, int height, Set<Wall> walls, Set<Position> pits) {
        this(width, height, walls, pits, Set.of(), defaultSpawnPoints(width, height));
    }

    public Board(int width, int height, Set<Wall> walls, Set<Position> pits, Set<Checkpoint> checkpoints) {
        this(width, height, walls, pits, checkpoints, defaultSpawnPoints(width, height));
    }

    public Board {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
        walls = Set.copyOf(Objects.requireNonNull(walls));
        pits = Set.copyOf(Objects.requireNonNull(pits));
        checkpoints = Set.copyOf(Objects.requireNonNull(checkpoints));
        spawnPoints = List.copyOf(Objects.requireNonNull(spawnPoints));
        if (walls.stream().anyMatch(wall -> !isWithinBounds(wall.cell(), width, height)))
            throw new IllegalArgumentException("Wall cells must be inside the board");
        if (pits.stream().anyMatch(position -> !isWithinBounds(position, width, height)))
            throw new IllegalArgumentException("Pits must be inside the board");
        if (checkpoints.stream().anyMatch(checkpoint -> !isWithinBounds(checkpoint.position(), width, height)))
            throw new IllegalArgumentException("Checkpoints must be inside the board");
        if (checkpoints.stream().map(Checkpoint::id).distinct().count() != checkpoints.size())
            throw new IllegalArgumentException("Checkpoint ids must be unique");
        if (checkpoints.stream().map(Checkpoint::position).distinct().count() != checkpoints.size())
            throw new IllegalArgumentException("Checkpoint positions must be unique");
        if (spawnPoints.isEmpty()) throw new IllegalArgumentException("A board needs spawn points");
        if (spawnPoints.stream().anyMatch(spawn -> !isWithinBounds(spawn.position(), width, height)))
            throw new IllegalArgumentException("Spawn points must be inside the board");
        if (spawnPoints.stream().map(SpawnPoint::position).distinct().count() != spawnPoints.size())
            throw new IllegalArgumentException("Spawn positions must be unique");
    }

    public boolean isValidPosition(Position position) {
        return isWithinBounds(position);
    }

    public boolean hasWall(Position cell, Direction direction) {
        Objects.requireNonNull(cell);
        Objects.requireNonNull(direction);
        return walls.contains(new Wall(cell, direction))
                || walls.contains(new Wall(cell.move(direction), direction.reverse()));
    }

    public boolean isPit(Position position) { return pits.contains(position); }

    public Checkpoint checkpointAt(Position position) {
        return checkpoints.stream().filter(checkpoint -> checkpoint.position().equals(position)).findFirst().orElse(null);
    }

    private boolean isWithinBounds(Position position) {
        return isWithinBounds(position, width, height);
    }

    private static boolean isWithinBounds(Position position, int width, int height) {
        Objects.requireNonNull(position);
        return position.x() >= 0 && position.x() < width && position.y() >= 0 && position.y() < height;
    }

    private static List<SpawnPoint> defaultSpawnPoints(int width, int height) {
        if (width <= 0 || height <= 0) return List.of();
        return IntStream.range(0, width * height)
                .mapToObj(index -> new SpawnPoint(new Position(index % width, index / width), Direction.SOUTH))
                .toList();
    }
}
