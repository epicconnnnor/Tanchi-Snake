package dev.connor.tanchi_snake.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnakeTest {

    @Test
    void movesForwardWithoutGrowing() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.moveTo(new Point(6, 5), false);
        assertEquals(new Point(6, 5), s.head());
        assertEquals(1, s.length());
    }

    @Test
    void growsWhenToldTo() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.moveTo(new Point(6, 5), true);
        assertEquals(2, s.length());
    }

    /*
     * The board wraps, so where a snake ends up is the engine's call. A snake
     * puts its head wherever it is told, including clear across the board.
     */
    @Test
    void goesWhereverItIsTold() {
        Snake s = new Snake("a", new Point(31, 5), Direction.RIGHT);
        s.moveTo(new Point(0, 5), false);
        assertEquals(new Point(0, 5), s.head());
    }

    @Test
    void banksTheValueOfWhatItEats() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.eat(3);
        s.eat(2);
        assertEquals(5, s.foodValueEaten());
        s.resetFoodEaten();
        assertEquals(0, s.foodValueEaten());
    }

    @Test
    void ignoresReversal() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.setDirection(Direction.LEFT);
        assertEquals(Direction.RIGHT, s.direction());
    }

}