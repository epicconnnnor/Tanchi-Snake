package dev.connor.tanchi_snake.room;

public class Player {

    private final String sessionId;
    private String name;
    private boolean ready;
    private boolean connected;
    private long disconnectedAtMillis;

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

    public void markConnected() {
        this.connected = true;
        this.disconnectedAtMillis = 0;
    }
}
