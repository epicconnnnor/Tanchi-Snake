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

        // 3. PASS TWO — resolve every outcome against the full picture. Each
        // stage below reads only inputs that were already settled before it
        // ran, so nothing depends on the order snakes come out of the map.
        Set<Snake> dead = new HashSet<>();
        Set<Snake> stunned = new HashSet<>();

        // 3a. Off the board.
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            if (!state.inBounds(e.getValue())) {
                dead.add(e.getKey());
            }
        }

        // 3b. Head into head — two or more snakes claiming the same cell.
        Map<Point, List<Snake>> contested = new HashMap<>();
        for (Map.Entry<Snake, Point> e : intended.entrySet()) {
            if (!dead.contains(e.getKey())) {
                contested.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }
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

        // 4. Apply the outcomes.
        for (Snake s : dead) {
            kill(s);
        }
        for (Snake s : stunned) {
            s.stun(STUN_TICKS);
        }

        // 5. Apply surviving moves, growing if food was eaten.
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
