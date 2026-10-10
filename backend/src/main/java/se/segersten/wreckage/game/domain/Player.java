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
    private final Set<String> visitedCheckpoints;
    private int crashes;
    private boolean shieldConsumed;

    Player(UUID id, String name, String accessTokenHash) {
        this(id, name, accessTokenHash, false, Set.of(), 0, false);
    }

    Player(UUID id, String name, String accessTokenHash, boolean automated, Set<String> visitedCheckpoints, int crashes, boolean shieldConsumed) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.name = name.trim();
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "accessTokenHash must not be null");
        this.automated = automated;
        this.visitedCheckpoints = new LinkedHashSet<>(Objects.requireNonNull(visitedCheckpoints));
        if (crashes < 0) throw new IllegalArgumentException("crashes must not be negative");
        this.crashes = crashes;
        this.shieldConsumed = shieldConsumed;
    }

    public static Player create(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player createAutomated(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash, true, Set.of(), 0, false);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash) {
        return new Player(id, name, accessTokenHash);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, int score, Set<String> visitedCheckpoints) {
        return new Player(id, name, accessTokenHash, false, visitedCheckpoints, 0, false);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, int score,
                                   Set<String> visitedCheckpoints, int crashes) {
        return new Player(id, name, accessTokenHash, false, visitedCheckpoints, crashes, false);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, boolean automated,
                                   int score, Set<String> visitedCheckpoints, int crashes) {
        return new Player(id, name, accessTokenHash, automated, visitedCheckpoints, crashes, false);
    }

    public static Player rehydrate(UUID id, String name, String accessTokenHash, boolean automated,
                                   Set<String> visitedCheckpoints, int crashes, boolean shieldConsumed) {
        return new Player(id, name, accessTokenHash, automated, visitedCheckpoints, crashes, shieldConsumed);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAccessTokenHash() { return accessTokenHash; }
    public boolean isAutomated() { return automated; }
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
    public int getCrashes() { return crashes; }
    public void recordCrash() { crashes++; }
    public boolean isShieldConsumed() { return shieldConsumed; }
    public void consumeShield() {
        if (shieldConsumed) throw new IllegalStateException("Shield has already been consumed");
        shieldConsumed = true;
    }
}
