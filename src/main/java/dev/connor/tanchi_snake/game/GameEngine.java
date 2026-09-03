package dev.connor.tanchi_snake.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GameEngine {

    /**
     * Food VALUE a snake must swallow to climb a level, not a number of
     * pieces. Food averages 1.5 apiece, so 8 is a little over five mouthfuls
     * -- about what the old flat 5-per-level asked for. Tune it here.
     */
    public static final int FOOD_PER_LEVEL = 8;

    public static final int TILES_PER_LEVEL = 4;
    public static final int STUN_TICKS = 10;
    public static final int WIN_LEVEL = 10;

    /**
     * Ticks a snake may sit still before it is killed off and moved on. A
     * stun freezes a snake without moving whatever stunned it, so the pair
     * can lock each other in place indefinitely; this is the ceiling on that.
     * Five seconds at a 100ms tick.
     */
    public static final int STUN_DEATH_TICKS = 50;

    /**
     * How far a snake arriving on the board is kept from every other living
     * head, as a Chebyshev distance across the wrapping board. Applies to
     * joiners and to respawns alike: landing next to somebody is the same
     * unfair start either way. Relaxed to the roomiest cell available when
     * nothing on the board is this clear.
     */
    public static final int RESPAWN_MIN_DISTANCE = 8;

    /** How much food the board is kept stocked with, scaled to a 48x48 board. */
    public static final int FOOD_ON_BOARD = 12;

    /** The most a single piece of food can be worth. */
    public static final int MAX_FOOD_VALUE = 3;

    /**
     * Percentage chance of each food value, from 1 upwards: 60% worth 1, 30%
     * worth 2, 10% worth 3. Must total 100.
     */
    private static final int[] FOOD_VALUE_ODDS = { 60, 30, 10 };

    /** Clear cells a snake needs ahead of it to be given a spot. */
    public static final int SPAWN_CLEARANCE = 3;

    /** Random cells tried before falling back to a sweep of the board. */
    private static final int PLACEMENT_ATTEMPTS = 200;

    private final Random random;

    public GameEngine() {
        this(new Random());
    }

    public GameEngine(Random random) {
        this.random = random;
    }

    public void tick(GameState state) {

        // The round is over once somebody has won: the board stops here.
        if (state.hasWinner()) {
            return;
        }

        // 1. Stun countdown. A stunned snake sits still this tick.
        for (Snake s : state.snakes()) {
            s.tickStun();
        }

        // 2. PASS ONE — where does everyone WANT to go? Nothing moves yet.
        Map<Snake, Point> intended = new HashMap<>();
        for (Snake s : state.snakes()) {
            if (s.stunTicks() > 0) {
                continue;
            }
            // The edges wrap, so this is always a cell on the board.
            intended.put(s, state.wrap(s.head().move(s.direction())));
        }

        // 3. PASS TWO — resolve every outcome against the full picture. Each
        // stage below reads only inputs that were already settled before it
        // ran, so nothing depends on the order snakes come out of the map.
        Set<Snake> dead = new HashSet<>();
        Set<Snake> stunned = new HashSet<>();

        // There is no death by wall: the board wraps, so running into another
        // snake is the only way off it.

        // 3b. Head into head — two or more snakes claiming the same cell.
        Map<Point, List<Snake>> contested = new HashMap<>();
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            contested.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        for (List<Snake> rivals : contested.values()) {
            if (rivals.size() < 2) {
                continue;
            }
            int topLevel = 0;
            for (Snake s : rivals) {
                topLevel = Math.max(topLevel, s.level());
            }
            List<Snake> atTop = new ArrayList<>();
            for (Snake s : rivals) {
                if (s.level() < topLevel) {
                    dead.add(s);
                } else {
                    atTop.add(s);
                }
            }
            // A single highest level takes the cell untouched. A tie stuns
            // everyone still standing: nobody dies and nobody moves.
            if (atTop.size() > 1) {
                stunned.addAll(atTop);
            }
        }

        // 3c. Head into own body — stunned, not dead.
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            Snake s = e.getKey();
            if (dead.contains(s) || stunned.contains(s)) {
                continue;
            }
            if (bodyAfterMove(state, s, e.getValue()).contains(e.getValue())) {
                stunned.add(s);
            }
        }

        // Movement is settled here: the dead and the freshly stunned stay put.
        intended.keySet().removeAll(dead);
        intended.keySet().removeAll(stunned);

        // 3d. Bodies as they will look AFTER every surviving move. Snapshotting
        // these before any death is applied is what keeps the outcome
        // order-independent. A snake already dead leaves the board, so its
        // cells are not lethal to anyone. A stunned snake stays where it is,
        // and its body remains just as lethal as a moving one's.
        Map<Snake, Set<Point>> bodiesAfterMove = new HashMap<>();
        for (Snake s : state.snakes()) {
            if (!dead.contains(s)) {
                bodiesAfterMove.put(s, bodyAfterMove(state, s, intended.get(s)));
            }
        }

        // 3e. Head into another snake's body: the snake with the head dies.
        Set<Snake> ranIntoBody = new HashSet<>();
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            Snake s = e.getKey();
            for (Map.Entry<Snake, Set<Point>> other : bodiesAfterMove.entrySet()) {
                if (other.getKey() != s && other.getValue().contains(e.getValue())) {
                    ranIntoBody.add(s);
                    break;
                }
            }
        }
        dead.addAll(ranIntoBody);
        intended.keySet().removeAll(ranIntoBody);

        /*
         * A snake that has sat still too long is not getting free on its own.
         * Whatever stunned it has not moved either, so on the tick its stun
         * runs out it aims at the same cell and is stunned all over again.
         * Killing it is what breaks that loop; it comes back elsewhere.
         */
        Set<Snake> stuckTooLong = new HashSet<>();
        for (Snake s : state.snakes()) {
            if (intended.containsKey(s)) {
                continue; // moving this tick; the move clears its run
            }
            if (dead.contains(s)) {
                continue; // already dying, and respawning clears the run
            }
            s.noteStuckTick();
            if (s.stuckTicks() > STUN_DEATH_TICKS) {
                stuckTooLong.add(s);
            }
        }
        dead.addAll(stuckTooLong);
        // They are dying, not freezing for another round.
        stunned.removeAll(stuckTooLong);

        // 4. Apply the outcomes.
        // Sorted so that two snakes dying on the same tick are always given
        // their new spots in the same order, keeping placement reproducible
        // for a given seed.
        List<Snake> toRespawn = new ArrayList<>(dead);
        toRespawn.sort(Comparator.comparing(Snake::id));
        // Survivors have not moved yet, so hold their target cells against
        // respawn placement too.
        Set<Point> claimed = new HashSet<>(intended.values());
        for (Snake s : toRespawn) {
            kill(state, s, claimed);
        }
        for (Snake s : stunned) {
            s.stun(STUN_TICKS);
        }

        // 5. Apply surviving moves, growing if food was eaten.
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            Snake s = e.getKey();
            Point newHead = e.getValue();
            int value = state.foodValue(newHead);
            boolean ate = value > 0;

            s.moveTo(newHead, ate);
            // Actually going somewhere is the only thing that clears the run.
            s.clearStuckTicks();

            if (ate) {
                state.removeFood(newHead);
                s.eat(value);
                if (s.foodValueEaten() >= FOOD_PER_LEVEL) {
                    s.setLevel(s.level() + 1);
                    // Stamped on the way up only. Ranking compares snakes on
                    // the same level, and the earlier arrival takes it.
                    s.setLevelReachedTick(state.tick());
                    s.resetFoodEaten();
                    s.growTo(s.level() * TILES_PER_LEVEL);
                }
            }
        }

        awardWin(state);
        if (!state.hasWinner()) {
            replenishFood(state);
        }
        state.incrementTick();
    }

    /**
     * Places a new snake for a joining player, under the same clearance rule
     * respawning uses.
     *
     * @return the snake, already added to the state, or null if the board has
     *         no room for one
     */
    public Snake spawnSnake(GameState state, String id, Direction direction) {
        Point spot = findSpawn(state, direction, null, Set.of());

        if (spot == null) {
            return null;
        }
        Snake s = new Snake(id, spot, direction);
        state.addSnake(s);
        return s;
    }

    /**
     * Hands the round to the first snake to reach {@link #WIN_LEVEL}. Snakes
     * that get there on the same tick are ranked by id, so a tie always
     * resolves the same way instead of following map iteration order.
     */
    private void awardWin(GameState state) {
        List<Snake> contenders = new ArrayList<>();
        for (Snake s : state.snakes()) {
            if (s.level() >= WIN_LEVEL) {
                contenders.add(s);
            }
        }
        if (contenders.isEmpty()) {
            return;
        }
        contenders.sort(Comparator.comparing(Snake::id));
        state.setWinner(contenders.get(0));
    }

    /** Tops the board back up to {@link #FOOD_ON_BOARD} pieces of food. */
    private void replenishFood(GameState state) {
        Set<Point> taken = new HashSet<>(state.occupiedCells());
        taken.addAll(state.food().keySet());

        while (state.food().size() < FOOD_ON_BOARD) {
            Point p = findFreeCell(state, taken);
            if (p == null) {
                break; // board is full; try again next tick
            }
            state.addFood(p, rollFoodValue());
            taken.add(p);
        }
    }

    /**
     * Picks what a new piece of food is worth, against
     * {@link #FOOD_VALUE_ODDS}. Uses the injected Random so a seeded board
     * plays out the same way twice.
     */
    private int rollFoodValue() {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (int i = 0; i < FOOD_VALUE_ODDS.length; i++) {
            cumulative += FOOD_VALUE_ODDS[i];
            if (roll < cumulative) {
                return i + 1;
            }
        }
        return MAX_FOOD_VALUE;
    }

    /** A random cell holding neither snake nor food, or null if there is none. */
    private Point findFreeCell(GameState state, Set<Point> taken) {
        for (int i = 0; i < PLACEMENT_ATTEMPTS; i++) {
            Point p = new Point(random.nextInt(state.width()), random.nextInt(state.height()));
            if (!taken.contains(p)) {
                return p;
            }
        }

        // Crowded board: sweep it rather than let the random search give up.
        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                Point p = new Point(x, y);
                if (!taken.contains(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * The cells this snake's body will occupy once this tick's move is applied,
     * excluding the cell its head lands on.
     *
     * <p>TAIL RULE: a snake that moves without growing vacates its tail cell, so
     * that cell is deliberately left out. Moving into the cell another snake's
     * tail is leaving on this same tick is LEGAL — the two never share a cell.
     * The same holds for a snake's own tail. A snake that grows keeps its tail,
     * so that cell stays lethal.
     *
     * @param newHead where the snake is headed, or null if it is staying put
     */
    private Set<Point> bodyAfterMove(GameState state, Snake s, Point newHead) {
        List<Point> cells = new ArrayList<>(s.body()); // head first, tail last

        if (newHead == null) {
            // Not moving, so every cell behind the head stays exactly where it is.
            return new HashSet<>(cells.subList(1, cells.size()));
        }

        // The old head becomes the first body segment behind the new head.
        boolean grows = state.hasFood(newHead);
        int end = grows ? cells.size() : cells.size() - 1;
        return new HashSet<>(cells.subList(0, end));
    }

    private void kill(GameState state, Snake s, Set<Point> claimed) {
        int newLevel = Math.max(1, s.level() - 2);
        s.setLevel(newLevel);
        s.resetFoodEaten();
        // A fresh start, whatever state it died in.
        s.stun(0);
        s.clearStuckTicks();

        Point spot = findSpawn(state, s.direction(), s, claimed);
        if (spot != null) {
            s.respawnAt(spot);
        }
        // Grow last: respawnAt collapses the snake to one segment, and a snake
        // that could not be placed still owes the board its new length.
        s.growTo(newLevel * TILES_PER_LEVEL);
    }

    /**
     * Picks where a snake arrives, whether it is joining or coming back from a
     * death. The cell needs {@link #SPAWN_CLEARANCE} free cells ahead of it so
     * nothing starts nose-first into a body, and it is kept
     * {@link #RESPAWN_MIN_DISTANCE} from every other living head. When the
     * board is too crowded for that, the roomiest cell going beats refusing to
     * place the snake at all.
     *
     * @param arriving the snake being placed, whose own body does not block
     *                 it; null for a joiner, which has no body yet
     * @return the spot, or null if the board has nowhere to put it
     */
    private Point findSpawn(GameState state, Direction facing, Snake arriving,
            Set<Point> claimed) {
        Set<Point> blocked = new HashSet<>(claimed);
        List<Point> heads = new ArrayList<>();
        for (Snake other : state.snakes()) {
            if (other != arriving) {
                blocked.addAll(other.body());
                if (other.length() > 0) {
                    heads.add(other.head());
                }
            }
        }

        List<Point> roomy = new ArrayList<>();
        List<Point> furthest = new ArrayList<>();
        int furthestSoFar = -1;

        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                Point p = new Point(x, y);
                if (!hasClearRun(state, blocked, p, facing)) {
                    continue;
                }
                int room = distanceToNearestHead(state, p, heads);
                if (room >= RESPAWN_MIN_DISTANCE) {
                    roomy.add(p);
                } else if (room > furthestSoFar) {
                    furthestSoFar = room;
                    furthest.clear();
                    furthest.add(p);
                } else if (room == furthestSoFar) {
                    furthest.add(p);
                }
            }
        }

        List<Point> pool = roomy.isEmpty() ? furthest : roomy;
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** Integer.MAX_VALUE when this snake has the board to itself. */
    private static int distanceToNearestHead(GameState state, Point p, List<Point> heads) {
        int nearest = Integer.MAX_VALUE;
        for (Point head : heads) {
            nearest = Math.min(nearest, ringDistance(state, p, head));
        }
        return nearest;
    }

    /**
     * Chebyshev distance, the long way round the board included. The edges
     * wrap, so two cells either side of one are neighbours, and plain
     * subtraction would call them the width of the board apart.
     */
    static int ringDistance(GameState state, Point a, Point b) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        return Math.max(
                Math.min(dx, state.width() - dx),
                Math.min(dy, state.height() - dy));
    }

    /**
     * True if the start cell and the run ahead of it are free. The run wraps
     * with the board, so no cell is disqualified for being near an edge any
     * more -- only for being occupied.
     */
    private boolean hasClearRun(GameState state, Set<Point> blocked, Point start, Direction d) {
        Point p = state.wrap(start);
        for (int i = 0; i <= SPAWN_CLEARANCE; i++) {
            if (blocked.contains(p)) {
                return false;
            }
            p = state.wrap(p.move(d));
        }
        return true;
    }
}
