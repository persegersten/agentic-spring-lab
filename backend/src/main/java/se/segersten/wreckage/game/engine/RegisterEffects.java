package se.segersten.wreckage.game.engine;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Mutable resolution context whose lifetime is exactly one program register. */
public final class RegisterEffects {
    private final Set<UUID> anchored = new HashSet<>();
    private final Set<UUID> shields = new HashSet<>();

    public void anchor(UUID vehicleId) { anchored.add(vehicleId); }
    public boolean isAnchored(UUID vehicleId) { return anchored.contains(vehicleId); }
    public void shield(UUID vehicleId) { shields.add(vehicleId); }
    public boolean consumeShield(UUID vehicleId) { return shields.remove(vehicleId); }
}
