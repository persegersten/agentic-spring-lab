package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import se.segersten.wreckage.game.domain.MovementOrder;

class ProgrammingCardDealerTest {
    @Test void dealsEightCardsDeterministicallyAndAllowsDuplicates() {
        var dealer = new ProgrammingCardDealer(Map.of(MovementOrder.FORWARD_3, 10), new Random(17));
        assertThat(dealer.deal()).hasSize(8).containsOnly(MovementOrder.FORWARD_3);
    }

    @Test void usesRelativeConfigurableWeights() {
        Map<MovementOrder, Integer> weights = new LinkedHashMap<>();
        weights.put(MovementOrder.FORWARD_1, 1);
        weights.put(MovementOrder.U_TURN, 1);
        var dealer = new ProgrammingCardDealer(weights, new Random(4));
        assertThat(dealer.deal()).containsOnly(MovementOrder.FORWARD_1, MovementOrder.U_TURN);
    }
}
