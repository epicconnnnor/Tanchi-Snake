package dev.connor.tanchi_snake.game;

import java.util.HashMap;
import java.util.HashSet;
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
        // TODO: head-on-head, head-on-body, self-collision

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

    private void kill(Snake s) {
        int newLevel = Math.max(1, s.level() - 2);
        s.setLevel(newLevel);
        s.resetFoodEaten();
        s.growTo(newLevel * TILES_PER_LEVEL);
    }
}