package dev.connor.tanchi_snake.room;

public class Player {

    private final String sessionId;
    private String name;
    private boolean ready;
    private boolean connected;
    private long disconnectedAtMillis;
    private int lastKnownLevel;
    private int lastKnownLevelTick;

    public Player(String sessionId, String name) {
        this.sessionId = sessionId;
        this.name = name;
        this.ready = false;
        this.connected = true;
    }

    public String sessionId() {
        return sessionId;
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

    public void markConnected() {
        this.connected = true;
        this.disconnectedAtMillis = 0;
    }
}
