package dev.connor.tanchi_snake.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A stun freezes a snake without moving whatever stunned it, so on the tick
 * the stun runs out the snake aims at the same cell and is stunned again. That
 * used to hold a snake still for the rest of the round. These are the tests
 * that say it cannot any more.
 */
class StunExpiryTest {

    /** Two equal-level snakes nose to nose: each tick they both claim (11,10). */
    private static GameState headToHeadDeadlock() {
        GameState state = new GameState(32, 32);
        Snake a = new Snake("a", new Point(10, 10), Direction.RIGHT);
        Snake b = new Snake("b", new Point(12, 10), Direction.LEFT);
        a.setLevel(5);
        b.setLevel(5);
        state.addSnake(a);
        state.addSnake(b);
        return state;
    }

    /** A snake curled so that the cell ahead of it is one of its own middles. */
    private static Snake foldedOntoItself(GameState state) {
        Snake s = new Snake("s", new Point(12, 10), Direction.LEFT);
        s.moveTo(new Point(11, 10), true);
        s.setDirection(Direction.DOWN);
        s.moveTo(new Point(11, 11), true);
        s.setDirection(Direction.RIGHT);
        s.moveTo(new Point(12, 11), true);
        s.setDirection(Direction.DOWN);
        s.moveTo(new Point(12, 12), true);
        s.setDirection(Direction.LEFT);
        s.moveTo(new Point(11, 12), true);
        // (11,11) is a middle segment, not the tail it is about to vacate.
        s.setDirection(Direction.UP);
        state.addSnake(s);
        return s;
    }

    private static Snake snake(GameState state, String id) {
        for (Snake s : state.snakes()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        throw new AssertionError("no snake " + id);
    }

    // --- the bug ---

    @Test
    void twoSnakesNoseToNoseDoNotFreezeForever() {
        GameState state = headToHeadDeadlock();
        GameEngine engine = new GameEngine(new Random(1));

        for (int i = 0; i < 200; i++) {
            engine.tick(state);
        }

        Snake a = snake(state, "a");
        Snake b = snake(state, "b");
        assertNotEquals(new Point(10, 10), a.head(), "a never got away from the stand-off");
        assertNotEquals(new Point(12, 10), b.head(), "b never got away from the stand-off");
    }

    @Test
    void aSnakeFoldedOntoItselfDoesNotFreezeForever() {
        GameState state = new GameState(32, 32);
        Snake s = foldedOntoItself(state);
        GameEngine engine = new GameEngine(new Random(1));

        for (int i = 0; i < 200; i++) {
            engine.tick(state);
        }

        assertNotEquals(new Point(11, 12), s.head(), "it never got out of its own coils");
    }

    @Test
    void noSnakeIsEverStuckPastTheLimit() {
        GameState state = headToHeadDeadlock();
        foldedOntoItself(state);
        GameEngine engine = new GameEngine(new Random(9));

        for (int i = 0; i < 500; i++) {
            engine.tick(state);
            for (Snake s : state.snakes()) {
                assertTrue(s.stuckTicks() <= GameEngine.STUN_DEATH_TICKS,
                        s.id() + " sat still for " + s.stuckTicks() + " ticks");
            }
        }
    }

    // --- the new rule ---

    @Test
    void aSnakeSurvivesRightUpToTheLimit() {
        GameState state = headToHeadDeadlock();
        GameEngine engine = new GameEngine(new Random(3));

        for (int i = 0; i < GameEngine.STUN_DEATH_TICKS; i++) {
            engine.tick(state);
        }

        Snake a = snake(state, "a");
        assertEquals(GameEngine.STUN_DEATH_TICKS, a.stuckTicks());
        assertEquals(5, a.level(), "no penalty until the limit is passed");
        assertEquals(new Point(10, 10), a.head(), "and it has not moved either");
    }

    @Test
    void oneTickPastTheLimitItDiesAndComesBack() {
        GameState state = headToHeadDeadlock();
        GameEngine engine = new GameEngine(new Random(3));

        for (int i = 0; i <= GameEngine.STUN_DEATH_TICKS; i++) {
            engine.tick(state);
        }

        Snake a = snake(state, "a");
        assertEquals(3, a.level(), "the usual two levels");
        assertEquals(3 * GameEngine.TILES_PER_LEVEL, a.length(), "resized to the new level");
        assertEquals(0, a.foodValueEaten());
        assertEquals(0, a.stunTicks(), "it comes back free, not frozen");
        assertEquals(0, a.stuckTicks(), "and with a clean sheet");
        assertNotEquals(new Point(10, 10), a.head(), "somewhere else on the board");
    }

    @Test
    void theStuckRunSurvivesBeingStunnedAgain() {
        GameState state = headToHeadDeadlock();
        GameEngine engine = new GameEngine(new Random(3));

        // Long enough to cross two whole STUN_TICKS cycles. Counting stuns
        // rather than ticks would have reset the run at each one.
        for (int i = 0; i < 25; i++) {
            engine.tick(state);
        }

        assertEquals(25, snake(state, "a").stuckTicks());
    }

    @Test
    void movingClearsTheStuckRun() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.noteStuckTick();
        s.noteStuckTick();
        state.addSnake(s);

        new GameEngine(new Random(4)).tick(state);

        assertEquals(0, s.stuckTicks());
        assertEquals(new Point(6, 5), s.head());
    }

