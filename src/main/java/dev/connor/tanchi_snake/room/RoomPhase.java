package dev.connor.tanchi_snake.room;

public enum RoomPhase {
    /** Gathering players; the host decides when to start. */
    LOBBY,
    /** The engine is ticking this room. */
    RUNNING,
    /** Someone won; the standings are on screen. */
    RESULTS
}
