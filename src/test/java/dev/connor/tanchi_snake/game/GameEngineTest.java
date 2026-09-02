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

    /** Builds a snake occupying a horizontal run of cells, head at the right end. */
    private static Snake horizontalSnake(String id, Point tail, int length) {
        Snake s = new Snake(id, tail, Direction.RIGHT);
        for (int i = 1; i < length; i++) {
            s.move(true);
        }
        return s;
    }

    @Test
    void headIntoAnotherSnakeBodyKillsTheSnakeWithTheHead() {
        GameState state = new GameState(32, 32);
        // b occupies (10,10) (11,10) (12,10), head at (12,10) moving right.
        Snake b = horizontalSnake("b", new Point(10, 10), 3);
        Snake a = new Snake("a", new Point(11, 11), Direction.UP);
        a.setLevel(5);
        state.addSnake(b);
        state.addSnake(a);

        new GameEngine().tick(state);

        // a drove its head into b's middle segment.
        assertEquals(3, a.level());
        assertEquals(12, a.length());
        // b is untouched and moved on.
        assertEquals(1, b.level());
        assertEquals(new Point(13, 10), b.head());
    }

    @Test
    void collisionUsesPostMoveBodiesNotPreMove() {
        GameState state = new GameState(32, 32);
        // b's head is at (12,10) now, but after this tick that cell holds a
        // body segment, so moving into it is still fatal.
        Snake b = horizontalSnake("b", new Point(10, 10), 3);
        Snake a = new Snake("a", new Point(12, 11), Direction.UP);
        a.setLevel(5);
        state.addSnake(b);
        state.addSnake(a);

        new GameEngine().tick(state);

        assertEquals(3, a.level());
        assertEquals(12, a.length());
    }

    @Test
    void movingIntoAVacatingTailIsLegal() {
        GameState state = new GameState(32, 32);
        // b occupies (10,10) (11,10) (12,10) and moves right, so it vacates
        // (10,10) on this tick. a claiming that cell is not a collision.
        Snake b = horizontalSnake("b", new Point(10, 10), 3);
        Snake a = new Snake("a", new Point(10, 11), Direction.UP);
        a.setLevel(5);
        state.addSnake(b);
        state.addSnake(a);

        new GameEngine().tick(state);

        assertEquals(new Point(10, 10), a.head());
        assertEquals(5, a.level());
        assertEquals(1, a.length());
    }

    @Test
    void aGrowingSnakeKeepsItsTailCellLethal() {
        GameState state = new GameState(32, 32);
        Snake b = horizontalSnake("b", new Point(10, 10), 3);
        state.addFood(new Point(13, 10)); // b eats and so keeps its tail
        Snake a = new Snake("a", new Point(10, 11), Direction.UP);
        a.setLevel(5);
        state.addSnake(b);
        state.addSnake(a);

        new GameEngine().tick(state);

        assertEquals(3, a.level());
        assertEquals(12, a.length());
        assertEquals(4, b.length());
    }
}
