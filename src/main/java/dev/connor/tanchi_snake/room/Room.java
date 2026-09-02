package dev.connor.tanchi_snake.room;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.connor.tanchi_snake.game.GameState;

/**
 * One room: its players, its board, and whose turn it is to press start.
 *
 * <p>Not thread safe. Every mutation happens on the scheduler thread; socket
 * threads only ever enqueue input.
 */
public class Room {

    public static final int MAX_PLAYERS = 8;
    public static final int BOARD_WIDTH = 32;
    public static final int BOARD_HEIGHT = 32;

    /** Nobody connected for this long and the room is torn down. */
    public static final long EMPTY_TTL_MILLIS = 20_000;

    /** Sentinel for "somebody is here", kept off the clock's value range. */
    private static final long NOT_EMPTY = -1;

    private final String code;
    private final GameState state;
    /** Insertion ordered, which is what makes "next in join order" meaningful. */
    private final Map<String, Player> players = new LinkedHashMap<>();

    private String hostSessionId;
    private RoomPhase phase = RoomPhase.LOBBY;

    /** When the room last went empty, or NOT_EMPTY while somebody is here. */
    private long emptySinceMillis = NOT_EMPTY;

    public Room(String code) {
        this.code = code;
        this.state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
    }

    public String code() {
        return code;
    }

    public GameState state() {
        return state;
    }

    public RoomPhase phase() {
        return phase;
    }

    public void setPhase(RoomPhase phase) {
        this.phase = phase;
    }

    public String hostSessionId() {
        return hostSessionId;
    }

    public boolean isHost(String sessionId) {
        return hostSessionId != null && hostSessionId.equals(sessionId);
    }

    /** Players in join order, connected or not. */
    public Collection<Player> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    public Player player(String sessionId) {
        return players.get(sessionId);
    }

    public int size() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public long connectedCount() {
        return players.values().stream().filter(Player::isConnected).count();
    }

    /**
     * Seats a player. The first one through the door is the host.
     *
     * @return the seated player, or null if the room is already full
     */
    public Player add(Player p) {
        if (isFull()) {
            return null;
        }
        players.put(p.sessionId(), p);
        if (hostSessionId == null) {
            hostSessionId = p.sessionId();
        }
        emptySinceMillis = NOT_EMPTY;
        return p;
    }

    /** Drops a player for good. Contrast with {@link #markDisconnected}. */
    public Player remove(String sessionId, long nowMillis) {
        Player gone = players.remove(sessionId);
        if (gone != null) {
            afterLeaving(sessionId, nowMillis);
        }
        return gone;
    }

    /**
     * Keeps the player's seat but marks them away, so a mid-round snake can be
     * frozen and picked back up if they return.
     */
    public Player markDisconnected(String sessionId, long nowMillis) {
        Player p = players.get(sessionId);
        if (p == null) {
            return null;
        }
        p.markDisconnected(nowMillis);
        afterLeaving(sessionId, nowMillis);
        return p;
    }

    public Player markConnected(String sessionId) {
        Player p = players.get(sessionId);
        if (p != null) {
            p.markConnected();
            emptySinceMillis = NOT_EMPTY;
            if (hostSessionId == null) {
                hostSessionId = sessionId;
            }
        }
        return p;
    }

    private void afterLeaving(String sessionId, long nowMillis) {
        if (sessionId.equals(hostSessionId)) {
            reassignHost();
        }
        if (connectedCount() == 0 && emptySinceMillis == NOT_EMPTY) {
            emptySinceMillis = nowMillis;
        }
    }

    /** Hands the room to the next connected player in join order. */
    private void reassignHost() {
        hostSessionId = null;
        for (Player p : players.values()) {
            if (p.isConnected()) {
                hostSessionId = p.sessionId();
                return;
            }
        }
    }

    public long emptySinceMillis() {
        return emptySinceMillis;
    }

    public boolean isExpired(long nowMillis) {
        return emptySinceMillis != NOT_EMPTY && nowMillis - emptySinceMillis >= EMPTY_TTL_MILLIS;
    }

    /** Session ids in join order, for broadcasting to just this room. */
    public List<String> sessionIds() {
        return new ArrayList<>(players.keySet());
    }
}
