package se.segersten.wreckage.game.domain;

public record GameConfiguration(
        int maxPlayers,
        int joinTimeoutSeconds,
        int programSize,
        int planningTimeoutSeconds,
        int roundLimit,
        int checkpointScore,
        int controlPointScore,
        int crashPenalty,
        int pushCrashScore,
        String mapId,
        Long mapSeed) {

    public static final String RANDOM_CORRIDORS_MAP_ID = "random-corridors";

    public static final int DEFAULT_MAX_PLAYERS = 9;
    public static final int DEFAULT_JOIN_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_PROGRAM_SIZE = 5;
    public static final int DEFAULT_PLANNING_TIMEOUT_SECONDS = 120;
    public static final int DEFAULT_ROUND_LIMIT = 6;
    public static final int DEFAULT_CHECKPOINT_SCORE = 2;
    public static final int DEFAULT_CONTROL_POINT_SCORE = 1;
    public static final int DEFAULT_CRASH_PENALTY = -1;
    public static final int DEFAULT_PUSH_CRASH_SCORE = 1;

    public GameConfiguration(int maxPlayers, int joinTimeoutSeconds, int programSize, int planningTimeoutSeconds) {
        this(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds, DEFAULT_ROUND_LIMIT,
                DEFAULT_CHECKPOINT_SCORE, DEFAULT_CONTROL_POINT_SCORE, DEFAULT_CRASH_PENALTY, DEFAULT_PUSH_CRASH_SCORE,
                null, null);
    }

    public GameConfiguration(int maxPlayers, int joinTimeoutSeconds, int programSize, int planningTimeoutSeconds,
                             int roundLimit, int checkpointScore, int crashPenalty, int pushCrashScore) {
        this(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds, roundLimit, checkpointScore,
                DEFAULT_CONTROL_POINT_SCORE, crashPenalty, pushCrashScore, null);
    }

    public GameConfiguration(int maxPlayers, int joinTimeoutSeconds, int programSize, int planningTimeoutSeconds,
                             int roundLimit, int checkpointScore, int controlPointScore, int crashPenalty,
                             int pushCrashScore) {
        this(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds, roundLimit, checkpointScore,
                controlPointScore, crashPenalty, pushCrashScore, null);
    }

    public GameConfiguration(int maxPlayers, int joinTimeoutSeconds, int programSize, int planningTimeoutSeconds,
                             int roundLimit, int checkpointScore, int controlPointScore, int crashPenalty,
                             int pushCrashScore, String mapId) {
        this(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds, roundLimit, checkpointScore,
                controlPointScore, crashPenalty, pushCrashScore, mapId, null);
    }

    public GameConfiguration {
        if (maxPlayers < 2 || maxPlayers > 10)
            throw new IllegalArgumentException("maxPlayers must be between 2 and 10");
        if (joinTimeoutSeconds < 1) throw new IllegalArgumentException("joinTimeoutSeconds must be positive");
        if (programSize != DEFAULT_PROGRAM_SIZE)
            throw new IllegalArgumentException("programSize must be exactly " + DEFAULT_PROGRAM_SIZE);
        if (planningTimeoutSeconds < 1)
            throw new IllegalArgumentException("planningTimeoutSeconds must be positive");
        if (roundLimit < 1) throw new IllegalArgumentException("roundLimit must be positive");
    }

    public static GameConfiguration defaults() {
        return new GameConfiguration(DEFAULT_MAX_PLAYERS, DEFAULT_JOIN_TIMEOUT_SECONDS,
                DEFAULT_PROGRAM_SIZE, DEFAULT_PLANNING_TIMEOUT_SECONDS, DEFAULT_ROUND_LIMIT,
                DEFAULT_CHECKPOINT_SCORE, DEFAULT_CONTROL_POINT_SCORE, DEFAULT_CRASH_PENALTY,
                DEFAULT_PUSH_CRASH_SCORE, RANDOM_CORRIDORS_MAP_ID, null);
    }

    public GameConfiguration withRoundLimit(int selectedRoundLimit) {
        return new GameConfiguration(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds,
                selectedRoundLimit, checkpointScore, controlPointScore, crashPenalty, pushCrashScore, mapId, mapSeed);
    }

    public GameConfiguration withMapSeed(long selectedMapSeed) {
        return new GameConfiguration(maxPlayers, joinTimeoutSeconds, programSize, planningTimeoutSeconds,
                roundLimit, checkpointScore, controlPointScore, crashPenalty, pushCrashScore, mapId, selectedMapSeed);
    }
}
