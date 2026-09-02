package dev.connor.tanchi_snake.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.Collection;

public class Snake {
    public Collection<Point> body() {
        return Collections.unmodifiableCollection(body);
    }

    public String id() {
        return id;
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

    private final String id;
    private final Deque<Point> body;
    private Direction direction;
    private int level;
    private int stunTicks;

    public Snake(String id, Point start, Direction direction) {
        this.id = id;
        this.direction = direction;
        this.level = 1;
        this.stunTicks = 0;
        this.body = new ArrayDeque<>();
        this.body.addFirst(start);
    }

    public Point head() {
        return body.getFirst();
    }

    public int length() {
        return body.size();
    }

    public void move(boolean grow) {
        Point newHead = head().move(direction);
        body.addFirst(newHead);
        if (!grow) {
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

    public void stun(int ticks) {
        this.stunTicks = ticks;
    }

    public void tickStun() {
        if (stunTicks > 0)
            stunTicks--;
    }

}
