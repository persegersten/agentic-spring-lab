package se.segersten.wreckage.game.domain;

import java.util.UUID;

public record GamePlacement(UUID playerId, int placement, int checkpointsVisited,
                            Integer distanceToNextCheckpoint, int crashes, boolean winner) {}
