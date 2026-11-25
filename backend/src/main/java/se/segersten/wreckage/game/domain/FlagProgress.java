package se.segersten.wreckage.game.domain;

import java.util.Collection;

public final class FlagProgress {
    private FlagProgress() {}

    public static boolean allCaptured(Board board, Collection<Player> players) {
        return !board.checkpoints().isEmpty() && nextAvailable(board, players) == null;
    }

    public static Checkpoint nextAvailable(Board board, Collection<Player> players) {
        return board.orderedCheckpoints().stream()
                .filter(flag -> players.stream().noneMatch(player -> player.getVisitedCheckpoints().contains(flag.id())))
                .findFirst().orElse(null);
    }
}
