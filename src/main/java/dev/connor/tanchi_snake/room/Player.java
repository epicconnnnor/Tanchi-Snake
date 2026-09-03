package dev.connor.tanchi_snake.room;

/**
 * One seat in a room.
 *
 * <p>A player has two identities. The playerId is theirs for the life of the
 * seat and is what keys their snake on the board, so it survives a dropped
 * connection. The sessionId is whichever socket they are on right now, and is
 * rebound every time they reconnect.
 */
public class Player {

    private final String playerId;
    private String sessionId;
    private String name;
    private boolean ready;
    private boolean connected;
    private long disconnectedAtMillis;
    private int lastKnownLevel;
    private int lastKnownLevelTick;
    private int colorIndex = UNASSIGNED;

    /** Colour slot before a room has handed one out. */
    public static final int UNASSIGNED = -1;

    public Player(String playerId, String sessionId, String name) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.name = name;
        this.ready = false;
        this.connected = true;
    }

    /**
     * Which colour this player draws in, assigned by the room on arrival and
     * held for the life of the seat, so it survives a reconnect.
     */
    public int colorIndex() {
        return colorIndex;
    }

    public void setColorIndex(int colorIndex) {
        this.colorIndex = colorIndex;
    }

    /** Stable for as long as the player holds the seat. Keys their snake. */
    public String playerId() {
        return playerId;
    }

    /** The socket they are on now, or the last one they were on. */
    public String sessionId() {
        return sessionId;
    }

    public void bindSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isConnected() {
        return connected;
    }

    /** The moment the player dropped, used to time the reconnect window. */
    public long disconnectedAtMillis() {
        return disconnectedAtMillis;
    }

    public void markDisconnected(long nowMillis) {
        this.connected = false;
        this.disconnectedAtMillis = nowMillis;
    }

    public void markConnected() {
        this.connected = true;
        this.disconnectedAtMillis = 0;
    }

    /**
     * Where the player stood when their snake left the board. Keeps them on the
     * results screen after a disconnect outlives the reconnect window.
     */
    public int lastKnownLevel() {
        return lastKnownLevel;
    }

    public int lastKnownLevelTick() {
        return lastKnownLevelTick;
    }

    public void rememberStanding(int level, int levelTick) {
        this.lastKnownLevel = level;
        this.lastKnownLevelTick = levelTick;
    }
}
