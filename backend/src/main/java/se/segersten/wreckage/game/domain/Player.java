package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;

public class Player {

    private final UUID id;
    private final String name;
    private final String accessTokenHash;
    private int score;
    private final Set<String> visitedCheckpoints;

    Player(UUID id, String name, String accessTokenHash) {
        this(id, name, accessTokenHash, 0, Set.of());
    }

    Player(UUID id, String name, String accessTokenHash, int score, Set<String> visitedCheckpoints) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.name = name.trim();
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "accessTokenHash must not be null");
        this.score = score;
        this.visitedCheckpoints = new LinkedHashSet<>(Objects.requireNonNull(visitedCheckpoints));
    }

    public static Player create(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, int score, Set<String> visitedCheckpoints) {
        return new Player(id, name, accessTokenHash, score, visitedCheckpoints);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAccessTokenHash() { return accessTokenHash; }
    public int getScore() { return score; }
    public Set<String> getVisitedCheckpoints() { return Set.copyOf(visitedCheckpoints); }
    public boolean visitCheckpoint(String checkpointId) { return visitedCheckpoints.add(checkpointId); }
    public int changeScore(int delta) { score += delta; return score; }
}
