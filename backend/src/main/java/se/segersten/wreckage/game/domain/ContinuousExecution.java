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
    public ContinuousExecution(Instant now, long interval, long duration) {
        if (interval < 1 || duration < 1) throw new IllegalArgumentException("Timing must be positive");
        intervalMillis = interval; nextTick = now.plusMillis(interval); deadline = now.plusMillis(duration);
    }
    public boolean duplicate(UUID player, UUID request, MovementOrder command) {
        if (request == null) throw new IllegalArgumentException("requestId is required");
        var old = receipts.get(player + ":" + request);
        if (old != null && old != command) throw new IllegalStateException("Request ID reused with different command");
        return old != null;
    }
    public void append(UUID player, UUID request, MovementOrder command) {
        queues.get(player).append(command); receipts.put(player + ":" + request, command); revision++;
    }
    public String encode() {
        var lines = new ArrayList<String>();
        lines.add(tick + "|" + revision + "|" + intervalMillis + "|" + nextTick + "|" + deadline);
        queues.forEach((id,q) -> lines.add("Q|" + id + "|" + q.encode()));
        receipts.forEach((id,c) -> lines.add("R|" + id + "|" + c));
        return String.join("\n", lines);
    }
    public static ContinuousExecution decode(String value) {
        var lines = value.split("\n"); var h = lines[0].split("\\|");
        var c = new ContinuousExecution(Instant.EPOCH, Long.parseLong(h[2]), 1);
        c.tick = Long.parseLong(h[0]); c.revision = Long.parseLong(h[1]);
        c.nextTick = Instant.parse(h[3]); c.deadline = Instant.parse(h[4]);
        for (int i = 1; i < lines.length; i++) { var p = lines[i].split("\\|");
            if (p[0].equals("Q")) c.queues.put(UUID.fromString(p[1]), CommandQueue.decode(p[2]));
            else c.receipts.put(p[1], MovementOrder.valueOf(p[2]));
        }
        return c;
    }
}
