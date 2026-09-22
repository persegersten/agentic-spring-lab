package se.segersten.wreckage.game.domain;

import java.util.List;
import java.util.UUID;

public record PlayerProgram(UUID playerId, List<MovementOrder> hand, List<MovementOrder> orders) {
    public PlayerProgram {
        hand = List.copyOf(hand);
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
    public boolean ready() { return !hand.isEmpty() && orders.size() == hand.size(); }
    public PlayerProgram lock(List<MovementOrder> selected) {
        if (ready()) throw new IllegalStateException("Program is already locked");
        if (selected == null || selected.size() != hand.size() || !sameCards(hand, selected))
            throw new IllegalArgumentException("Program must contain each dealt card exactly once");
        return new PlayerProgram(playerId, hand, selected);
    }
    private static boolean sameCards(List<MovementOrder> a, List<MovementOrder> b) {
        var left = new java.util.ArrayList<>(a);
        for (var card : b) if (!left.remove(card)) return false;
        return left.isEmpty();
    }
}