    // --- the stacking that makes a respawn look like a solid block ---

    /*
     * growTo pads a snake by repeating its last cell, so a freshly respawned
     * one is N points on a single cell. That is what draws as a solid block.
     * It is NOT what stuns anything: the cells are deduplicated before the
     * collision check, and the head only ever aims at a neighbouring cell.
     */
    @Test
    void aStackedSnakeSitsOnOneCell() {
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.growTo(20);

        assertEquals(20, s.length(), "it is twenty segments long");
        assertEquals(1, new HashSet<>(s.body()).size(), "stacked on one cell");
    }

    @Test
    void aStackedSnakeMovesOffInsteadOfStunningItself() {
        GameState state = new GameState(32, 32);
        Snake s = new Snake("a", new Point(5, 5), Direction.RIGHT);
        s.setLevel(5);
        s.growTo(5 * GameEngine.TILES_PER_LEVEL);
        state.addSnake(s);

        GameEngine engine = new GameEngine(new Random(6));
        engine.tick(state);

        assertEquals(0, s.stunTicks(), "stacked cells are not a self-collision");
        assertEquals(0, s.stuckTicks());
        assertEquals(new Point(6, 5), s.head());

        // And it unrolls as it goes, rather than staying a block.
        for (int i = 0; i < 6; i++) {
            engine.tick(state);
        }
        assertTrue(new HashSet<>(s.body()).size() > 1, "still stacked after seven ticks");
        assertEquals(0, s.stunTicks());
    }

    // --- respawn placement ---

    @Test
    void respawnKeepsItsDistanceFromEveryOtherHead() {
        GameState state = headToHeadDeadlock();
        // Two more snakes parked well out of the way, so there are heads to
        // measure against that are not part of the stand-off.
        Snake c = new Snake("c", new Point(2, 2), Direction.RIGHT);
        Snake d = new Snake("d", new Point(20, 25), Direction.LEFT);
        c.stun(500);
        d.stun(500);
        state.addSnake(c);
        state.addSnake(d);

        GameEngine engine = new GameEngine(new Random(11));
        for (int i = 0; i <= GameEngine.STUN_DEATH_TICKS; i++) {
            engine.tick(state);
        }

        Snake a = snake(state, "a");
        for (Snake other : state.snakes()) {
            if (other == a) {
                continue;
            }
            assertTrue(GameEngine.ringDistance(state, a.head(), other.head())
                            >= GameEngine.RESPAWN_MIN_DISTANCE,
                    "respawned " + a.head() + " on top of " + other.id() + " at " + other.head());
        }
    }

