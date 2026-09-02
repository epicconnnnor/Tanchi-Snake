package dev.connor.tanchi_snake.game;

public record Point(int x, int y) {
    public Point move(Direction d) {
        return new Point(x + d.dx, y + d.dy);
    }
}