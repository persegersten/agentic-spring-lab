package se.segersten.wreckage.game.domain;

public record PlayerCountRange(int min, int max) {
    public PlayerCountRange {
        if (min < 2 || max < min || max > 10)
            throw new IllegalArgumentException("Player range must be between 2 and 10");
    }

    public boolean supports(int count) { return count >= min && count <= max; }
}
