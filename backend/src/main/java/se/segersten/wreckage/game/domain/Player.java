package se.segersten.wreckage.game.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

public class Player {

    private final UUID id;
    private final String name;
    private final String accessTokenHash;
    private final boolean automated;
    private int score;
    private final Set<String> visitedCheckpoints;
    private int crashes;

    Player(UUID id, String name, String accessTokenHash) {
        this(id, name, accessTokenHash, false, 0, Set.of(), 0);
    }

    Player(UUID id, String name, String accessTokenHash, boolean automated, int score, Set<String> visitedCheckpoints, int crashes) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.name = name.trim();
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "accessTokenHash must not be null");
        this.automated = automated;
        this.score = score;
        this.visitedCheckpoints = new LinkedHashSet<>(Objects.requireNonNull(visitedCheckpoints));
        if (crashes < 0) throw new IllegalArgumentException("crashes must not be negative");
        this.crashes = crashes;
    }

    public static Player create(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player createAutomated(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash, true, 0, Set.of(), 0);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, int score, Set<String> visitedCheckpoints) {
        return new Player(id, name, accessTokenHash, false, score, visitedCheckpoints, 0);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, int score,
                                   Set<String> visitedCheckpoints, int crashes) {
        return new Player(id, name, accessTokenHash, false, score, visitedCheckpoints, crashes);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, boolean automated,
                                   int score, Set<String> visitedCheckpoints, int crashes) {
        return new Player(id, name, accessTokenHash, automated, score, visitedCheckpoints, crashes);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAccessTokenHash() { return accessTokenHash; }
    public boolean isAutomated() { return automated; }
    public int getScore() { return score; }
    public Set<String> getVisitedCheckpoints() { return Set.copyOf(visitedCheckpoints); }
    public boolean visitCheckpoint(String checkpointId) { return visitedCheckpoints.add(checkpointId); }
    public int getCapturedCheckpointCount(Board board) {
        int captured = 0;
        for (Checkpoint checkpoint : board.orderedCheckpoints()) {
            if (!visitedCheckpoints.contains(checkpoint.id())) break;
            captured++;
        }
        return captured;
    }
    public Checkpoint getNextCheckpoint(Board board) {
        int captured = getCapturedCheckpointCount(board);
        List<Checkpoint> checkpoints = board.orderedCheckpoints();
        return captured < checkpoints.size() ? checkpoints.get(captured) : null;
    }
    public boolean captureCheckpoint(Checkpoint checkpoint, Board board) {
        Checkpoint next = getNextCheckpoint(board);
        return next != null && next.id().equals(checkpoint.id()) && visitedCheckpoints.add(checkpoint.id());
    }
    public boolean hasCompletedCheckpoints(Board board) {
        return !board.checkpoints().isEmpty() && getCapturedCheckpointCount(board) == board.checkpoints().size();
    }
    public int changeScore(int delta) { score += delta; return score; }
    public int getCrashes() { return crashes; }
    public void recordCrash() { crashes++; }
}
