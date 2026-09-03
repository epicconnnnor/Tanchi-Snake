package dev.connor.tanchi_snake.net;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.connor.tanchi_snake.room.Player;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomCodeGenerator;
import dev.connor.tanchi_snake.room.RoomPhase;
import dev.connor.tanchi_snake.room.SnakeNameGenerator;

/**
 * The registry of live rooms: creating them, letting players in and out, and
 * sweeping the ones nobody came back to.
 *
 * <p>Two identities are tracked per player. The playerId is stable and keys
 * their seat and their snake; the sessionId is whichever socket they are on,
 * and changes every time they reconnect.
 */
@Component
public class RoomManager {

    /** Random codes tried before falling back to a scan for a free one. */
    private static final int CODE_ATTEMPTS = 100;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomByPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> playerBySession = new ConcurrentHashMap<>();
    private final RoomCodeGenerator codes;
    private final SnakeNameGenerator names;
    private final Random random;
    private final Clock clock;

    public RoomManager() {
        this(new Random(), Clock.systemUTC());
    }

    public RoomManager(Random random, Clock clock) {
        this.codes = new RoomCodeGenerator(random);
        this.names = new SnakeNameGenerator(random);
        this.random = random;
        this.clock = clock;
    }

    public Room create() {
        String code = freeCode();
        Room room = new Room(code, random);
        rooms.put(code, room);
        return room;
    }

    private String freeCode() {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String code = codes.next();
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        // Vanishingly unlikely, but never hand back a code already in use.
        String code;
        do {
            code = codes.next();
        } while (rooms.containsKey(code));
        return code;
    }

    /** Looks a room up by code, tolerating junk and lowercase from clients. */
    public Room find(String code) {
        if (code == null) {
            return null;
        }
        return rooms.get(code.trim().toUpperCase());
    }

    public Collection<Room> rooms() {
        return rooms.values();
    }

    /** The room the socket is in, or null. */
    public Room roomOf(String sessionId) {
        String playerId = playerBySession.get(sessionId);
        return playerId == null ? null : roomOfPlayer(playerId);
    }

    public Room roomOfPlayer(String playerId) {
        String code = roomByPlayer.get(playerId);
        return code == null ? null : rooms.get(code);
    }

    /** The stable id behind a socket, or null if it belongs to nobody. */
    public String playerIdOf(String sessionId) {
        return playerBySession.get(sessionId);
    }

    /**
     * Seats a player, or brings a returning one back to the seat they left.
     *
     * <p>A client that has been here before sends back the playerId it was
     * given; that, not the socket, is what identifies them, since reconnecting
     * always means a brand new sessionId.
     *
     * <p>Never throws on bad input: the failure comes back in the result so the
     * caller can tell the client and carry on.
     */
    public JoinResult join(String code, String sessionId, String claimedPlayerId, String name) {
        Room room = find(code);
        if (room == null) {
            return JoinResult.failed(JoinResult.Failure.NO_SUCH_ROOM);
        }

        Player returning = claimedPlayerId == null ? null : room.player(claimedPlayerId);
        if (returning != null) {
            room.markConnected(claimedPlayerId, sessionId);
            // The snake is keyed by playerId, so it is still theirs.
            room.resumeSnake(claimedPlayerId);
            bind(sessionId, claimedPlayerId, room.code());
            return JoinResult.rejoined(room, returning);
        }

        if (room.isFull()) {
            return JoinResult.failed(JoinResult.Failure.ROOM_FULL);
        }

        Player seated = room.add(new Player(newPlayerId(), sessionId, names.normalise(name)));
        bind(sessionId, seated.playerId(), room.code());
        // Joining mid-round puts them straight on the board at level 1, with
        // no grace period.
        if (room.phase() == RoomPhase.RUNNING) {
            room.spawnSnakeFor(seated);
        }
        return JoinResult.joined(room, seated);
    }

    private String newPlayerId() {
        return UUID.randomUUID().toString();
    }

    private void bind(String sessionId, String playerId, String code) {
        playerBySession.put(sessionId, playerId);
        roomByPlayer.put(playerId, code);
    }

    /**
     * Marks a player away without giving up their seat, so a mid-round snake
     * can be resumed. Reassigns the host if they were holding it.
     */
    public Room disconnect(String sessionId) {
        String playerId = playerBySession.remove(sessionId);
        if (playerId == null) {
            return null;
        }
        Room room = roomOfPlayer(playerId);
        if (room != null) {
            room.markDisconnected(playerId, clock.millis());
            // Freeze right away rather than waiting for the next tick.
            room.holdDisconnectedSnakes();
        }
        return room;
    }

    /**
     * Removes a player for good: they asked to leave, or their reconnect
     * window lapsed. Frees the seat and takes their snake off the board.
     */
    public Room leave(String sessionId) {
        String playerId = playerBySession.remove(sessionId);
        if (playerId == null) {
            return null;
        }
        Room room = roomOfPlayer(playerId);
        if (room != null) {
            room.leaveNow(playerId, clock.millis());
        }
        roomByPlayer.remove(playerId);
        return room;
    }

    /** Renames a player, falling back to a generated name if they blanked it. */
    public String rename(String sessionId, String requested) {
        Room room = roomOf(sessionId);
        if (room == null) {
            return null;
        }
        Player p = room.player(playerBySession.get(sessionId));
        if (p == null) {
            return null;
        }
        p.setName(names.normalise(requested));
        return p.name();
    }

    /**
     * Destroys rooms that have sat with nobody connected past the deadline.
     *
     * @return the codes that were torn down
     */
    public List<String> sweepEmptyRooms() {
        long now = clock.millis();
        List<String> destroyed = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (room.isExpired(now)) {
                rooms.remove(room.code());
                for (String playerId : room.playerIds()) {
                    playerBySession.values().removeIf(playerId::equals);
                }
                // Catches any mapping left behind by a seat already given up.
                roomByPlayer.values().removeIf(code -> code.equals(room.code()));
                destroyed.add(room.code());
            }
        }
        return destroyed;
    }
}
