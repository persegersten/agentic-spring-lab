package se.segersten.wreckage.game.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.PlayerProgram;

public final class ProgrammingCardDealer {
    private final Map<MovementOrder, Integer> weights;
    private final RandomGenerator random;
    private final int totalWeight;

    public ProgrammingCardDealer(Map<MovementOrder, Integer> weights, RandomGenerator random) {
        this.weights = new LinkedHashMap<>(Objects.requireNonNull(weights));
        this.random = Objects.requireNonNull(random);
        if (this.weights.isEmpty() || this.weights.keySet().stream().anyMatch(card -> !card.isProgrammingCard())
                || this.weights.values().stream().anyMatch(weight -> weight == null || weight <= 0)) {
            throw new IllegalArgumentException("Programming card weights must be positive and dealable");
        }
        this.totalWeight = this.weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<MovementOrder> deal() {
        List<MovementOrder> hand = new ArrayList<>(PlayerProgram.HAND_SIZE);
        for (int index = 0; index < PlayerProgram.HAND_SIZE; index++) hand.add(draw());
        return List.copyOf(hand);
    }

    private MovementOrder draw() {
        int ticket = random.nextInt(totalWeight);
        for (var entry : weights.entrySet()) {
            ticket -= entry.getValue();
            if (ticket < 0) return entry.getKey();
        }
        throw new IllegalStateException("Unable to draw programming card");
    }
}
