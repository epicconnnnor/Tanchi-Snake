package dev.connor.tanchi_snake.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    void snakeWrapsOffTheRightEdge() {
        assertEquals(new Point(0, 5), headAfterLeaving(new Point(31, 5), Direction.RIGHT));
    }

    @Test
    void snakeWrapsOffTheLeftEdge() {
        assertEquals(new Point(31, 5), headAfterLeaving(new Point(0, 5), Direction.LEFT));
    }

    @Test
    void snakeWrapsOffTheTopEdge() {
        assertEquals(new Point(5, 31), headAfterLeaving(new Point(5, 0), Direction.UP));
    }

    @Test
    void snakeWrapsOffTheBottomEdge() {
        assertEquals(new Point(5, 0), headAfterLeaving(new Point(5, 31), Direction.DOWN));
    }

    @Test
    void wrappingCostsNeitherLevelNorLength() {
        GameState state = new GameState(32, 32);
        Snake s = horizontalSnake("a", new Point(28, 5), 4); // head at (31,5)
        s.setLevel(5);
        state.addSnake(s);

        new GameEngine().tick(state);

        assertEquals(new Point(0, 5), s.head());
        assertEquals(5, s.level(), "walking off the edge is not a death");
        assertEquals(4, s.length());
    }

    /** Walks a lone snake over the given edge and hands back where it lands. */
    private static Point headAfterLeaving(Point from, Direction facing) {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", from, facing);
        state.addSnake(s);

        new GameEngine().tick(state);

        return s.head();
    }

    @Test
    void eatingFoodGrowsSnake() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        state.addFood(new Point(6, 5));

        new GameEngine().tick(state);

        assertEquals(2, s.length());
        assertEquals(1, s.foodValueEaten());
        assertFalse(state.hasFood(new Point(6, 5)), "eaten food should be gone");
        assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size());
    }

    /** Builds a snake occupying a horizontal run of cells, head at the right end. */
    /** Grows a snake one cell along the way it is facing. */
    private static void step(Snake s) {
        s.moveTo(s.head().move(s.direction()), true);
    }

    private static Snake horizontalSnake(String id, Point tail, int length) {
        Snake s = new Snake(id, tail, Direction.RIGHT);
        for (int i = 1; i < length; i++) {
            step(s);
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
        step(s);                    // (11,10)
        step(s);                    // (12,10)
        s.setDirection(Direction.DOWN);
        step(s);                    // (12,11)
        s.setDirection(Direction.LEFT);
        step(s);                    // (11,11)
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
        step(s);                    // (11,10)
        s.setDirection(Direction.DOWN);
        step(s);                    // (11,11)
        s.setDirection(Direction.LEFT);
        step(s);                    // (10,11)
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

    /**
     * Parks a long stunned snake along row 0. Nothing dies of the board edge
     * any more, so a test that needs a death aims a snake at this instead.
     */
    private static void parkAWallInFrontOf(GameState state) {
        Snake blocker = horizontalSnake("blocker", new Point(0, 0), 20);
        blocker.stun(100);
        state.addSnake(blocker);
    }

    @Test
    void deathDropsTwoLevelsAndResizesToMatch() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 1), Direction.UP);
        s.setLevel(7);
        state.addSnake(s);
        parkAWallInFrontOf(state);

        new GameEngine(new Random(1)).tick(state);

        assertEquals(5, s.level());
        assertEquals(5 * GameEngine.TILES_PER_LEVEL, s.length());
        assertEquals(0, s.foodValueEaten());
    }

    @Test
    void deathLevelIsFlooredAtOne() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 1), Direction.UP);
        s.setLevel(2);
        state.addSnake(s);
        parkAWallInFrontOf(state);

        new GameEngine(new Random(1)).tick(state);

        assertEquals(1, s.level());
        assertEquals(GameEngine.TILES_PER_LEVEL, s.length());
    }

    @Test
    void respawnLandsOnACellWithClearanceAhead() {
        // A 6x1 board with (1,0) and (2,0) taken leaves exactly one start with
        // four free cells ahead of it, counting the wrap: (3,0) runs 3-4-5-0.
        GameState state = new GameState(6, 1);
        Snake blocker = horizontalSnake("blocker", new Point(1, 0), 2);
        blocker.stun(100); // parked, so it stays in the way
        Snake s = new Snake("a", new Point(0, 0), Direction.RIGHT);
        state.addSnake(blocker);
        state.addSnake(s);

        new GameEngine(new Random(7)).tick(state); // s runs into the blocker

        assertEquals(new Point(3, 0), s.head());
    }

    @Test
    void respawnAvoidsCellsOtherSnakesOccupy() {
        GameState state = new GameState(32, 32);
        Snake blocker = horizontalSnake("blocker", new Point(0, 0), 20);
        blocker.stun(100); // parked, so it stays put and stays in the way
        Snake s = new Snake("a", new Point(5, 1), Direction.UP); // aimed at it
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
            assertFalse(blockerCells.contains(p), "no clearance ahead at " + p);
            p = state.wrap(p.move(s.direction()));
        }
    }

    @Test
    void respawnPlacementIsReproducibleForAGivenSeed() {
        assertEquals(respawnHeadWithSeed(99), respawnHeadWithSeed(99));
    }

    private static Point respawnHeadWithSeed(long seed) {
        GameState state = new GameState(32, 32);
        Snake blocker = horizontalSnake("blocker", new Point(0, 0), 20);
        blocker.stun(100);
        Snake s = new Snake("a", new Point(5, 1), Direction.UP); // aimed at it
        state.addSnake(blocker);
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
        for (Point f : state.food().keySet()) {
            assertFalse(occupied.contains(f), "food spawned on the snake at " + f);
            assertTrue(state.inBounds(f), "food spawned off the board at " + f);
        }
        // A Set of food cannot hold duplicates, so distinctness is the size.
        assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size());
    }

    @Test
    void foodIsOnlyEverWorthOneToThree() {
        for (int value : spawnedValues(1_000, 77)) {
            assertTrue(value >= 1 && value <= GameEngine.MAX_FOOD_VALUE,
                    "food worth " + value + " is off the scale");
        }
    }

    @Test
    void foodValueFollowsTheSpawnWeighting() {
        List<Integer> values = spawnedValues(1_200, 2024);

        double ones = share(values, 1);
        double twos = share(values, 2);
        double threes = share(values, 3);

        // Bounds are loose enough to describe the 60/30/10 intent rather than
        // to pin down one seed's exact run.
        assertTrue(ones > 0.55 && ones < 0.65, "value 1 came out at " + ones);
        assertTrue(twos > 0.25 && twos < 0.35, "value 2 came out at " + twos);
        assertTrue(threes > 0.05 && threes < 0.15, "value 3 came out at " + threes);
    }

    @Test
    void foodValueSpawningIsReproducibleForAGivenSeed() {
        assertEquals(spawnedValues(300, 5150), spawnedValues(300, 5150));
    }

    /**
     * Clears the board and lets the engine restock it, over and over, so the
     * value roll is exercised a sample at a time.
     */
    private static List<Integer> spawnedValues(int wanted, long seed) {
        GameState state = new GameState(32, 32);
        GameEngine engine = new GameEngine(new Random(seed));
        List<Integer> values = new ArrayList<>();

        while (values.size() < wanted) {
            engine.tick(state);
            values.addAll(state.food().values());
            for (Point p : new HashSet<>(state.food().keySet())) {
                state.removeFood(p);
            }
        }
        return values;
    }

    private static double share(List<Integer> values, int value) {
        int hits = 0;
        for (int v : values) {
            if (v == value) {
                hits++;
            }
        }
        return (double) hits / values.size();
    }

    @Test
    void eatingBanksTheFoodValueNotThePiece() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        state.addFood(new Point(6, 5), 3);

        new GameEngine(new Random(17)).tick(state);

        assertEquals(3, s.foodValueEaten(), "one piece, three towards the level");
    }

    @Test
    void aRichEnoughMouthfulLevelsTheSnakeUp() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.eat(GameEngine.FOOD_PER_LEVEL - 3);
        state.addSnake(s);
        state.addFood(new Point(6, 5), 3);

        new GameEngine(new Random(19)).tick(state);

        assertEquals(2, s.level());
        assertEquals(0, s.foodValueEaten(), "the counter starts again on a level");
    }

    @Test
    void foodSpawningIsReproducibleForAGivenSeed() {
        assertEquals(foodAfterTickWithSeed(404), foodAfterTickWithSeed(404));
    }

    private static Set<Point> foodAfterTickWithSeed(long seed) {
        GameState state = new GameState(32, 32);
        state.addSnake(new Snake("a", new Point(5, 5), Direction.RIGHT));
        new GameEngine(new Random(seed)).tick(state);
        return new HashSet<>(state.food().keySet());
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
        // With (1,0) and (2,0) taken, only (3,0) has four free cells ahead.
        GameState state = new GameState(6, 1);
        state.addSnake(horizontalSnake("sitting", new Point(1, 0), 2));

        Snake s = new GameEngine(new Random(2)).spawnSnake(state, "joiner", Direction.RIGHT);

        assertNotNull(s);
        assertEquals(new Point(3, 0), s.head());
        assertEquals("joiner", s.id());
        assertEquals(2, state.snakes().size());
    }

    @Test
    void spawnSnakeAvoidsCellsAlreadyOccupied() {
        GameState state = new GameState(32, 32);
        Snake sitting = horizontalSnake("sitting", new Point(0, 0), 20);
        state.addSnake(sitting);

        Snake joiner = new GameEngine(new Random(8)).spawnSnake(state, "joiner", Direction.RIGHT);

        assertNotNull(joiner);
        Set<Point> sittingCells = new HashSet<>(sitting.body());
        assertFalse(sittingCells.contains(joiner.head()));
        Point p = joiner.head();
        for (int i = 0; i <= GameEngine.SPAWN_CLEARANCE; i++) {
            assertFalse(sittingCells.contains(p), "no clearance ahead at " + p);
            p = state.wrap(p.move(Direction.RIGHT));
        }
    }

    @Test
    void spawnSnakeReturnsNullWhenNoSpotHasClearance() {
        // A run of four wraps the whole of a 4x1 board, so a single occupied
        // cell is enough to spoil every start on it.
        GameState state = new GameState(4, 1);
        state.addSnake(new Snake("sitting", new Point(0, 0), Direction.RIGHT));

        assertNull(new GameEngine(new Random(4)).spawnSnake(state, "joiner", Direction.RIGHT));
        assertEquals(1, state.snakes().size());
    }

    /** A snake one mouthful short of levelling up, with that food in front of it. */
    private static Snake onTheBrinkOfLevel(GameState state, String id, Point at, int level) {
        Snake s = new Snake(id, at, Direction.RIGHT);
        s.setLevel(level - 1);
        s.eat(GameEngine.FOOD_PER_LEVEL - 1);
        state.addSnake(s);
        state.addFood(at.move(Direction.RIGHT));
        return s;
    }

    @Test
    void noWinnerWhileEveryoneIsBelowTheWinLevel() {
        GameState state = new GameState(32, 32);
        Snake s = onTheBrinkOfLevel(state, "a", new Point(5, 5), GameEngine.WIN_LEVEL - 1);

        new GameEngine(new Random(21)).tick(state);

        assertEquals(GameEngine.WIN_LEVEL - 1, s.level());
        assertNull(state.winner());
        assertFalse(state.hasWinner());
    }

    @Test
    void reachingTheWinLevelWinsTheRound() {
        GameState state = new GameState(32, 32);
        Snake s = onTheBrinkOfLevel(state, "a", new Point(5, 5), GameEngine.WIN_LEVEL);

        new GameEngine(new Random(21)).tick(state);

        assertEquals(GameEngine.WIN_LEVEL, s.level());
        assertTrue(state.hasWinner());
        assertSame(s, state.winner());
    }

    @Test
    void theBoardFreezesOnceTheRoundIsWon() {
        GameState state = new GameState(32, 32);
        Snake winner = onTheBrinkOfLevel(state, "a", new Point(5, 5), GameEngine.WIN_LEVEL);
        Snake other = new Snake("b", new Point(20, 20), Direction.RIGHT);
        state.addSnake(other);

        GameEngine engine = new GameEngine(new Random(21));
        engine.tick(state);
        int tickAtWin = state.tick();
        Point frozen = other.head();

        engine.tick(state);

        assertSame(winner, state.winner());
        assertEquals(tickAtWin, state.tick(), "the clock should stop");
        assertEquals(frozen, other.head(), "nobody should move after the win");
    }

    @Test
    void theFirstWinnerKeepsTheTitle() {
        GameState state = new GameState(32, 32);
        Snake first = onTheBrinkOfLevel(state, "a", new Point(5, 5), GameEngine.WIN_LEVEL);

        new GameEngine(new Random(21)).tick(state);
        assertSame(first, state.winner());

        // A later arrival at the win level does not take the title away.
        Snake latecomer = new Snake("b", new Point(20, 20), Direction.RIGHT);
        latecomer.setLevel(GameEngine.WIN_LEVEL + 2);
        state.addSnake(latecomer);
        new GameEngine(new Random(21)).tick(state);

        assertSame(first, state.winner());
    }

    @Test
    void aTieOnTheSameTickResolvesTheSameWayEveryTime() {
        assertEquals("a", winnerIdForTiedSnakes());
        // Rerun from scratch: the result must not ride on map iteration order.
        assertEquals("a", winnerIdForTiedSnakes());
    }

    private static String winnerIdForTiedSnakes() {
        GameState state = new GameState(32, 32);
        // Ids added in reverse so insertion order cannot be what decides it.
        onTheBrinkOfLevel(state, "z", new Point(20, 20), GameEngine.WIN_LEVEL);
        onTheBrinkOfLevel(state, "a", new Point(5, 5), GameEngine.WIN_LEVEL);

        new GameEngine(new Random(21)).tick(state);

        assertTrue(state.hasWinner());
        return state.winner().id();
    }

    @Test
    void levellingUpRecordsTheTickItHappenedOn() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        GameEngine engine = new GameEngine(new Random(31));

        // Run the board forward so the level-up lands on a non-zero tick.
        for (int i = 0; i < 3; i++) {
            engine.tick(state);
        }
        assertEquals(0, s.levelReachedTick(), "no level-up yet");

        int tickBeforeLevelUp = state.tick();
        s.setLevel(4);
        s.eat(GameEngine.FOOD_PER_LEVEL - 1);
        state.addFood(s.head().move(s.direction()));
        engine.tick(state);

        assertEquals(5, s.level());
        assertEquals(tickBeforeLevelUp, s.levelReachedTick());
    }

    @Test
    void aLaterLevelUpOverwritesTheEarlierStamp() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        state.addSnake(s);
        GameEngine engine = new GameEngine(new Random(32));

        int first = levelUpNow(state, engine, s);
        int second = levelUpNow(state, engine, s);

        assertTrue(second > first, "second level-up should be on a later tick");
        assertEquals(second, s.levelReachedTick());
    }

    /** Feeds the snake its last mouthful and returns the tick it levelled on. */
    private static int levelUpNow(GameState state, GameEngine engine, Snake s) {
        s.eat(GameEngine.FOOD_PER_LEVEL - 1 - s.foodValueEaten());
        state.addFood(s.head().move(s.direction()));
        int tick = state.tick();
        engine.tick(state);
        return tick;
    }
}
