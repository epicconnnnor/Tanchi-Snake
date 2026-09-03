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
    /** Cell to the value that cell of food is worth. */
    private final Map<Point, Integer> food;
    private int tick;
    private Snake winner;

    public GameState(int width, int height) {
        this.width = width;
        this.height = height;
        this.snakes = new HashMap<>();
        this.food = new HashMap<>();
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

    /** Every cell of food on the board, mapped to what it is worth. */
    public Map<Point, Integer> food() {
        return Collections.unmodifiableMap(food);
    }

    public boolean hasFood(Point p) {
        return food.containsKey(p);
    }

    /** What the food on this cell is worth, or 0 if there is none. */
    public int foodValue(Point p) {
        return food.getOrDefault(p, 0);
    }

    public void addFood(Point p, int value) {
        food.put(p, value);
    }

    /** Food worth the base value, which is all most food is worth. */
    public void addFood(Point p) {
        addFood(p, 1);
    }

    public void removeFood(Point p) {
        food.remove(p);
    }

    /**
     * True if this cell is on the board. The edges no longer kill anyone, so
     * this is a question about coordinates rather than a rule; {@link #wrap}
     * is what the engine reaches for.
     */
    public boolean inBounds(Point p) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    /**
     * Folds a point back onto the board. The edges wrap, so a snake leaving one
     * side reappears on the opposite one. Java's % keeps the sign of its left
     * operand, hence the extra add before the second modulo.
     */
    public Point wrap(Point p) {
        return new Point(
                ((p.x() % width) + width) % width,
                ((p.y() % height) + height) % height);
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