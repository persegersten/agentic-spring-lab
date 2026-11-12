package se.segersten.wreckage.game.domain;

import java.util.*;

/** Positions are stored in execution order (1 through 5). */
public final class CommandQueue {
    private final MovementOrder[] slots = new MovementOrder[5];
    public int append(MovementOrder command) {
        if (command == null || !command.isProgrammingCard()) throw new IllegalArgumentException("Invalid command");
        for (int i = 3; i < 5; i++) if (slots[i] == null) { slots[i] = command; return i + 1; }
        throw new IllegalStateException("Queue is full");
    }
    public MovementOrder head() { return slots[0] == null ? MovementOrder.WAIT : slots[0]; }
    public List<MovementOrder> snapshot() { return Collections.unmodifiableList(Arrays.asList(slots.clone())); }
    public void shift() { System.arraycopy(slots, 1, slots, 0, 4); slots[4] = null; }
    public void clear() { Arrays.fill(slots, null); }
    public String encode() { return String.join(",", snapshot().stream().map(c -> c == null ? "-" : c.name()).toList()); }
    public static CommandQueue decode(String value) {
        var q = new CommandQueue(); var parts = value.split(",");
        for (int i = 0; i < 5; i++) q.slots[i] = parts[i].equals("-") ? null : MovementOrder.valueOf(parts[i]);
        return q;
    }
}