    @Test
    void aBoardTooSmallForTheDistanceStillPlacesTheSnake() {
        // Nothing on a 10x10 ring is 8 apart: the furthest two cells get is 5.
        GameState state = new GameState(10, 10);
        Snake parked = new Snake("parked", new Point(7, 7), Direction.RIGHT);
        parked.stun(500);
        state.addSnake(parked);

        // The same fold as above, shifted onto the small board.
        Snake a = new Snake("a", new Point(3, 1), Direction.LEFT);
        a.moveTo(new Point(2, 1), true);
        a.setDirection(Direction.DOWN);
        a.moveTo(new Point(2, 2), true);
        a.setDirection(Direction.RIGHT);
        a.moveTo(new Point(3, 2), true);
        a.setDirection(Direction.DOWN);
        a.moveTo(new Point(3, 3), true);
        a.setDirection(Direction.LEFT);
        a.moveTo(new Point(2, 3), true);
        a.setDirection(Direction.UP); // into (2,2), a middle segment
        a.setLevel(4);
        state.addSnake(a);

        GameEngine engine = new GameEngine(new Random(13));
        for (int i = 0; i <= GameEngine.STUN_DEATH_TICKS; i++) {
            engine.tick(state);
        }

        assertEquals(2, a.level(), "it still took the death penalty");
        // Relaxing must still mean the roomiest cell going, which on a ring
        // this size is 5 away from the only other head.
        assertEquals(5, GameEngine.ringDistance(state, a.head(), parked.head()),
                "relaxed further than it had to");
    }

    @Test
    void respawnPlacementIsReproducibleForAGivenSeed() {
        assertEquals(respawnHeadAfterDeadlock(404), respawnHeadAfterDeadlock(404));
    }

    private static Point respawnHeadAfterDeadlock(long seed) {
        GameState state = headToHeadDeadlock();
        GameEngine engine = new GameEngine(new Random(seed));
        for (int i = 0; i <= GameEngine.STUN_DEATH_TICKS; i++) {
            engine.tick(state);
        }
        return snake(state, "a").head();
    }

    // --- distance on a board that wraps ---

    @Test
    void ringDistanceCountsTheShortWayRound() {
        GameState state = new GameState(32, 32);

        assertEquals(1, GameEngine.ringDistance(state, new Point(0, 5), new Point(31, 5)),
                "either side of the seam is one cell apart");
        assertEquals(1, GameEngine.ringDistance(state, new Point(5, 0), new Point(5, 31)));
        assertEquals(16, GameEngine.ringDistance(state, new Point(0, 0), new Point(16, 0)));
        assertEquals(3, GameEngine.ringDistance(state, new Point(1, 1), new Point(4, 3)),
                "Chebyshev takes the larger of the two axes");
        assertEquals(0, GameEngine.ringDistance(state, new Point(7, 7), new Point(7, 7)));
    }

    @Test
    void everyRespawnCellIsClearOfOtherBodies() {
        GameState state = headToHeadDeadlock();
        Snake blocker = new Snake("blocker", new Point(20, 20), Direction.RIGHT);
        for (int i = 0; i < 15; i++) {
            blocker.moveTo(blocker.head().move(Direction.RIGHT), true);
        }
        blocker.stun(500);
        state.addSnake(blocker);

        GameEngine engine = new GameEngine(new Random(21));
        for (int i = 0; i <= GameEngine.STUN_DEATH_TICKS; i++) {
            engine.tick(state);
        }

        Set<Point> blockerCells = new HashSet<>(blocker.body());
        List<Snake> revived = new ArrayList<>();
        revived.add(snake(state, "a"));
        revived.add(snake(state, "b"));
        for (Snake s : revived) {
            for (Point cell : s.body()) {
                assertFalse(blockerCells.contains(cell),
                        s.id() + " came back inside the blocker at " + cell);
            }
        }
    }
}
