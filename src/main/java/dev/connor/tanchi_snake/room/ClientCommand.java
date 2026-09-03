package dev.connor.tanchi_snake.room;

import dev.connor.tanchi_snake.game.Direction;

/**
 * One validated instruction from a client, waiting to be applied.
 *
 * <p>Socket threads only ever build these and put them on a queue. Everything
 * they describe is carried out later, on the scheduler thread.
 */
public record ClientCommand(
        String sessionId,
        Type type,
        String roomCode,
        String name,
        String claimedPlayerId,
        Direction direction) {

    public enum Type {
        CREATE,
        JOIN,
        RENAME,
        READY,
        START,
        TURN,
        PLAY_AGAIN,
        LEAVE,
        DISCONNECT
    }

    public static ClientCommand create(String sessionId, String name) {
        return new ClientCommand(sessionId, Type.CREATE, null, name, null, null);
    }

    public static ClientCommand join(String sessionId, String roomCode, String name) {
        return join(sessionId, roomCode, name, null);
    }

    /** A returning client sends back the playerId it was given on joining. */
    public static ClientCommand join(String sessionId, String roomCode, String name,
            String claimedPlayerId) {
        return new ClientCommand(sessionId, Type.JOIN, roomCode, name, claimedPlayerId, null);
    }

    public static ClientCommand rename(String sessionId, String name) {
        return new ClientCommand(sessionId, Type.RENAME, null, name, null, null);
    }

    public static ClientCommand ready(String sessionId) {
        return new ClientCommand(sessionId, Type.READY, null, null, null, null);
    }

    public static ClientCommand start(String sessionId) {
        return new ClientCommand(sessionId, Type.START, null, null, null, null);
    }

    public static ClientCommand turn(String sessionId, Direction direction) {
        return new ClientCommand(sessionId, Type.TURN, null, null, null, direction);
    }

    public static ClientCommand playAgain(String sessionId) {
        return new ClientCommand(sessionId, Type.PLAY_AGAIN, null, null, null, null);
    }

    /** Quitting on purpose, as opposed to a socket dropping. */
    public static ClientCommand leave(String sessionId) {
        return new ClientCommand(sessionId, Type.LEAVE, null, null, null, null);
    }

    public static ClientCommand disconnect(String sessionId) {
        return new ClientCommand(sessionId, Type.DISCONNECT, null, null, null, null);
    }

    /**
     * True for the commands handled outside any one room's queue. Leaving
     * belongs here with disconnecting: both take a player out of the room
     * bookkeeping rather than acting on the board.
     */
    public boolean isLobbyLevel() {
        return type == Type.CREATE || type == Type.JOIN
                || type == Type.LEAVE || type == Type.DISCONNECT;
    }
}
