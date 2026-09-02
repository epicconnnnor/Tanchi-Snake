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

    /** Builds a snake curled into an L, long enough to have a real interior. */
    private static Snake curledSnake(String id) {
        Snake s = new Snake(id, new Point(10, 10), Direction.RIGHT);
        s.move(true);                    // (11,10)
        s.move(true);                    // (12,10)
        s.setDirection(Direction.DOWN);
        s.move(true);                    // (12,11)
        s.setDirection(Direction.LEFT);
        s.move(true);                    // (11,11)
        return s;
    }

    @Test
    void headToHeadLowerLevelDiesHigherSurvivesUntouched() {
        GameState state = new GameState(32, 32);
        Snake high = new Snake("high", new Point(5, 5), Direction.RIGHT);
        high.setLevel(6);
        Snake low = new Snake("low", new Point(7, 5), Direction.LEFT);
        low.setLevel(3);
        state.addSnake(high);
        state.addSnake(low);

        new GameEngine().tick(state);

        // high took the contested cell and paid nothing.
        assertEquals(new Point(6, 5), high.head());
        assertEquals(6, high.level());
        assertEquals(0, high.stunTicks());
        // low lost the exchange and died.
        assertEquals(1, low.level());
        assertEquals(4, low.length());
    }

    @Test
    void headToHeadSameLevelStunsBothAndNeitherMoves() {
        GameState state = new GameState(32, 32);
        Snake a = new Snake("a", new Point(5, 5), Direction.RIGHT);
        a.setLevel(4);
        Snake b = new Snake("b", new Point(7, 5), Direction.LEFT);
        b.setLevel(4);
        state.addSnake(a);
        state.addSnake(b);

        new GameEngine().tick(state);

        // Neither died.
        assertEquals(4, a.level());
        assertEquals(4, b.level());
        // Neither moved.
        assertEquals(new Point(5, 5), a.head());
        assertEquals(new Point(7, 5), b.head());
        // Both stunned.
        assertEquals(GameEngine.STUN_TICKS, a.stunTicks());
        assertEquals(GameEngine.STUN_TICKS, b.stunTicks());
    }

    @Test
    void headIntoOwnBodyStunsButDoesNotKill() {
        GameState state = new GameState(32, 32);
        Snake s = curledSnake("s");
        s.setLevel(5);
        // Body is (11,11) (12,11) (12,10) (11,10) (10,10), head at (11,11).
        // Turning up aims at (11,10), one of its own middle segments.
        s.setDirection(Direction.UP);
        state.addSnake(s);

        new GameEngine().tick(state);

        // Stunned, not dead: level and length untouched, and it stayed put.
        assertEquals(5, s.level());
        assertEquals(5, s.length());
        assertEquals(new Point(11, 11), s.head());
        assertEquals(GameEngine.STUN_TICKS, s.stunTicks());
    }

    @Test
    void headIntoOwnVacatingTailIsLegal() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("s", new Point(10, 10), Direction.RIGHT);
        s.move(true);                    // (11,10)
        s.setDirection(Direction.DOWN);
        s.move(true);                    // (11,11)
        s.setDirection(Direction.LEFT);
        s.move(true);                    // (10,11)
        s.setLevel(5);
        // Body is (10,11) (11,11) (11,10) (10,10). Turning up aims at (10,10),
        // its own tail, which it vacates on this same tick.
        s.setDirection(Direction.UP);
        state.addSnake(s);

        new GameEngine().tick(state);

        assertEquals(new Point(10, 10), s.head());
        assertEquals(0, s.stunTicks());
        assertEquals(5, s.level());
    }
}
