package dev.connor.tanchi_snake.game;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

public class Snake {

    private final String id;
    private final Deque<Point> body;
    private Direction direction;
    private int level;
    private int stunTicks;
    private int foodEaten;
    private int levelReachedTick;

    public Snake(String id, Point start, Direction direction) {
        this.id = id;
        this.direction = direction;
        this.level = 1;
        this.stunTicks = 0;
        this.foodEaten = 0;
        this.levelReachedTick = 0;
        this.body = new ArrayDeque<>();
        this.body.addFirst(start);
    }

    // --- accessors ---

    public String id() {
        return id;
    }

    public Collection<Point> body() {
        return Collections.unmodifiableCollection(body);
    }

    public Point head() {
        return body.getFirst();
    }

    public int length() {
        return body.size();
    }

    public Direction direction() {
        return direction;
    }

    public int level() {
        return level;
    }

    public int stunTicks() {
        return stunTicks;
    }

    public int foodEaten() {
        return foodEaten;
    }

    /**
     * The tick on which this snake last levelled up. Ranking uses it to break
     * ties between snakes sitting on the same level: the earlier arrival wins.
     */
    public int levelReachedTick() {
        return levelReachedTick;
    }

    // --- mutators ---

    public void move(boolean grow) {
        Point newHead = head().move(direction);
        body.addFirst(newHead);
        if (!grow) {
            body.removeLast();
        }
    }

    /**
     * Moves the whole snake to a fresh cell, collapsed down to a single
     * segment. Callers grow it back to the length they want afterwards.
     */
    public void respawnAt(Point start) {
        body.clear();
        body.addFirst(start);
    }

    public void growTo(int targetLength) {
        while (body.size() < targetLength) {
            body.addLast(body.getLast());
        }
        while (body.size() > targetLength) {
            body.removeLast();
        }
    }

    public void setDirection(Direction d) {
        if (!d.isOpposite(direction)) {
            this.direction = d;
        }
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setLevelReachedTick(int tick) {
        this.levelReachedTick = tick;
    }

    public void stun(int ticks) {
        this.stunTicks = ticks;
    }

    public void tickStun() {
        if (stunTicks > 0) {
            stunTicks--;
        }
    }

    public void eat() {
        foodEaten++;
    }

    public void resetFoodEaten() {
        foodEaten = 0;
    }
}