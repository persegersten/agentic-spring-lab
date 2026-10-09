package se.segersten.wreckage.game.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import se.segersten.wreckage.game.domain.MovementOrder;

@Configuration
public class ProgrammingCardConfiguration {
    @Bean
    @ConditionalOnMissingBean(RandomGenerator.class)
    RandomGenerator programmingCardRandomGenerator() {
        return RandomGenerator.getDefault();
    }

    @Bean
    ProgrammingCardDealer programmingCardDealer(RandomGenerator programmingCardRandomGenerator,
            @Value("${wreckage.cards.weights.forward-1:20}") int forward1,
            @Value("${wreckage.cards.weights.forward-2:15}") int forward2,
            @Value("${wreckage.cards.weights.forward-3:10}") int forward3,
            @Value("${wreckage.cards.weights.backward-1:10}") int backward1,
            @Value("${wreckage.cards.weights.turn-left:15}") int turnLeft,
            @Value("${wreckage.cards.weights.turn-right:15}") int turnRight,
            @Value("${wreckage.cards.weights.u-turn:5}") int uTurn) {
        Map<MovementOrder, Integer> weights = new LinkedHashMap<>();
        weights.put(MovementOrder.FORWARD_1, forward1);
        weights.put(MovementOrder.FORWARD_2, forward2);
        weights.put(MovementOrder.FORWARD_3, forward3);
        weights.put(MovementOrder.REVERSE_1, backward1);
        weights.put(MovementOrder.TURN_LEFT, turnLeft);
        weights.put(MovementOrder.TURN_RIGHT, turnRight);
        weights.put(MovementOrder.U_TURN, uTurn);
        return new ProgrammingCardDealer(weights, programmingCardRandomGenerator);
    }
}
