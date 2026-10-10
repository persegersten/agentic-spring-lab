package se.segersten.wreckage.game.domain;

public record GameConfiguration(int maxPlayers, int joinTimeoutSeconds, int programSize,
                                int planningTimeoutSeconds, int roundLimit, String mapId) {
    public static final int DEFAULT_MAX_PLAYERS = 9;
    public static final int DEFAULT_JOIN_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_PROGRAM_SIZE = 5;
    public static final int DEFAULT_PLANNING_TIMEOUT_SECONDS = 120;
    public static final int DEFAULT_ROUND_LIMIT = 6;

    public GameConfiguration(int maxPlayers, int joinTimeoutSeconds, int programSize, int planningTimeoutSeconds) {
        this(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds, DEFAULT_ROUND_LIMIT, null);
    }

    public GameConfiguration {
        if (maxPlayers < 2 || maxPlayers > 10) throw new IllegalArgumentException("maxPlayers must be between 2 and 10");
        if (joinTimeoutSeconds < 1) throw new IllegalArgumentException("joinTimeoutSeconds must be positive");
        if (programSize != 5) throw new IllegalArgumentException("programSize must be 5");
        if (planningTimeoutSeconds < 1) throw new IllegalArgumentException("planningTimeoutSeconds must be positive");
        if (roundLimit < 1) throw new IllegalArgumentException("roundLimit must be positive");
    }

    public static GameConfiguration defaults() {
        return new GameConfiguration(DEFAULT_MAX_PLAYERS, DEFAULT_JOIN_TIMEOUT_SECONDS,
                DEFAULT_PROGRAM_SIZE, DEFAULT_PLANNING_TIMEOUT_SECONDS, DEFAULT_ROUND_LIMIT, null);
    }

    public GameConfiguration withRoundLimit(int selectedRoundLimit) {
        return new GameConfiguration(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds,
                selectedRoundLimit, mapId);
    }
}
