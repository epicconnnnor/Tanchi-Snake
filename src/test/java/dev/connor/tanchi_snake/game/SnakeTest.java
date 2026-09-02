package dev.connor.tanchi_snake.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnakeTest {

    @Test
    void movesForwardWithoutGrowing() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.move(false);
        assertEquals(new Point(6, 5), s.head());
        assertEquals(1, s.length());
    }

    @Test
    void growsWhenToldTo() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.move(true);
        assertEquals(2, s.length());
    }

    @Test
    void ignoresReversal() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.setDirection(Direction.LEFT);
        assertEquals(Direction.RIGHT, s.direction());
    }

    @Test
    void eatingFoodGrowsSnake() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        state.addFood(new Point(6, 5));

        new GameEngine().tick(state);

        assertEquals(2, s.length());
        assertEquals(1, s.foodEaten());
        assertTrue(state.food().isEmpty());
    }

    @Test
    void eatingFoodGrowsSnake() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        state.addFood(new Point(6, 5));

        new GameEngine().tick(state);

        assertEquals(2, s.length());
        assertEquals(1, s.foodEaten());
        assertTrue(state.food().isEmpty());
    }
}