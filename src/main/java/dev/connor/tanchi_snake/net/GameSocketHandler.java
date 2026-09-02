package dev.connor.tanchi_snake.net;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

import dev.connor.tanchi_snake.game.Direction;
import dev.connor.tanchi_snake.room.ClientCommand;
import dev.connor.tanchi_snake.room.Room;

/**
 * The socket end of the game.
 *
 * <p>This class never touches game state. It parses what a client sent, throws
 * out anything it does not recognise, and puts a command on a queue for the
 * scheduler thread to apply. Everything a client can send is treated as
 * hostile until it has been checked here.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    /** Longest message accepted, so a client cannot post a novel at us. */
    static final int MAX_MESSAGE_BYTES = 4096;

    /** Buffered bytes allowed per session before the decorator gives up on it. */
    private static final int SEND_BUFFER_BYTES = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final CommandBus bus;
    private final RoomManager rooms;

    public GameSocketHandler(CommandBus bus, RoomManager rooms) {
        this.bus = bus;
        this.rooms = rooms;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // sendMessage is not thread safe, and both the scheduler thread and
        // this one can reach a session. The decorator serialises them.
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_BYTES));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        bus.submit(ClientCommand.disconnect(session.getId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        bus.submit(ClientCommand.disconnect(session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientCommand command = parse(session.getId(), message.getPayload());
        if (command == null) {
            send(session.getId(), errorJson("unrecognised message"));
            return;
        }
        route(command);
    }

    /** Puts a command where the scheduler thread will find it. */
    private void route(ClientCommand command) {
        if (command.isLobbyLevel()) {
            bus.submit(command);
            return;
        }
        Room room = rooms.roomOf(command.sessionId());
        if (room == null) {
            send(command.sessionId(), errorJson("you are not in a room"));
            return;
        }
        room.enqueue(command);
    }

    /**
     * Turns a client payload into a command, or null if it is not something we
     * are willing to act on. Never throws: bad input is a dropped message, not
     * a broken loop.
     */
    ClientCommand parse(String sessionId, String payload) {
        if (payload == null || payload.length() > MAX_MESSAGE_BYTES) {
            return null;
        }

        Map<?, ?> fields;
        try {
            fields = json.readValue(payload, Map.class);
        } catch (Exception malformed) {
            return null;
        }
        if (fields == null) {
            return null;
        }

        String type = text(fields, "type");
        if (type == null) {
            return null;
        }

        return switch (type.trim().toLowerCase()) {
            case "create" -> ClientCommand.create(sessionId, text(fields, "name"));
            case "join" -> joinOrNull(sessionId, fields);
            case "rename" -> ClientCommand.rename(sessionId, text(fields, "name"));
            case "ready" -> ClientCommand.ready(sessionId);
            case "start" -> ClientCommand.start(sessionId);
            case "turn" -> turnOrNull(sessionId, fields);
            case "playagain" -> ClientCommand.playAgain(sessionId);
            default -> null;
        };
    }

    private ClientCommand joinOrNull(String sessionId, Map<?, ?> fields) {
        String code = text(fields, "room");
        // The room may still not exist; that is the loop's problem, not ours.
        // Here we only insist there is something to look up.
        // "you" is echoed back from a previous joined message; absent for a
        // first-time join, which is when a new id gets minted.
        return code == null || code.isBlank()
                ? null
                : ClientCommand.join(sessionId, code, text(fields, "name"), text(fields, "you"));
    }

    private ClientCommand turnOrNull(String sessionId, Map<?, ?> fields) {
        Direction d = direction(text(fields, "dir"));
        return d == null ? null : ClientCommand.turn(sessionId, d);
    }

    /** Maps a client string to a direction, or null for anything else. */
    static Direction direction(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Direction.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Only strings count; a number or object where a name belongs is ignored. */
    private static String text(Map<?, ?> fields, String field) {
        Object value = fields.get(field);
        return value instanceof String s ? s : null;
    }

    // --- outbound ---

    /** Sends to one session, dropping it if the socket has gone. */
    public void send(String sessionId, String payload) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException | IllegalStateException gone) {
            // A dead socket must never take the broadcast down with it.
            sessions.remove(sessionId);
        }
    }

    /** Sends the same payload to a set of sessions, serialised once by the caller. */
    public void sendAll(Collection<String> sessionIds, String payload) {
        for (String id : sessionIds) {
            send(id, payload);
        }
    }

    public String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception unserialisable) {
            return null;
        }
    }

    static String errorJson(String message) {
        return "{\"type\":\"error\",\"message\":\"" + message.replace("\"", "'") + "\"}";
    }

    public boolean isConnected(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    int sessionCount() {
        return sessions.size();
    }

    /** Test seam for registering an already-decorated session. */
    void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }
}
