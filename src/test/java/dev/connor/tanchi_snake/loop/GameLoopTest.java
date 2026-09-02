package dev.connor.tanchi_snake.loop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import dev.connor.tanchi_snake.game.Direction;
import dev.connor.tanchi_snake.game.Snake;
import dev.connor.tanchi_snake.net.CommandBus;
import dev.connor.tanchi_snake.net.GameSocketHandler;
import dev.connor.tanchi_snake.net.RoomManager;
import dev.connor.tanchi_snake.room.ClientCommand;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomPhase;

class GameLoopTest {

    static final class TestClock extends Clock {
        private long millis;

        TestClock(long millis) {
            this.millis = millis;
        }

        void advance(long by) {
            millis += by;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private TestClock clock;
    private RoomManager rooms;
    private CommandBus bus;
    private GameSocketHandler sockets;
    private GameLoop loop;

    @BeforeEach
    void setUp() {
        clock = new TestClock(0);
        rooms = new RoomManager(new Random(13), clock);
        bus = new CommandBus();
        sockets = new GameSocketHandler(bus, rooms);
        loop = new GameLoop(rooms, bus, sockets, clock);
    }

    /**
     * Registers through the real connection path, so the session the loop
     * broadcasts to is wrapped exactly as it would be in production.
     */
    private WebSocketSession attach(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        sockets.afterConnectionEstablished(session);
        return session;
    }

    // --- lobby commands ---

    @Test
    void createMakesARoomAndSeatsTheCaller() {
        attach("s1");
        bus.submit(ClientCommand.create("s1", "Ann"));

        loop.tick();

        assertEquals(1, rooms.rooms().size());
        Room room = rooms.roomOf("s1");
        assertNotNull(room);
        assertTrue(room.isHost("s1"));
        assertEquals("Ann", room.player("s1").name());
    }

    @Test
    void aBlankNameGetsOneAssigned() {
        attach("s1");
        bus.submit(ClientCommand.create("s1", "   "));

        loop.tick();

        assertTrue(rooms.roomOf("s1").player("s1").name().endsWith(" Snake"));
    }

    @Test
    void joiningARoomThatDoesNotExistAnswersWithAnErrorAndKeepsTicking() throws Exception {
        WebSocketSession session = attach("s1");
        bus.submit(ClientCommand.join("s1", "ZZZZ", "Ann"));

        assertDoesNotThrow(loop::tick);

        assertNull(rooms.roomOf("s1"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void aFullRoomTurnsExtraPlayersAway() {
        Room room = rooms.create();
        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            rooms.join(room.code(), "p" + i, "p" + i);
        }
        attach("late");
        bus.submit(ClientCommand.join("late", room.code(), "Late"));

        loop.tick();

        assertNull(rooms.roomOf("late"));
        assertEquals(Room.MAX_PLAYERS, room.size());
    }

    // --- room commands ---

    @Test
    void turnCommandsChangeTheSnakeDirection() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        room.startRound("s1");
        Snake snake = room.snakeOf("s1");
        Direction turnTo = perpendicularTo(snake.direction());

        room.enqueue(ClientCommand.turn("s1", turnTo));
        loop.tick();

        assertEquals(turnTo, snake.direction());
    }

    private static Direction perpendicularTo(Direction d) {
        return switch (d) {
            case UP, DOWN -> Direction.LEFT;
            case LEFT, RIGHT -> Direction.UP;
        };
    }

    @Test
    void aTurnFromSomeoneWithNoSnakeIsIgnored() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");

        room.enqueue(ClientCommand.turn("ghost", Direction.UP));

        assertDoesNotThrow(loop::tick);
    }

    @Test
    void readyAndStartFlowThroughTheQueue() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        rooms.join(room.code(), "s2", "Bo");

        room.enqueue(ClientCommand.ready("s1"));
        room.enqueue(ClientCommand.start("s2"));
        loop.tick();

        assertTrue(room.player("s1").isReady());
        assertEquals(RoomPhase.LOBBY, room.phase(), "s2 is not the host");

        room.enqueue(ClientCommand.start("s1"));
        loop.tick();

        assertEquals(RoomPhase.RUNNING, room.phase());
    }

    @Test
    void onlyTheHostTakesTheRoomBackToTheLobby() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        rooms.join(room.code(), "s2", "Bo");
        room.startRound("s1");
        room.state().setWinner(room.snakeOf("s1"));
        room.finishIfWon();

        room.enqueue(ClientCommand.playAgain("s2"));
        loop.tick();
        assertEquals(RoomPhase.RESULTS, room.phase());

        room.enqueue(ClientCommand.playAgain("s1"));
        loop.tick();
        assertEquals(RoomPhase.LOBBY, room.phase());
    }

    // --- ticking ---

    @Test
    void onlyRunningRoomsAdvance() {
        Room lobby = rooms.create();
        rooms.join(lobby.code(), "s1", "Ann");
        Room running = rooms.create();
        rooms.join(running.code(), "s2", "Bo");
        running.startRound("s2");

        loop.tick();

        assertEquals(0, lobby.state().tick(), "a lobby does not simulate");
        assertEquals(1, running.state().tick());
    }

