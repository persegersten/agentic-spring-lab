package se.segersten.wreckage.game.domain;

public record GameConfiguration(
        int maxPlayers,
        int joinTimeoutSeconds,
        int cardsPerRound,
        int planningTimeoutSeconds) {

    public static final int DEFAULT_MAX_PLAYERS = 12;
    public static final int DEFAULT_JOIN_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_CARDS_PER_ROUND = 3;
    public static final int DEFAULT_PLANNING_TIMEOUT_SECONDS = 120;

    public GameConfiguration {
        if (maxPlayers < 1) throw new IllegalArgumentException("maxPlayers must be positive");
        if (joinTimeoutSeconds < 1) throw new IllegalArgumentException("joinTimeoutSeconds must be positive");
        if (cardsPerRound < 3 || cardsPerRound > 10)
            throw new IllegalArgumentException("cardsPerRound must be between 3 and 10");
        if (planningTimeoutSeconds < 1)
            throw new IllegalArgumentException("planningTimeoutSeconds must be positive");
    }

    public static GameConfiguration defaults() {
        return new GameConfiguration(DEFAULT_MAX_PLAYERS, DEFAULT_JOIN_TIMEOUT_SECONDS,
                DEFAULT_CARDS_PER_ROUND, DEFAULT_PLANNING_TIMEOUT_SECONDS);
    }
}
