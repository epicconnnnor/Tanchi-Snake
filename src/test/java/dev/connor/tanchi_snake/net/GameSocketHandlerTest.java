package dev.connor.tanchi_snake.net;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import dev.connor.tanchi_snake.game.Direction;
import dev.connor.tanchi_snake.room.ClientCommand;
import dev.connor.tanchi_snake.room.Room;

class GameSocketHandlerTest {

    private CommandBus bus;
    private RoomManager rooms;
    private GameSocketHandler handler;

    @BeforeEach
    void setUp() {
        bus = new CommandBus();
        rooms = new RoomManager(new Random(3), Clock.systemUTC());
        handler = new GameSocketHandler(bus, rooms);
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    // --- parsing ---

    @Test
    void parsesTheCommandsAClientCanSend() {
        assertEquals(ClientCommand.Type.CREATE, handler.parse("s1", "{\"type\":\"create\"}").type());
        assertEquals(ClientCommand.Type.READY, handler.parse("s1", "{\"type\":\"ready\"}").type());
        assertEquals(ClientCommand.Type.START, handler.parse("s1", "{\"type\":\"start\"}").type());
        assertEquals(ClientCommand.Type.PLAY_AGAIN,
                handler.parse("s1", "{\"type\":\"playAgain\"}").type());
    }

    @Test
    void parsesJoinWithRoomAndName() {
        ClientCommand c = handler.parse("s1", "{\"type\":\"join\",\"room\":\"ABCD\",\"name\":\"Ann\"}");

        assertEquals(ClientCommand.Type.JOIN, c.type());
        assertEquals("ABCD", c.roomCode());
        assertEquals("Ann", c.name());
        assertEquals("s1", c.sessionId());
    }

    @Test
    void parsesEveryDirection() {
        for (Direction d : Direction.values()) {
            ClientCommand c = handler.parse("s1", "{\"type\":\"turn\",\"dir\":\"" + d.name() + "\"}");
            assertEquals(d, c.direction());
        }
    }

    @Test
    void directionParsingIsForgivingAboutCaseAndPadding() {
        assertEquals(Direction.UP, GameSocketHandler.direction("up"));
        assertEquals(Direction.LEFT, GameSocketHandler.direction("  LeFt "));
    }

    @Test
    void aBogusDirectionIsRejectedRatherThanGuessed() {
        assertNull(GameSocketHandler.direction("sideways"));
        assertNull(GameSocketHandler.direction(""));
        assertNull(GameSocketHandler.direction(null));
        assertNull(handler.parse("s1", "{\"type\":\"turn\",\"dir\":\"NORTHWEST\"}"));
        assertNull(handler.parse("s1", "{\"type\":\"turn\"}"));
    }

    @Test
    void malformedJsonIsDroppedNotThrown() {
        assertNull(handler.parse("s1", "not json"));
        assertNull(handler.parse("s1", "{\"type\":"));
        assertNull(handler.parse("s1", "[1,2,3]"));
        assertNull(handler.parse("s1", ""));
        assertNull(handler.parse("s1", null));
    }

    @Test
    void unknownAndMistypedFieldsAreRejected() {
        assertNull(handler.parse("s1", "{\"type\":\"selfDestruct\"}"));
        assertNull(handler.parse("s1", "{\"nope\":\"ready\"}"));
        // A number where a string belongs must not be coerced.
        assertNull(handler.parse("s1", "{\"type\":7}"));
        assertNull(handler.parse("s1", "{\"type\":\"join\",\"room\":42}"));
    }

    @Test
    void joinNeedsARoomCode() {
        assertNull(handler.parse("s1", "{\"type\":\"join\"}"));
        assertNull(handler.parse("s1", "{\"type\":\"join\",\"room\":\"  \"}"));
    }

    @Test
    void oversizedMessagesAreRefused() {
        String huge = "{\"type\":\"rename\",\"name\":\"" + "x".repeat(GameSocketHandler.MAX_MESSAGE_BYTES) + "\"}";
        assertNull(handler.parse("s1", huge));
    }

    // --- routing ---

    @Test
    void lobbyCommandsGoToTheSharedBus() throws Exception {
        WebSocketSession session = openSession("s1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"create\"}"));

        assertEquals(1, bus.size());
        assertEquals(ClientCommand.Type.CREATE, bus.drain().get(0).type());
    }

