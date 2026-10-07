package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.stream.IntStream;

public record Board(int width, int height, Set<Wall> walls, Set<Position> pits,
                    Set<Checkpoint> checkpoints,List<SpawnPoint> spawnPoints,List<Conveyor> conveyors,
                    List<Rotator> rotators, Set<Position> controlPoints) {

    public Board(int width, int height) {
        this(width,height,Set.of(),Set.of(),Set.of(),defaultSpawnPoints(width,height),List.of(),List.of(),Set.of());
    }

    public Board(int width, int height, Set<Wall> walls) {
        this(width,height,walls,Set.of(),Set.of(),defaultSpawnPoints(width,height),List.of(),List.of(),Set.of());
    }

    public Board(int width, int height, Set<Wall> walls, Set<Position> pits) {
        this(width,height,walls,pits,Set.of(),defaultSpawnPoints(width,height),List.of(),List.of(),Set.of());
    }

    public Board(int width, int height, Set<Wall> walls, Set<Position> pits, Set<Checkpoint> checkpoints) {
        this(width,height,walls,pits,checkpoints,defaultSpawnPoints(width,height),List.of(),List.of(),Set.of());
    }
    public Board(int w,int h,Set<Wall>walls,Set<Position>pits,Set<Checkpoint>checkpoints,List<SpawnPoint>spawns){this(w,h,walls,pits,checkpoints,spawns,List.of(),List.of(),Set.of());}
    public Board(int w,int h,Set<Wall>walls,Set<Position>pits,Set<Checkpoint>checkpoints,List<SpawnPoint>spawns,List<Conveyor>conveyors,List<Rotator>rotators){this(w,h,walls,pits,checkpoints,spawns,conveyors,rotators,Set.of());}

    public Board {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
        walls = Set.copyOf(Objects.requireNonNull(walls));
        pits = Set.copyOf(Objects.requireNonNull(pits));
        checkpoints = Set.copyOf(Objects.requireNonNull(checkpoints));
        spawnPoints = List.copyOf(Objects.requireNonNull(spawnPoints));
        conveyors=List.copyOf(Objects.requireNonNull(conveyors));rotators=List.copyOf(Objects.requireNonNull(rotators));
        controlPoints = Set.copyOf(Objects.requireNonNull(controlPoints));
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
        if (checkpoints.stream().map(Checkpoint::order).distinct().count() != checkpoints.size())
            throw new IllegalArgumentException("Checkpoint orders must be unique");
        if (spawnPoints.isEmpty()) throw new IllegalArgumentException("A board needs spawn points");
        if (spawnPoints.stream().anyMatch(spawn -> !isWithinBounds(spawn.position(), width, height)))
            throw new IllegalArgumentException("Spawn points must be inside the board");
        if (spawnPoints.stream().map(SpawnPoint::position).distinct().count() != spawnPoints.size())
            throw new IllegalArgumentException("Spawn positions must be unique");
        if(conveyors.stream().anyMatch(c->!isWithinBounds(c.position(),width,height)))throw new IllegalArgumentException("Conveyors must be inside the board");
        if(conveyors.stream().map(Conveyor::position).distinct().count()!=conveyors.size())throw new IllegalArgumentException("Conveyor positions must be unique");
        if(rotators.stream().anyMatch(r->!isWithinBounds(r.position(),width,height)))throw new IllegalArgumentException("Rotators must be inside the board");
        if(rotators.stream().map(Rotator::position).distinct().count()!=rotators.size())throw new IllegalArgumentException("Rotator positions must be unique");
        if (controlPoints.stream().anyMatch(position -> !isWithinBounds(position, width, height)))
            throw new IllegalArgumentException("Control points must be inside the board");
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
    public List<Checkpoint> orderedCheckpoints() {
        return checkpoints.stream().sorted(java.util.Comparator.comparingInt(Checkpoint::order)).toList();
    }
    public Checkpoint checkpoint(int order) {
        return checkpoints.stream().filter(checkpoint -> checkpoint.order() == order).findFirst().orElse(null);
    }
    public Rotator rotatorAt(Position p){return rotators.stream().filter(r->r.position().equals(p)).findFirst().orElse(null);}
    public boolean isControlPoint(Position position) { return controlPoints.contains(position); }

    public Board withDimensions(int selectedWidth, int selectedHeight) {
        java.util.function.Predicate<Position> inside = position ->
                isWithinBounds(position, selectedWidth, selectedHeight);
        List<SpawnPoint> selectedSpawns = spawnPoints.stream()
                .filter(spawn -> inside.test(spawn.position())).toList();
        if (selectedSpawns.isEmpty()) selectedSpawns = defaultSpawnPoints(selectedWidth, selectedHeight);
        return new Board(selectedWidth, selectedHeight,
                walls.stream().filter(wall -> inside.test(wall.cell())).collect(java.util.stream.Collectors.toSet()),
                pits.stream().filter(inside).collect(java.util.stream.Collectors.toSet()),
                checkpoints.stream().filter(checkpoint -> inside.test(checkpoint.position()))
                        .collect(java.util.stream.Collectors.toSet()),
                selectedSpawns,
                conveyors.stream().filter(conveyor -> inside.test(conveyor.position())).toList(),
                rotators.stream().filter(rotator -> inside.test(rotator.position())).toList(),
                controlPoints.stream().filter(inside).collect(java.util.stream.Collectors.toSet()));
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
