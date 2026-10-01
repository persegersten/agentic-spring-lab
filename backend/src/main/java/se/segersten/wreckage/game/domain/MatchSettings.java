package se.segersten.wreckage.game.domain;

public record MatchSettings(int boardWidth, int boardHeight, int roundLimit) {

    public static MatchSettings forPlayerCount(int playerCount) {
        if (playerCount < 2 || playerCount > 10) {
            throw new IllegalArgumentException("playerCount must be between 2 and 10");
        }
        if (playerCount <= 3) return new MatchSettings(10, 10, 7);
        if (playerCount <= 6) return new MatchSettings(12, 12, 6);
        return new MatchSettings(16, 16, 5);
    }
}