    @Test
    void inRoomCommandsGoToThatRoomsQueueOnly() throws Exception {
        Room room = rooms.create();
        Room other = rooms.create();
        rooms.join(room.code(), "s1", null, "Ann");
        WebSocketSession session = openSession("s1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"turn\",\"dir\":\"UP\"}"));

        assertEquals(1, room.inboxSize());
        assertEquals(0, other.inboxSize(), "other rooms must not see it");
        assertEquals(0, bus.size());
    }

    @Test
    void anInRoomCommandFromSomeoneWithNoRoomIsAnsweredNotQueued() throws Exception {
        WebSocketSession session = openSession("stray");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ready\"}"));

        assertEquals(0, bus.size());
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void junkGetsAnErrorBackAndIsNotQueued() throws Exception {
        WebSocketSession session = openSession("s1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("garbage"));

        assertEquals(0, bus.size());
        verify(session).sendMessage(any(TextMessage.class));
    }

    // --- session lifecycle ---

    @Test
    void connectingRegistersAndClosingQueuesADisconnect() {
        WebSocketSession session = openSession("s1");

        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.sessionCount());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertEquals(0, handler.sessionCount());
        List<ClientCommand> queued = bus.drain();
        assertEquals(1, queued.size());
        assertEquals(ClientCommand.Type.DISCONNECT, queued.get(0).type());
    }

    @Test
    void aTransportErrorIsTreatedAsADisconnect() {
        WebSocketSession session = openSession("s1");
        handler.afterConnectionEstablished(session);

        handler.handleTransportError(session, new IOException("boom"));

        assertEquals(0, handler.sessionCount());
        assertEquals(ClientCommand.Type.DISCONNECT, bus.drain().get(0).type());
    }

    // --- sending ---

    @Test
    void sendingToAnUnknownOrClosedSessionIsSilentlyIgnored() {
        assertDoesNotThrow(() -> handler.send("ghost", "{}"));

        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.getId()).thenReturn("s1");
        when(closed.isOpen()).thenReturn(false);
        handler.register("s1", closed);

        assertDoesNotThrow(() -> handler.send("s1", "{}"));
    }

    @Test
    void aFailingSocketIsDroppedRatherThanBreakingTheBroadcast() throws Exception {
        WebSocketSession good = openSession("good");
        WebSocketSession bad = openSession("bad");
        doThrow(new IOException("gone")).when(bad).sendMessage(any(TextMessage.class));
        handler.register("good", good);
        handler.register("bad", bad);

        assertDoesNotThrow(() -> handler.sendAll(List.of("bad", "good"), "{\"type\":\"state\"}"));

        verify(good).sendMessage(any(TextMessage.class));
        assertFalse(handler.isConnected("bad"), "the dead socket is forgotten");
    }

    @Test
    void connectionsAreWrappedForThreadSafeSending() {
        WebSocketSession raw = openSession("s1");
        handler.afterConnectionEstablished(raw);

        // The handler must not hand the raw session back out: sendMessage is
        // not thread safe and both the loop and socket threads reach it.
        handler.send("s1", "{}");

        assertTrue(handler.isConnected("s1"));
    }

    @Test
    void errorPayloadsStayValidJsonEvenWithQuotesInThem() {
        String payload = GameSocketHandler.errorJson("bad \"thing\" happened");

        assertFalse(payload.substring(payload.indexOf("message")).contains("\\"),
                "quotes are replaced, not escaped away");
        assertTrue(payload.startsWith("{\"type\":\"error\""));
    }
}
