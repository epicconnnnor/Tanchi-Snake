package dev.connor.tanchi_snake.net;

import dev.connor.tanchi_snake.room.Player;
import dev.connor.tanchi_snake.room.Room;

/**
 * What came of a join attempt. Failures are values rather than exceptions so a
 * bad room code cannot take the game loop down with it.
 */
public record JoinResult(Room room, Player player, boolean rejoined, Failure failure) {

    public enum Failure {
        NO_SUCH_ROOM,
        ROOM_FULL,
        ALREADY_IN_ROOM
    }

    public static JoinResult joined(Room room, Player player) {
        return new JoinResult(room, player, false, null);
    }

    public static JoinResult rejoined(Room room, Player player) {
        return new JoinResult(room, player, true, null);
    }

    public static JoinResult failed(Failure failure) {
        return new JoinResult(null, null, false, failure);
    }

    public boolean ok() {
        return failure == null;
    }
}
