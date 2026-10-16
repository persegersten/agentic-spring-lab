package se.segersten.wreckage.game.application;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import se.segersten.wreckage.game.domain.CorridorGenerationParameters;
import se.segersten.wreckage.game.domain.CorridorNetwork;
import se.segersten.wreckage.game.domain.Position;

/**
 * Builds connected networks from long orthogonal segments. This is deliberately not a
 * cell-by-cell maze algorithm: every growth operation extends a straight lane from the existing network.
 */
public class OrthogonalCorridorGenerator {
    static final int MAX_GENERATION_ATTEMPTS = 8;
    private static final int CANDIDATE_ATTEMPTS_PER_TILE = 24;
    private final CorridorGeometryValidator validator;

    public OrthogonalCorridorGenerator() {
        this(new CorridorGeometryValidator());
    }

    OrthogonalCorridorGenerator(CorridorGeometryValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    public CorridorNetwork generate(int width, int height, long seed,
                                    CorridorGenerationParameters parameters) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Corridor dimensions must be positive");
        Objects.requireNonNull(parameters, "Generation parameters are required");

        CorridorNetwork best = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            Random random = new Random(mixSeed(seed, attempt));
            CorridorNetwork candidate = generateCandidate(width, height, random, parameters);
            validator.validate(candidate);
            if (best == null || candidate.density() > best.density()) best = candidate;
            if (candidate.density() >= parameters.targetDensity()) return candidate;
        }
        if (best != null) return best; // Density is a soft target; hard geometry constraints have already passed.
        throw new IllegalStateException("Unable to generate corridor network after " + MAX_GENERATION_ATTEMPTS
                + " attempts: width=" + width + ", height=" + height + ", seed=" + seed
                + ", parameters=" + parameters);
    }

    private CorridorNetwork generateCandidate(int width, int height, Random random,
                                              CorridorGenerationParameters parameters) {
        boolean[][] open = new boolean[height][width];
        Orientation nextOrientation = random.nextBoolean() ? Orientation.HORIZONTAL : Orientation.VERTICAL;
        carveSpine(open, width, height, nextOrientation, random);
        nextOrientation = nextOrientation.other();

        int targetTiles = Math.max(1, (int) Math.ceil(parameters.targetDensity() * width * height));
        int maxCandidates = Math.max(16, width * height * CANDIDATE_ATTEMPTS_PER_TILE);
        for (int candidate = 0; candidate < maxCandidates && count(open) < targetTiles; candidate++) {
            carveGrowthSegment(open, width, height, nextOrientation, random, parameters, targetTiles);
            nextOrientation = nextOrientation.other();
        }

        int loopCandidates = Math.max(4, width * height / 3);
        int densityCeiling = Math.min(width * height,
                Math.max(targetTiles, (int) Math.ceil((parameters.targetDensity() + 0.05) * width * height)));
        for (int candidate = 0; candidate < loopCandidates && count(open) < densityCeiling; candidate++) {
            if (random.nextDouble() <= parameters.additionalConnectionProbability()) {
                carveGrowthSegment(open, width, height,
                        random.nextBoolean() ? Orientation.HORIZONTAL : Orientation.VERTICAL, random, parameters,
                        densityCeiling);
            }
        }
        return network(open, width, height);
    }

    private void carveSpine(boolean[][] open, int width, int height, Orientation orientation, Random random) {
        if (orientation == Orientation.HORIZONTAL) {
            int y = random.nextInt(height);
            for (int x = 0; x < width; x++) open[y][x] = true;
        } else {
            int x = random.nextInt(width);
            for (int y = 0; y < height; y++) open[y][x] = true;
        }
    }

    private void carveGrowthSegment(boolean[][] open, int width, int height, Orientation orientation, Random random,
                                    CorridorGenerationParameters parameters, int maximumOpenTiles) {
        List<Position> anchors = openPositions(open, width, height);
        Position anchor = anchors.get(random.nextInt(anchors.size()));
        int variation = Math.max(1, parameters.preferredStraightLength() / 3);
        int desiredLength = Math.max(2, parameters.preferredStraightLength()
                + random.nextInt(variation * 2 + 1) - variation);
        boolean extendAsConnection = random.nextDouble() < parameters.additionalConnectionProbability();
        int negative = extendAsConnection ? Integer.MAX_VALUE : random.nextInt(desiredLength);
        int positive = extendAsConnection ? Integer.MAX_VALUE : desiredLength - 1 - negative;

        carveDirection(open, width, height, anchor, orientation, -1, negative, maximumOpenTiles);
        carveDirection(open, width, height, anchor, orientation, 1, positive, maximumOpenTiles);
    }

    private void carveDirection(boolean[][] open, int width, int height, Position anchor, Orientation orientation,
                                int sign, int requestedDistance, int maximumOpenTiles) {
        int limit = requestedDistance == Integer.MAX_VALUE ? Math.max(width, height) : requestedDistance;
        for (int distance = 1; distance <= limit; distance++) {
            if (count(open) >= maximumOpenTiles) return;
            int x = anchor.x() + (orientation == Orientation.HORIZONTAL ? sign * distance : 0);
            int y = anchor.y() + (orientation == Orientation.VERTICAL ? sign * distance : 0);
            if (x < 0 || x >= width || y < 0 || y >= height) return;
            if (open[y][x]) continue;
            open[y][x] = true;
            if (createsOpenThreeByThree(open, width, height, x, y)) {
                open[y][x] = false;
                return;
            }
        }
    }

    private boolean createsOpenThreeByThree(boolean[][] open, int width, int height, int changedX, int changedY) {
        for (int originY = Math.max(0, changedY - 2); originY <= Math.min(changedY, height - 3); originY++) {
            for (int originX = Math.max(0, changedX - 2); originX <= Math.min(changedX, width - 3); originX++) {
                boolean allOpen = true;
                for (int y = originY; y < originY + 3 && allOpen; y++)
                    for (int x = originX; x < originX + 3; x++)
                        if (!open[y][x]) { allOpen = false; break; }
                if (allOpen) return true;
            }
        }
        return false;
    }

    private CorridorNetwork network(boolean[][] open, int width, int height) {
        Set<Position> positions = new HashSet<>();
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (open[y][x]) positions.add(new Position(x, y));
        return new CorridorNetwork(width, height, positions);
    }

    private List<Position> openPositions(boolean[][] open, int width, int height) {
        java.util.ArrayList<Position> positions = new java.util.ArrayList<>();
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (open[y][x]) positions.add(new Position(x, y));
        return positions;
    }

    private int count(boolean[][] open) {
        int result = 0;
        for (boolean[] row : open) for (boolean cell : row) if (cell) result++;
        return result;
    }

    private long mixSeed(long seed, int attempt) {
        return seed + 0x9E3779B97F4A7C15L * attempt;
    }

    private enum Orientation {
        HORIZONTAL, VERTICAL;
        Orientation other() { return this == HORIZONTAL ? VERTICAL : HORIZONTAL; }
    }
}
