package dev.connor.tanchi_snake.loop;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.connor.tanchi_snake.game.Snake;
import dev.connor.tanchi_snake.net.CommandBus;
import dev.connor.tanchi_snake.net.GameSocketHandler;
import dev.connor.tanchi_snake.net.JoinResult;
import dev.connor.tanchi_snake.net.RoomManager;
import dev.connor.tanchi_snake.net.StateMessage;
import dev.connor.tanchi_snake.room.ClientCommand;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomPhase;

/**
 * The single thread that owns the game.
 *
 * <p>Socket threads queue commands; this class drains them, advances each
 * running room, and broadcasts. Nothing else writes to a GameState, which is
 * what lets the game classes stay free of locks.
 */
@Component
public class GameLoop {

    public static final long TICK_MILLIS = 100;

    private final RoomManager rooms;
    private final CommandBus bus;
    private final GameSocketHandler sockets;
    private final Clock clock;

    @Autowired
    public GameLoop(RoomManager rooms, CommandBus bus, GameSocketHandler sockets) {
        this(rooms, bus, sockets, Clock.systemUTC());
    }

    /** Lets tests drive the clock that times reconnect windows and sweeps. */
    public GameLoop(RoomManager rooms, CommandBus bus, GameSocketHandler sockets, Clock clock) {
        this.rooms = rooms;
        this.bus = bus;
        this.sockets = sockets;
        this.clock = clock;
    }

    @Scheduled(fixedRate = TICK_MILLIS)
    public void tick() {
        long now = clock.millis();

        applyLobbyCommands();

        for (Room room : rooms.rooms()) {
            // One bad room must not stop the others from ticking.
            try {
                advance(room, now);
            } catch (RuntimeException failed) {
                // Nothing to do but leave this room be until the next tick.
            }
        }

        rooms.sweepEmptyRooms();
    }

    /** Commands from players who are not in a room yet, or are leaving one. */
    private void applyLobbyCommands() {
        for (ClientCommand c : bus.drain()) {
            try {
                switch (c.type()) {
                    case CREATE -> {
                        Room room = rooms.create();
                        JoinResult seated = rooms.join(room.code(), c.sessionId(), c.name());
                        replyToJoin(c.sessionId(), seated);
                    }
                    case JOIN -> replyToJoin(c.sessionId(), rooms.join(c.roomCode(), c.sessionId(), c.name()));
                    case DISCONNECT -> rooms.disconnect(c.sessionId());
                    default -> {
                        // Not a lobby-level command; the room queue handles it.
                    }
                }
            } catch (RuntimeException bad) {
                // A malformed command is dropped, not fatal.
            }
        }
    }

    private void replyToJoin(String sessionId, JoinResult result) {
        if (result.ok()) {
            sockets.send(sessionId, "{\"type\":\"joined\",\"room\":\"" + result.room().code() + "\"}");
        } else {
            sockets.send(sessionId, switch (result.failure()) {
                case NO_SUCH_ROOM -> "{\"type\":\"error\",\"message\":\"no such room\"}";
                case ROOM_FULL -> "{\"type\":\"error\",\"message\":\"room is full\"}";
            });
        }
    }

    /** One room's worth of work: input, then simulation, then broadcast. */
    void advance(Room room, long nowMillis) {
        applyRoomCommands(room);

        if (room.phase() == RoomPhase.RUNNING) {
            // Hold the dropped players' snakes still before the engine runs, so
            // they stay put but remain lethal to everyone else.
            room.holdDisconnectedSnakes();
            room.engine().tick(room.state());
            room.finishIfWon();
        }

        room.dropExpiredPlayers(nowMillis);
        broadcast(room);
    }

    private void applyRoomCommands(Room room) {
        for (ClientCommand c : room.drainInbox()) {
            try {
                switch (c.type()) {
                    case TURN -> turn(room, c);
                    case READY -> room.toggleReady(c.sessionId());
                    case START -> room.startRound(c.sessionId());
                    case RENAME -> rooms.rename(c.sessionId(), c.name());
                    case PLAY_AGAIN -> playAgain(room, c.sessionId());
                    default -> {
                        // Lobby-level commands never reach a room queue.
                    }
                }
            } catch (RuntimeException bad) {
                // Drop the command and keep the room ticking.
            }
        }
    }

    private void turn(Room room, ClientCommand c) {
        Snake snake = room.snakeOf(c.sessionId());
        // setDirection already refuses a reversal, so a client cannot fold a
        // snake back onto itself by asking.
        if (snake != null && c.direction() != null) {
            snake.setDirection(c.direction());
        }
    }

    /** Only the host takes the room back to the lobby, as with starting. */
    private void playAgain(Room room, String sessionId) {
        if (room.phase() == RoomPhase.RESULTS && room.isHost(sessionId)) {
            room.returnToLobby();
        }
    }

    private void broadcast(Room room) {
        String payload = sockets.toJson(StateMessage.of(room));
        if (payload == null) {
            return;
        }
        List<String> recipients = room.sessionIds();
        sockets.sendAll(recipients, payload);
    }
}