    @Test
    void roomsAdvanceIndependently() {
        Room a = rooms.create();
        rooms.join(a.code(), "s1", "Ann");
        a.startRound("s1");
        Room b = rooms.create();
        rooms.join(b.code(), "s2", "Bo");

        loop.tick();
        loop.tick();

        assertEquals(2, a.state().tick());
        assertEquals(0, b.state().tick());
    }

    @Test
    void aRoundThatIsWonMovesToResultsAndStopsAdvancing() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        room.startRound("s1");
        room.state().setWinner(room.snakeOf("s1"));

        loop.tick();
        assertEquals(RoomPhase.RESULTS, room.phase());

        int frozen = room.state().tick();
        loop.tick();
        assertEquals(frozen, room.state().tick(), "results do not simulate");
    }

    // --- broadcasting ---

    @Test
    void stateGoesOnlyToTheRoomsOwnSessions() throws Exception {
        Room a = rooms.create();
        rooms.join(a.code(), "s1", "Ann");
        Room b = rooms.create();
        rooms.join(b.code(), "s2", "Bo");
        WebSocketSession inA = attach("s1");
        WebSocketSession inB = attach("s2");
        WebSocketSession outsider = attach("s3");

        loop.tick();

        verify(inA, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(inB, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(outsider, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void theBroadcastIsWellFormedJsonCarryingTheBoard() throws Exception {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        room.startRound("s1");
        WebSocketSession session = attach("s1");

        loop.tick();

        List<String> sent = captureMessages(session);
        assertFalse(sent.isEmpty());
        String payload = sent.get(sent.size() - 1);
        assertTrue(payload.contains("\"type\":\"state\""), payload);
        assertTrue(payload.contains(room.code()), payload);
        assertTrue(payload.contains("\"phase\":\"RUNNING\""), payload);
        assertTrue(payload.contains("\"snakes\""), payload);
        assertTrue(payload.contains("\"food\""), payload);
        assertTrue(payload.contains("Ann"), payload);
    }

    private static List<String> captureMessages(WebSocketSession session) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        List<String> payloads = new ArrayList<>();
        for (TextMessage m : captor.getAllValues()) {
            payloads.add(m.getPayload());
        }
        return payloads;
    }

    @Test
    void standingsRideAlongOnlyOnTheResultsScreen() throws Exception {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        room.startRound("s1");
        WebSocketSession session = attach("s1");

        loop.tick();
        assertTrue(captureMessages(session).get(0).contains("\"standings\":[]"));

        room.state().setWinner(room.snakeOf("s1"));
        loop.tick();

        String last = captureMessages(session).stream().reduce((a, b) -> b).orElseThrow();
        assertTrue(last.contains("\"rank\":1"), last);
        assertTrue(last.contains("\"podium\":true"), last);
    }

    // --- disconnects and sweeping ---

    @Test
    void aDisconnectFreezesTheSnakeAndLaterRemovesIt() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        rooms.join(room.code(), "s2", "Bo");
        room.startRound("s1");

        bus.submit(ClientCommand.disconnect("s2"));
        loop.tick();

        Snake frozen = room.snakeOf("s2");
        assertNotNull(frozen, "still on the board during the window");
        assertTrue(frozen.stunTicks() > 0, "and frozen, so it stays lethal");

        clock.advance(Room.DISCONNECT_GRACE_MILLIS);
        loop.tick();

        assertNull(room.snakeOf("s2"), "gone once the window lapses");
        assertNull(room.player("s2"));
    }

    @Test
    void emptyRoomsAreSweptByTheLoop() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        bus.submit(ClientCommand.disconnect("s1"));
        loop.tick();
        assertNotNull(rooms.find(room.code()));

        clock.advance(Room.EMPTY_TTL_MILLIS);
        loop.tick();

        assertNull(rooms.find(room.code()), "room torn down");
    }

    @Test
    void theLoopSurvivesARoomWithNoSessionsAttached() {
        Room room = rooms.create();
        rooms.join(room.code(), "s1", "Ann");
        room.startRound("s1");

        assertDoesNotThrow(loop::tick);
        assertEquals(1, room.state().tick());
    }

    @Test
    void aFailingSocketDoesNotStopOtherRoomsFromTicking() throws Exception {
        Room bad = rooms.create();
        rooms.join(bad.code(), "s1", "Ann");
        bad.startRound("s1");
        WebSocketSession broken = attach("s1");
        doThrow(new java.io.IOException("gone")).when(broken).sendMessage(any(TextMessage.class));

        Room good = rooms.create();
        rooms.join(good.code(), "s2", "Bo");
        good.startRound("s2");

        assertDoesNotThrow(loop::tick);
        assertEquals(1, good.state().tick());
        assertEquals(1, bad.state().tick());
    }

    @Test
    void tickWithNothingHappeningIsHarmless() {
        assertDoesNotThrow(loop::tick);
        assertEquals(0, rooms.rooms().size());
    }
}
