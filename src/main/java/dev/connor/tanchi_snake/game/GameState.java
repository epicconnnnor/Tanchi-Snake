package dev.connor.tanchi_snake.game;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GameState {

    private final int width;
    private final int height;
    private final Map<String, Snake> snakes;
    private final Set<Point> food;
    private int tick;
    private Snake winner;

    public GameState(int width, int height) {
        this.width = width;
        this.height = height;
        this.snakes = new HashMap<>();
        this.food = new HashSet<>();
        this.tick = 0;
    }

    public Set<Point> occupiedCells() {
        Set<Point> occupied = new HashSet<>();
        for (Snake s : snakes.values()) {
            occupied.addAll(s.body());
        }
        return occupied;
    }

    public void addSnake(Snake s) {
        snakes.put(s.id(), s);
    }

    public void removeSnake(String id) {
        snakes.remove(id);
    }

    public Collection<Snake> snakes() {
        return Collections.unmodifiableCollection(snakes.values());
    }

    public Set<Point> food() {
        return Collections.unmodifiableSet(food);
    }

    public void addFood(Point p) {
        food.add(p);
    }

    public void removeFood(Point p) {
        food.remove(p);
    }

    public boolean inBounds(Point p) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int tick() {
        return tick;
    }

    /** The snake that won the round, or null while it is still being played. */
    public Snake winner() {
        return winner;
    }

    public boolean hasWinner() {
        return winner != null;
    }

    /**
     * Records the winner. The first snake to get here keeps the title, so
     * later calls are ignored.
     */
    public void setWinner(Snake s) {
        if (winner == null) {
            winner = s;
        }
    }

    public void incrementTick() {
        tick++;
    }
}