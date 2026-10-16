package se.segersten.wreckage.game.domain;

public record CorridorGenerationParameters(double targetDensity, int preferredStraightLength,
                                           double additionalConnectionProbability) {
    public static final double DEFAULT_TARGET_DENSITY = 0.42;
    public static final int DEFAULT_PREFERRED_STRAIGHT_LENGTH = 6;
    public static final double DEFAULT_ADDITIONAL_CONNECTION_PROBABILITY = 0.20;

    public CorridorGenerationParameters {
        if (!Double.isFinite(targetDensity) || targetDensity <= 0.0 || targetDensity > 1.0)
            throw new IllegalArgumentException("Target density must be finite and in (0, 1]");
        if (preferredStraightLength <= 0)
            throw new IllegalArgumentException("Preferred straight corridor length must be positive");
        if (!Double.isFinite(additionalConnectionProbability)
                || additionalConnectionProbability < 0.0 || additionalConnectionProbability > 1.0)
            throw new IllegalArgumentException("Additional connection probability must be finite and in [0, 1]");
    }

    public static CorridorGenerationParameters defaults() {
        return new CorridorGenerationParameters(DEFAULT_TARGET_DENSITY, DEFAULT_PREFERRED_STRAIGHT_LENGTH,
                DEFAULT_ADDITIONAL_CONNECTION_PROBABILITY);
    }
}
