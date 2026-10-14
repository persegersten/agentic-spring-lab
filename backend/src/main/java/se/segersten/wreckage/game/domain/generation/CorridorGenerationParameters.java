package se.segersten.wreckage.game.domain.generation;

/** Configuration for deterministic corridor generation. */
public record CorridorGenerationParameters(double targetDensity, int preferredStraightLength,
                                           double additionalConnectionProbability,
                                           int maxGenerationAttempts) {
    public static final double DEFAULT_TARGET_DENSITY = 0.42;
    public static final int DEFAULT_PREFERRED_STRAIGHT_LENGTH = 7;
    public static final double DEFAULT_ADDITIONAL_CONNECTION_PROBABILITY = 0.35;
    public static final int DEFAULT_MAX_GENERATION_ATTEMPTS = 24;

    public CorridorGenerationParameters {
        if (!Double.isFinite(targetDensity) || targetDensity <= 0.0 || targetDensity >= 1.0) {
            throw new IllegalArgumentException("Target density must be finite and between 0 and 1");
        }
        if (preferredStraightLength < 2) {
            throw new IllegalArgumentException("Preferred straight corridor length must be at least 2");
        }
        if (!Double.isFinite(additionalConnectionProbability)
                || additionalConnectionProbability < 0.0 || additionalConnectionProbability > 1.0) {
            throw new IllegalArgumentException("Additional connection probability must be finite and between 0 and 1");
        }
        if (maxGenerationAttempts <= 0) {
            throw new IllegalArgumentException("Maximum generation attempts must be positive");
        }
    }

    public static CorridorGenerationParameters defaults() {
        return new CorridorGenerationParameters(DEFAULT_TARGET_DENSITY, DEFAULT_PREFERRED_STRAIGHT_LENGTH,
                DEFAULT_ADDITIONAL_CONNECTION_PROBABILITY, DEFAULT_MAX_GENERATION_ATTEMPTS);
    }
}
