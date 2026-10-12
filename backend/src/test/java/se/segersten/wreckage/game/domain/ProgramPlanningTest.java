package se.segersten.wreckage.game.domain;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProgramPlanningTest {
    @Test void shieldDoesNotOccupyAProgrammingSlot() {
        UUID id = UUID.randomUUID();
        List<MovementOrder> hand = List.of(MovementOrder.FORWARD_1, MovementOrder.FORWARD_2,
                MovementOrder.FORWARD_3, MovementOrder.REVERSE_1, MovementOrder.TURN_LEFT,
                MovementOrder.TURN_RIGHT, MovementOrder.U_TURN, MovementOrder.LASER);
        PlayerProgram program = PlayerProgram.dealt(id, 5, hand)
                .lock(hand.subList(0, 5), true);
        assertThat(program.commands()).hasSize(5);
        assertThat(program.shieldSelected()).isTrue();
    }

    @Test void validatesHandMultiplicityAndKeepsLaserAsAProgrammingCard() {
        UUID id = UUID.randomUUID();
        List<MovementOrder> hand = List.of(MovementOrder.LASER, MovementOrder.FORWARD_1,
                MovementOrder.FORWARD_2, MovementOrder.FORWARD_3, MovementOrder.REVERSE_1,
                MovementOrder.TURN_LEFT, MovementOrder.TURN_RIGHT, MovementOrder.U_TURN);
        PlayerProgram program = PlayerProgram.dealt(id, 5, hand);
        assertThatThrownBy(() -> program.lock(Collections.nCopies(5, MovementOrder.LASER), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MovementOrder.LASER.isProgrammingCard()).isTrue();
        assertThat(program.lock(hand.subList(0, 5), false).ready()).isTrue();
    }
}
