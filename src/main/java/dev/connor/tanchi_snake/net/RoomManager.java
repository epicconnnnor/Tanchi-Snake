package dev.connor.tanchi_snake.net;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.connor.tanchi_snake.room.Player;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomCodeGenerator;

/**
 * The registry of live rooms: creating them, letting players in and out, and
 * sweeping the ones nobody came back to.
 */
@Component
public class RoomManager {

    /** Random codes tried before falling back to a scan for a free one. */
    private static final int CODE_ATTEMPTS = 100;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomBySession = new ConcurrentHashMap<>();
    private final RoomCodeGenerator codes;
    private final Clock clock;

    public RoomManager() {
        this(new Random(), Clock.systemUTC());
    }

    public RoomManager(Random random, Clock clock) {
        this.codes = new RoomCodeGenerator(random);
        this.clock = clock;
    }

    public Room create() {
        String code = freeCode();
        Room room = new Room(code);
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

    public Room roomOf(String sessionId) {
        String code = roomBySession.get(sessionId);
        return code == null ? null : rooms.get(code);
    }

    /**
     * Puts a player into a room, or brings a returning one back to the seat
     * they left. Never throws on bad input: the failure comes back in the
     * result so the caller can tell the client and carry on.
     */
    public JoinResult join(String code, String sessionId, String name) {
        Room room = find(code);
        if (room == null) {
            return JoinResult.failed(JoinResult.Failure.NO_SUCH_ROOM);
        }

        Player returning = room.player(sessionId);
        if (returning != null) {
            room.markConnected(sessionId);
            roomBySession.put(sessionId, room.code());
            return JoinResult.rejoined(room, returning);
        }

        if (room.isFull()) {
            return JoinResult.failed(JoinResult.Failure.ROOM_FULL);
        }

        Player seated = room.add(new Player(sessionId, name));
        roomBySession.put(sessionId, room.code());
        return JoinResult.joined(room, seated);
    }

    /**
     * Marks a player away without giving up their seat, so a mid-round snake
     * can be resumed. Reassigns the host if they were holding it.
     */
    public Room disconnect(String sessionId) {
        Room room = roomOf(sessionId);
        if (room != null) {
            room.markDisconnected(sessionId, clock.millis());
        }
        return room;
    }

    /** Removes a player for good, e.g. once their reconnect window lapses. */
    public Room leave(String sessionId) {
        Room room = roomOf(sessionId);
        if (room != null) {
            room.remove(sessionId, clock.millis());
        }
        roomBySession.remove(sessionId);
        return room;
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
                roomBySession.values().removeIf(code -> code.equals(room.code()));
                destroyed.add(room.code());
            }
        }
        return destroyed;
    }
}
