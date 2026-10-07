package se.segersten.wreckage.game.domain;

import java.util.Objects;

public record Checkpoint(String id, int order, Position position) {
    public Checkpoint(String id, Position position) {
        this(id, parseOrder(id), position);
    }

    public Checkpoint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Checkpoint id must not be blank");
        id = id.trim();
        if (order < 1) throw new IllegalArgumentException("Checkpoint order must be positive");
        Objects.requireNonNull(position);
    }

    private static int parseOrder(String id) {
        if (id == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)$").matcher(id.trim());
        if (!matcher.find()) return 1;
        return Integer.parseInt(matcher.group(1));
    }
}
