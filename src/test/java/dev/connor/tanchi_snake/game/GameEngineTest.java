package dev.connor.tanchi_snake.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

    @Test
    void snakeMovesOneCellPerTick() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);

        new GameEngine().tick(state);

        assertEquals(new Point(6, 5), s.head());
        assertEquals(1, state.tick());
    }

    @Test
    void stunnedSnakeDoesNotMove() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.stun(3);
        state.addSnake(s);

        new GameEngine().tick(state);

        assertEquals(new Point(5, 5), s.head());
        assertEquals(2, s.stunTicks());
    }

    @Test
    void snakeDiesOutOfBounds() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(31, 5), Direction.RIGHT);
        s.setLevel(5);
        state.addSnake(s);

        new GameEngine().tick(state);

        assertEquals(3, s.level());
        assertEquals(12, s.length());
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