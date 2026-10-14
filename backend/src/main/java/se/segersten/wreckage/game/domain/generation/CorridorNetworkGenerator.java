package se.segersten.wreckage.game.domain.generation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import se.segersten.wreckage.game.domain.Position;

/**
 * Constructs an orthogonal street-like network from long intersecting segments. This is deliberately not
 * a cell-by-cell maze algorithm: every carving decision adds a straight run attached to the existing network.
 */
public final class CorridorNetworkGenerator {
    private final CorridorNetworkValidator validator;

    public CorridorNetworkGenerator() {
        this(new CorridorNetworkValidator());
    }

    CorridorNetworkGenerator(CorridorNetworkValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    public CorridorNetwork generate(int width, int height, long seed) {
        return generate(width, height, seed, CorridorGenerationParameters.defaults());
    }

    public CorridorNetwork generate(int width, int height, long seed, CorridorGenerationParameters parameters) {
        Objects.requireNonNull(parameters, "Generation parameters are required");
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("Corridor generation requires dimensions of at least 3x3");
        }
        SplittableRandom seeds = new SplittableRandom(seed);
        CorridorNetwork best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int attempt = 1; attempt <= parameters.maxGenerationAttempts(); attempt++) {
            CorridorNetwork candidate = generateCandidate(width, height, seed, parameters, attempt, seeds.split());
            try {
                validator.validate(candidate);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            double distance = Math.abs(parameters.targetDensity() - candidate.density());
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
            if (candidate.density() >= parameters.targetDensity() * 0.98
                    && candidate.density() <= parameters.targetDensity() + 0.025) {
                return candidate;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Could not generate a valid corridor network for seed=" + seed
                    + ", width=" + width + ", height=" + height + ", parameters=" + parameters);
        }
        return new CorridorNetwork(best.width(), best.height(), best.seed(), best.parameters(),
                best.traversablePositions(), best.walls(), parameters.maxGenerationAttempts());
    }

    private CorridorNetwork generateCandidate(int width, int height, long seed,
                                               CorridorGenerationParameters parameters, int attempt,
                                               SplittableRandom random) {
        boolean[][] open = new boolean[height][width];
        int backboneY = random.nextInt(height);
        int backboneX = random.nextInt(width);
        carveUnchecked(open, horizontal(backboneY, 0, width - 1));
        carveUnchecked(open, vertical(backboneX, 0, height - 1));

        List<AxisLine> candidates = new ArrayList<>();
        for (int y = 0; y < height; y++) if (y != backboneY) candidates.add(new AxisLine(true, y));
        for (int x = 0; x < width; x++) if (x != backboneX) candidates.add(new AxisLine(false, x));
        shuffle(candidates, random);

        int index = 0;
        int decisionLimit = (width + height) * 4;
        for (int decisions = 0; decisions < decisionLimit && density(open) < parameters.targetDensity(); decisions++) {
            if (index == candidates.size()) {
                shuffle(candidates, random);
                index = 0;
            }
            AxisLine line = candidates.get(index++);
            List<Position> segment = attachedSegment(open, line, parameters, random);
            if (!segment.isEmpty()) tryCarve(open, segment);
        }

        // A final bounded loop pass extends existing lanes. It may miss the soft density target, but never
        // compromises connectivity or the no-room invariant.
        int loopAttempts = width + height;
        for (int i = 0; i < loopAttempts && density(open) < parameters.targetDensity(); i++) {
            if (random.nextDouble() > parameters.additionalConnectionProbability()) continue;
            AxisLine line = candidates.get(random.nextInt(candidates.size()));
            List<Position> segment = attachedSegment(open, line, parameters, random);
            if (!segment.isEmpty()) tryCarve(open, segment);
        }

        Set<Position> positions = new LinkedHashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) if (open[y][x]) positions.add(new Position(x, y));
        }
        return new CorridorNetwork(width, height, seed, parameters, positions, Set.of(), attempt);
    }

    private List<Position> attachedSegment(boolean[][] open, AxisLine line,
                                           CorridorGenerationParameters parameters, SplittableRandom random) {
        int varyingSize = line.horizontal ? open[0].length : open.length;
        List<Integer> anchors = new ArrayList<>();
        for (int value = 0; value < varyingSize; value++) {
            int x = line.horizontal ? value : line.coordinate;
            int y = line.horizontal ? line.coordinate : value;
            if (open[y][x]) anchors.add(value);
        }
        if (anchors.isEmpty()) return List.of();
        int anchor = anchors.get(random.nextInt(anchors.size()));
        int minimumLength = Math.min(varyingSize,
                Math.max(2, Math.min(parameters.preferredStraightLength(), varyingSize)));
        int length;
        if (random.nextDouble() < parameters.additionalConnectionProbability()) {
            length = varyingSize;
        } else {
            int lower = Math.max(minimumLength, (varyingSize + 1) / 2);
            length = lower + random.nextInt(varyingSize - lower + 1);
        }
        int minimumStart = Math.max(0, anchor - length + 1);
        int maximumStart = Math.min(anchor, varyingSize - length);
        int start = minimumStart + random.nextInt(maximumStart - minimumStart + 1);
        return line.horizontal
                ? horizontal(line.coordinate, start, start + length - 1)
                : vertical(line.coordinate, start, start + length - 1);
    }

    private boolean tryCarve(boolean[][] open, List<Position> segment) {
        List<Position> added = new ArrayList<>();
        boolean attached = false;
        for (Position position : segment) {
            if (open[position.y()][position.x()]) attached = true;
            else {
                open[position.y()][position.x()] = true;
                added.add(position);
            }
        }
        if (!attached || added.isEmpty() || hasOpenThreeByThree(open)) {
            for (Position position : added) open[position.y()][position.x()] = false;
            return false;
        }
        return true;
    }

    private boolean hasOpenThreeByThree(boolean[][] open) {
        for (int y = 0; y <= open.length - 3; y++) {
            for (int x = 0; x <= open[0].length - 3; x++) {
                boolean allOpen = true;
                for (int dy = 0; dy < 3 && allOpen; dy++) {
                    for (int dx = 0; dx < 3; dx++) {
                        if (!open[y + dy][x + dx]) {
                            allOpen = false;
                            break;
                        }
                    }
                }
                if (allOpen) return true;
            }
        }
        return false;
    }

    private void carveUnchecked(boolean[][] open, List<Position> segment) {
        for (Position position : segment) open[position.y()][position.x()] = true;
    }

    private List<Position> horizontal(int y, int fromX, int toX) {
        List<Position> result = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) result.add(new Position(x, y));
        return result;
    }

    private List<Position> vertical(int x, int fromY, int toY) {
        List<Position> result = new ArrayList<>();
        for (int y = fromY; y <= toY; y++) result.add(new Position(x, y));
        return result;
    }

    private double density(boolean[][] open) {
        int count = 0;
        for (boolean[] row : open) for (boolean cell : row) if (cell) count++;
        return count / (double) (open.length * open[0].length);
    }

    private <T> void shuffle(List<T> values, SplittableRandom random) {
        for (int i = values.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            T value = values.get(i);
            values.set(i, values.get(other));
            values.set(other, value);
        }
    }

    private record AxisLine(boolean horizontal, int coordinate) { }
}
