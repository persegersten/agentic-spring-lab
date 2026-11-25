package se.segersten.wreckage.game.domain;

import java.util.UUID;

// The nullable distance field is retained for API compatibility; flags alone determine rank.
public record GamePlacement(UUID playerId, int placement, int score, int checkpointsVisited,
                            Integer distanceToNextCheckpoint, int crashes, boolean winner) {}
