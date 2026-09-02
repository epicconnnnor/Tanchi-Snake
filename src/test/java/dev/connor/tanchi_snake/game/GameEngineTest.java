package dev.connor.tanchi_snake.game;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
        assertFalse(state.food().contains(new Point(6, 5)), "eaten food should be gone");
        assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size());
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

    @Test
    void stunnedSnakeBodyIsStillLethal() {
        GameState state = new GameState(32, 32);
        // b occupies (10,10) (11,10) (12,10) but is stunned, so it stays put.
        Snake b = horizontalSnake("b", new Point(10, 10), 3);
        b.stun(5);
        Snake a = new Snake("a", new Point(11, 11), Direction.UP);
        a.setLevel(5);
        state.addSnake(b);
        state.addSnake(a);

        new GameEngine(new Random(1)).tick(state);

        assertEquals(3, a.level());
        assertEquals(12, a.length());
        // b never moved and took no damage.
        assertEquals(new Point(12, 10), b.head());
        assertEquals(1, b.level());
    }

    @Test
    void stunnedSnakeTailIsLethalBecauseItNeverVacates() {
        GameState state = new GameState(32, 32);
        Snake b = horizontalSnake("b", new Point(10, 10), 3);
        b.stun(5);
        // (10,10) is b's tail. A moving snake would be vacating it, but a
        // stunned one is not, so running into it is fatal.
        Snake a = new Snake("a", new Point(10, 11), Direction.UP);
        a.setLevel(5);
        state.addSnake(b);
        state.addSnake(a);

        new GameEngine(new Random(1)).tick(state);

        assertEquals(3, a.level());
        assertEquals(12, a.length());
    }

    @Test
    void deathDropsTwoLevelsAndResizesToMatch() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(31, 5), Direction.RIGHT);
        s.setLevel(7);
        state.addSnake(s);

        new GameEngine(new Random(1)).tick(state);

        assertEquals(5, s.level());
        assertEquals(5 * GameEngine.TILES_PER_LEVEL, s.length());
        assertEquals(0, s.foodEaten());
    }

    @Test
    void deathLevelIsFlooredAtOne() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(31, 5), Direction.RIGHT);
        s.setLevel(2);
        state.addSnake(s);

        new GameEngine(new Random(1)).tick(state);

        assertEquals(1, s.level());
        assertEquals(GameEngine.TILES_PER_LEVEL, s.length());
    }

    @Test
    void respawnLandsOnACellWithClearanceAhead() {
        // A 4x1 board leaves exactly one spot with 3 clear cells ahead of a
        // snake facing right: (0,0). Anything further right runs off the edge.
        GameState state = new GameState(4, 1);
        Snake s = new Snake("a", new Point(3, 0), Direction.RIGHT);
        state.addSnake(s);

        new GameEngine(new Random(7)).tick(state);

        assertEquals(new Point(0, 0), s.head());
    }

    @Test
    void respawnAvoidsCellsOtherSnakesOccupy() {
        GameState state = new GameState(32, 32);
        Snake blocker = horizontalSnake("blocker", new Point(0, 0), 20);
        blocker.stun(100); // parked, so it stays put and stays in the way
        Snake s = new Snake("a", new Point(31, 5), Direction.RIGHT);
        state.addSnake(blocker);
        state.addSnake(s);

        new GameEngine(new Random(3)).tick(state);

        Set<Point> blockerCells = new HashSet<>(blocker.body());
        for (Point cell : s.body()) {
            assertFalse(blockerCells.contains(cell), "respawned onto the blocker at " + cell);
        }
        // And it still landed somewhere with room ahead of it.
        Point p = s.head();
        for (int i = 0; i <= GameEngine.SPAWN_CLEARANCE; i++) {
            assertTrue(state.inBounds(p), "no clearance ahead at " + p);
            p = p.move(Direction.RIGHT);
        }
    }

    @Test
    void respawnPlacementIsReproducibleForAGivenSeed() {
        assertEquals(respawnHeadWithSeed(99), respawnHeadWithSeed(99));
    }

    private static Point respawnHeadWithSeed(long seed) {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(31, 5), Direction.RIGHT);
        state.addSnake(s);
        new GameEngine(new Random(seed)).tick(state);
        return s.head();
    }

    @Test
    void tickKeepsTheBoardStockedWithFood() {
        GameState state = new GameState(32, 32);
        state.addSnake(new Snake("a", new Point(5, 5), Direction.RIGHT));

        new GameEngine(new Random(11)).tick(state);

        assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size());
    }

    @Test
    void foodNeverSpawnsOnASnakeOrOnExistingFood() {
        // A small board forces the spawner to work around what is already there.
        GameState state = new GameState(6, 6);
        Snake s = horizontalSnake("a", new Point(0, 0), 5);
        s.stun(100); // parked, so its cells stay where the assertion expects
        state.addSnake(s);

        new GameEngine(new Random(5)).tick(state);

        Set<Point> occupied = new HashSet<>(s.body());
        for (Point f : state.food()) {
            assertFalse(occupied.contains(f), "food spawned on the snake at " + f);
            assertTrue(state.inBounds(f), "food spawned off the board at " + f);
        }
        // A Set of food cannot hold duplicates, so distinctness is the size.
        assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size());
    }

    @Test
    void foodSpawningIsReproducibleForAGivenSeed() {
        assertEquals(foodAfterTickWithSeed(404), foodAfterTickWithSeed(404));
    }

    private static Set<Point> foodAfterTickWithSeed(long seed) {
        GameState state = new GameState(32, 32);
        state.addSnake(new Snake("a", new Point(5, 5), Direction.RIGHT));
        new GameEngine(new Random(seed)).tick(state);
        return new HashSet<>(state.food());
    }

    @Test
    void eatenFoodIsReplacedOnTheSameTick() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        state.addFood(new Point(6, 5));

        GameEngine engine = new GameEngine(new Random(13));
        engine.tick(state); // tops up to FOOD_ON_BOARD after the snake eats
        assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size());
    }

    @Test
    void spawnSnakePlacesAJoinerWithClearanceAhead() {
        // Only (0,0) has three clear cells ahead of a snake facing right.
        GameState state = new GameState(4, 1);

        Snake s = new GameEngine(new Random(2)).spawnSnake(state, "joiner", Direction.RIGHT);

        assertNotNull(s);
        assertEquals(new Point(0, 0), s.head());
        assertEquals("joiner", s.id());
        assertEquals(1, state.snakes().size());
    }

    @Test
    void spawnSnakeAvoidsCellsAlreadyOccupied() {
        GameState state = new GameState(32, 32);
        Snake sitting = horizontalSnake("sitting", new Point(0, 0), 20);
        state.addSnake(sitting);

        Snake joiner = new GameEngine(new Random(8)).spawnSnake(state, "joiner", Direction.RIGHT);

        assertNotNull(joiner);
        assertFalse(new HashSet<>(sitting.body()).contains(joiner.head()));
        Point p = joiner.head();
        for (int i = 0; i <= GameEngine.SPAWN_CLEARANCE; i++) {
            assertTrue(state.inBounds(p), "no clearance ahead at " + p);
            p = p.move(Direction.RIGHT);
        }
    }

    @Test
    void spawnSnakeReturnsNullWhenNoSpotHasClearance() {
        // Two cells wide cannot fit a snake plus three clear cells ahead.
        GameState state = new GameState(2, 1);

        assertNull(new GameEngine(new Random(4)).spawnSnake(state, "joiner", Direction.RIGHT));
        assertEquals(0, state.snakes().size());
    }
}
