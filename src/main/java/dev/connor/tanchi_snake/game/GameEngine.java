package dev.connor.tanchi_snake.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameEngine {

    public static final int FOOD_PER_LEVEL = 5;
    public static final int TILES_PER_LEVEL = 4;
    public static final int STUN_TICKS = 10;
    public static final int WIN_LEVEL = 10;

    public void tick(GameState state) {

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
            intended.put(s, s.head().move(s.direction()));
        }

        // 3. PASS TWO — resolve collisions against the full picture.
        Set<Snake> dead = new HashSet<>();

        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            if (!state.inBounds(e.getValue())) {
                dead.add(e.getKey());
            }
        }

        // Bodies as they will look AFTER every snake has moved. Snapshotting
        // these before any outcome is applied is what keeps the result
        // independent of the order snakes happen to come out of the map.
        // A snake already dead this tick leaves the board, so its cells are
        // not lethal to anyone.
        Map<Snake, Set<Point>> bodiesAfterMove = new HashMap<>();
        for (Snake s : state.snakes()) {
            if (!dead.contains(s)) {
                bodiesAfterMove.put(s, bodyAfterMove(state, s, intended.get(s)));
            }
        }

        // Head into another snake's body: the snake with the head dies.
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            Snake s = e.getKey();
            if (dead.contains(s)) {
                continue;
            }
            for (Map.Entry<Snake, Set<Point>> other : bodiesAfterMove.entrySet()) {
                if (other.getKey() != s && other.getValue().contains(e.getValue())) {
                    dead.add(s);
                    break;
                }
            }
        }
        // TODO: head-on-head, self-collision

        for (Snake s : dead) {
            kill(s);
            intended.remove(s);
        }

        // 4. Apply surviving moves, growing if food was eaten.
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            Snake s = e.getKey();
            Point newHead = e.getValue();
            boolean ate = state.food().contains(newHead);

            s.move(ate);

            if (ate) {
                state.removeFood(newHead);
                s.eat();
                if (s.foodEaten() >= FOOD_PER_LEVEL) {
                    s.setLevel(s.level() + 1);
                    s.resetFoodEaten();
                    s.growTo(s.level() * TILES_PER_LEVEL);
                }
            }
        }

        state.incrementTick();
    }

    /**
     * The cells this snake's body will occupy once this tick's move is applied,
     * excluding the cell its head lands on.
     *
     * <p>TAIL RULE: a snake that moves without growing vacates its tail cell, so
     * that cell is deliberately left out. Moving into the cell another snake's
     * tail is leaving on this same tick is LEGAL — the two never share a cell.
     * A snake that grows keeps its tail, so that cell stays lethal.
     *
     * @param newHead where the snake is headed, or null if it is stunned and
     *                staying put
     */
    private Set<Point> bodyAfterMove(GameState state, Snake s, Point newHead) {
        List<Point> cells = new ArrayList<>(s.body()); // head first, tail last

        if (newHead == null) {
            // Stunned: nothing shifts, so every cell behind the head stays put.
            return new HashSet<>(cells.subList(1, cells.size()));
        }

        // The old head becomes the first body segment behind the new head.
        boolean grows = state.food().contains(newHead);
        int end = grows ? cells.size() : cells.size() - 1;
        return new HashSet<>(cells.subList(0, end));
    }

    private void kill(Snake s) {
        int newLevel = Math.max(1, s.level() - 2);
        s.setLevel(newLevel);
        s.resetFoodEaten();
        s.growTo(newLevel * TILES_PER_LEVEL);
    }
}
