package se.segersten.wreckage.game.domain;

import java.time.Instant;
import java.util.*;

/** Match-owned clock metadata, private queues and acceptance receipts. No wall clock access. */
public final class ContinuousExecution {
    public long tick;
    public long revision = 1;
    public long intervalMillis;
    public Instant nextTick;
    public Instant deadline;
    public final Map<UUID, CommandQueue> queues = new LinkedHashMap<>();
    private final Map<String, MovementOrder> receipts = new LinkedHashMap<>();
    private record Shield(UUID requestId, long startTick) {}
    private final Map<UUID, Shield> shields = new LinkedHashMap<>();
    public static final int SHIELD_TICKS = 5;
    public ContinuousExecution(Instant now, long interval, long duration) {
        if (interval < 1 || duration < 1) throw new IllegalArgumentException("Timing must be positive");
        intervalMillis = interval; nextTick = now.plusMillis(interval); deadline = now.plusMillis(duration);
    }
    public boolean duplicate(UUID player, UUID request, MovementOrder command) {
        if (request == null) throw new IllegalArgumentException("requestId is required");
        var shield = shields.get(player);
        if (shield != null && shield.requestId().equals(request)) throw new IllegalStateException("Request ID reused for a different action");
        var old = receipts.get(player + ":" + request);
        if (old != null && old != command) throw new IllegalStateException("Request ID reused with different command");
        return old != null;
    }
    public void append(UUID player, UUID request, MovementOrder command) {
        queues.get(player).append(command); receipts.put(player + ":" + request, command); revision++;
    }
    public boolean duplicateShield(UUID player, UUID request) {
        if (request == null) throw new IllegalArgumentException("requestId is required");
        if (receipts.containsKey(player + ":" + request)) throw new IllegalStateException("Request ID reused for a different action");
        var shield = shields.get(player);
        return shield != null && shield.requestId().equals(request);
    }
    public void scheduleShield(UUID player, UUID request) {
        if (duplicateShield(player, request)) return;
        if (shields.containsKey(player)) throw new IllegalStateException("Shield has already been consumed");
        shields.put(player, new Shield(request, tick + 1));
        revision++;
    }
    public boolean shieldPending(UUID player) {
        var shield = shields.get(player);
        return shield != null && tick < shield.startTick();
    }
    /** Future protected ticks, excluding the already resolved tick. */
    public int shieldTicksRemaining(UUID player) {
        var shield = shields.get(player);
        return shield == null ? 0 : (int)Math.max(0, shield.startTick() + SHIELD_TICKS - Math.max(tick + 1, shield.startTick()));
    }
    public Set<UUID> shieldedPlayersAt(long tickNumber) {
        var result = new LinkedHashSet<UUID>();
        shields.forEach((player, shield) -> {
            if (tickNumber >= shield.startTick() && tickNumber < shield.startTick() + SHIELD_TICKS) result.add(player);
        });
        return Set.copyOf(result);
    }
    public boolean shieldStartsAt(UUID player, long tickNumber) {
        var shield = shields.get(player);
        return shield != null && shield.startTick() == tickNumber;
    }
    public String encode() {
        var lines = new ArrayList<String>();
        lines.add(tick + "|" + revision + "|" + intervalMillis + "|" + nextTick + "|" + deadline);
        queues.forEach((id,q) -> lines.add("Q|" + id + "|" + q.encode()));
        receipts.forEach((id,c) -> lines.add("R|" + id + "|" + c));
        shields.forEach((player, shield) -> lines.add("S|" + player + "|" + shield.requestId() + "|" + shield.startTick()));
        return String.join("\n", lines);
    }
    public static ContinuousExecution decode(String value) {
        var lines = value.split("\n"); var h = lines[0].split("\\|");
        var c = new ContinuousExecution(Instant.EPOCH, Long.parseLong(h[2]), 1);
        c.tick = Long.parseLong(h[0]); c.revision = Long.parseLong(h[1]);
        c.nextTick = Instant.parse(h[3]); c.deadline = Instant.parse(h[4]);
        for (int i = 1; i < lines.length; i++) { var p = lines[i].split("\\|");
            if (p[0].equals("Q")) c.queues.put(UUID.fromString(p[1]), CommandQueue.decode(p[2]));
            else if (p[0].equals("R")) c.receipts.put(p[1], MovementOrder.valueOf(p[2]));
            else if (p[0].equals("S")) c.shields.put(UUID.fromString(p[1]), new Shield(UUID.fromString(p[2]), Long.parseLong(p[3])));
        }
        return c;
    }
}
