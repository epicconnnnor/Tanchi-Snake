package dev.connor.tanchi_snake.net;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import dev.connor.tanchi_snake.game.Direction;
import dev.connor.tanchi_snake.game.GameEngine;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomCodeGenerator;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the real server over a real socket: embedded Tomcat on a random port,
 * a genuine StandardWebSocketClient, and no mocks anywhere.
 *
 * <p>This is the only test that exercises the wiring the unit tests cannot
 * reach: the /ws registration, Jackson 3 over the wire, and the session
 * decorator serialising sends that genuinely race.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketEndToEndTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    /** A real client that keeps every frame the server sent, in order. */
    private static final class Client extends TextWebSocketHandler implements AutoCloseable {

        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final List<String> everything = new ArrayList<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private WebSocketSession session;
        /** The stable id the server gave us; keys our snake on the board. */
        private String playerId;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();
            synchronized (everything) {
                everything.add(payload);
            }
            inbox.add(payload);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            failure.set(exception);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        /** Waits for the first frame matching the predicate, discarding earlier ones. */
        String await(Predicate<String> match) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                long left = deadline - System.nanoTime();
                String payload = inbox.poll(left, TimeUnit.NANOSECONDS);
                if (payload == null) {
                    break;
                }
                if (match.test(payload)) {
                    return payload;
                }
            }
            return null;
        }

        StateMessage awaitState(Predicate<StateMessage> match) throws InterruptedException {
            AtomicReference<StateMessage> found = new AtomicReference<>();
            await(payload -> {
                StateMessage state = asState(payload);
                if (state != null && match.test(state)) {
                    found.set(state);
                    return true;
                }
                return false;
            });
            return found.get();
        }

        List<String> received() {
            synchronized (everything) {
                return new ArrayList<>(everything);
            }
        }

        Throwable transportFailure() {
            return failure.get();
        }

        @Override
        public void close() throws Exception {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    private static StateMessage asState(String payload) {
        try {
            StateMessage state = JSON.readValue(payload, StateMessage.class);
            return "state".equals(state.type()) ? state : null;
        } catch (Exception notAState) {
            return null;
        }
    }

    private Client connect() throws Exception {
        Client client = new Client();
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + "/ws")
                .get(WAIT.toSeconds(), TimeUnit.SECONDS);
        return client;
    }

    /** Connects, creates a room, and returns the code the server assigned. */
    private String createRoom(Client client, String name) throws Exception {
        client.send("{\"type\":\"create\",\"name\":\"" + name + "\"}");
        return codeFromJoined(client);
    }

    /** Joins an existing room as a newcomer. */
    private String joinRoom(Client client, String code, String name) throws Exception {
        client.send("{\"type\":\"join\",\"room\":\"" + code + "\",\"name\":\"" + name + "\"}");
        return codeFromJoined(client);
    }

    /** Rejoins claiming a previously issued id, the way a returning client would. */
    private String rejoinRoom(Client client, String code, String playerId) throws Exception {
        client.send("{\"type\":\"join\",\"room\":\"" + code
                + "\",\"you\":\"" + playerId + "\"}");
        return codeFromJoined(client);
    }

    /** Reads the joined message, stashing the id the server handed out. */
    private static String codeFromJoined(Client client) throws Exception {
        String joined = client.await(p -> p.contains("\"type\":\"joined\""));
        assertNotNull(joined, "no joined message came back");
        Map<?, ?> fields = JSON.readValue(joined, Map.class);
        Object you = fields.get("you");
        assertInstanceOf(String.class, you, "joined carried no player id: " + joined);
        client.playerId = (String) you;
        return (String) fields.get("room");
    }

    /** Our own snake, found by id rather than by a name that need not be unique. */
    private static StateMessage.SnakeView mySnake(StateMessage state, Client client) {
        for (StateMessage.SnakeView s : state.snakes()) {
            if (s.id().equals(client.playerId)) {
                return s;
            }
        }
        return null;
    }

    // --- 1. connect and create ---

    @Test
    void creatingARoomReturnsAValidFourCharacterCode() throws Exception {
        try (Client host = connect()) {
            String code = createRoom(host, "Ann");

            assertNotNull(code, "joined message carried no room code");
            assertEquals(RoomCodeGenerator.CODE_LENGTH, code.length());
            assertTrue(RoomCodeGenerator.isWellFormed(code), "bad code: " + code);
            assertNotNull(host.playerId, "joined message carried no player id");
            assertFalse(host.playerId.isBlank());
            assertNull(host.transportFailure());
        }
    }

    // --- 2. a second client joins ---

    @Test
    void bothClientsSeeTheLobbyAfterTheSecondJoins() throws Exception {
        try (Client host = connect(); Client guest = connect()) {
            String code = createRoom(host, "Ann");

            joinRoom(guest, code, "Bo");
            assertNotEquals(host.playerId, guest.playerId, "ids must be per player");

            StateMessage hostView = host.awaitState(s -> s.players().size() == 2);
            StateMessage guestView = guest.awaitState(s -> s.players().size() == 2);

            assertNotNull(hostView, "host never saw the second player");
            assertNotNull(guestView, "guest never saw the lobby");

            for (StateMessage state : List.of(hostView, guestView)) {
                assertEquals(code, state.room());
                assertEquals("LOBBY", state.phase());
                List<String> names = state.players().stream().map(StateMessage.PlayerView::name).toList();
                assertTrue(names.containsAll(List.of("Ann", "Bo")), names.toString());
                assertEquals(1, state.players().stream().filter(StateMessage.PlayerView::host).count());
                List<String> ids = state.players().stream()
                        .map(StateMessage.PlayerView::playerId).toList();
                assertTrue(ids.containsAll(List.of(host.playerId, guest.playerId)), ids.toString());
            }
        }
    }

    // --- 3. the host starts the round ---

    @Test
    void startingTheRoundPutsBothClientsOnTheTick() throws Exception {
        try (Client host = connect(); Client guest = connect()) {
            String code = createRoom(host, "Ann");
            joinRoom(guest, code, "Bo");
            assertNotNull(host.awaitState(s -> s.players().size() == 2));

            host.send("{\"type\":\"start\"}");

            StateMessage running = host.awaitState(s -> "RUNNING".equals(s.phase()));
            assertNotNull(running, "round never started");
            assertNotNull(guest.awaitState(s -> "RUNNING".equals(s.phase())));

            assertEquals(2, running.snakes().size(), "both players are on the board");
            assertNotNull(mySnake(running, host), "host cannot find its own snake");
            StateMessage guestRunning = guest.awaitState(s -> s.snakes().size() == 2);
            assertNotNull(mySnake(guestRunning, guest), "guest cannot find its own snake");

            // The clock is genuinely advancing, not a single snapshot.
            int first = running.tick();
            StateMessage later = host.awaitState(s -> s.tick() > first + 2);
            assertNotNull(later, "ticks did not advance");
        }
    }

    // --- 4. turning ---

    @Test
    void aTurnShowsUpInTheNextBroadcast() throws Exception {
        try (Client host = connect()) {
            createRoom(host, "Solo");
            host.send("{\"type\":\"start\"}");

            StateMessage running = host.awaitState(
                    s -> "RUNNING".equals(s.phase()) && mySnake(s, host) != null);
            assertNotNull(running);

            Direction facing = Direction.valueOf(mySnake(running, host).direction());
            Direction turnTo = perpendicularTo(facing);

            host.send("{\"type\":\"turn\",\"dir\":\"" + turnTo.name() + "\"}");

            StateMessage turned = host.awaitState(s -> {
                StateMessage.SnakeView mine = mySnake(s, host);
                return mine != null && turnTo.name().equals(mine.direction());
            });
            assertNotNull(turned, "the snake never turned to " + turnTo);
        }
    }

    private static Direction perpendicularTo(Direction d) {
        return switch (d) {
            case UP, DOWN -> Direction.LEFT;
            case LEFT, RIGHT -> Direction.UP;
        };
    }

    // --- 5. concurrent input ---

    @Test
    void concurrentInputFromTwoClientsNeverCorruptsAFrame() throws Exception {
        try (Client host = connect(); Client guest = connect()) {
            String code = createRoom(host, "Ann");
            joinRoom(guest, code, "Bo");
            assertNotNull(host.awaitState(s -> s.players().size() == 2));
            host.send("{\"type\":\"start\"}");
            assertNotNull(host.awaitState(s -> "RUNNING".equals(s.phase())));

            // Both clients hammer input while the loop broadcasts to the same
            // sessions at 10Hz. The junk messages matter: each one makes the
            // socket thread send an error on a session the scheduler thread is
            // already writing to, which is exactly what the decorator guards.
            int rounds = 60;
            CountDownLatch go = new CountDownLatch(1);
            List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            Runnable spam = () -> {
                try {
                    go.await();
                    for (int i = 0; i < rounds; i++) {
                        host.send("{\"type\":\"turn\",\"dir\":\"UP\"}");
                        host.send("this is not json");
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            };
            Runnable spamGuest = () -> {
                try {
                    go.await();
                    for (int i = 0; i < rounds; i++) {
                        guest.send("{\"type\":\"turn\",\"dir\":\"LEFT\"}");
                        guest.send("{\"type\":\"nonsense\"}");
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            };

            Thread one = new Thread(spam, "spam-host");
            Thread two = new Thread(spamGuest, "spam-guest");
            one.start();
            two.start();
            go.countDown();
            one.join(WAIT.toMillis());
            two.join(WAIT.toMillis());

            assertTrue(errors.isEmpty(), "sending threw: " + errors);

            // Let the loop keep broadcasting through the storm.
            assertNotNull(host.awaitState(s -> s.tick() > 0));
            assertNotNull(guest.awaitState(s -> s.tick() > 0));
            Thread.sleep(500);

            assertNull(host.transportFailure(), "host transport broke");
            assertNull(guest.transportFailure(), "guest transport broke");

            // Every frame must be a whole, parseable message. A frame torn by
            // two threads writing at once would fail here.
            for (Client client : List.of(host, guest)) {
                List<String> frames = client.received();
                assertFalse(frames.isEmpty());
                for (String frame : frames) {
                    assertTrue(isWholeJsonObject(frame),
                            "corrupted or interleaved frame: " + frame);
                }
            }

            // And no broadcast went missing: the ticks a client saw run
            // consecutively, with nothing dropped in between.
            assertNoGaps(host.received());
            assertNoGaps(guest.received());
        }
    }

    private static boolean isWholeJsonObject(String frame) {
        try {
            return JSON.readValue(frame, Map.class).containsKey("type");
        } catch (Exception torn) {
            return false;
        }
    }

    /**
     * Consecutive running frames must carry consecutive ticks: the loop ticks
     * the engine once and broadcasts once per pass, so a gap means a frame went
     * missing. Only RUNNING frames count, since a lobby broadcasts repeatedly
     * without advancing the clock.
     */
    private static void assertNoGaps(List<String> frames) {
        int previous = -1;
        int counted = 0;
        for (String frame : frames) {
            StateMessage state = asState(frame);
            if (state == null || !"RUNNING".equals(state.phase())) {
                continue;
            }
            if (previous >= 0) {
                assertEquals(previous + 1, state.tick(),
                        "dropped a broadcast between tick " + previous + " and " + state.tick());
            }
            previous = state.tick();
            counted++;
        }
        assertTrue(counted > 5, "expected a run of broadcasts, saw " + counted);
    }

    // --- 6. full round trip through Jackson 3 ---

    @Test
    void theStateMessageSurvivesTheWireFieldByField() throws Exception {
        try (Client host = connect(); Client guest = connect()) {
            String code = createRoom(host, "Ann");
            joinRoom(guest, code, "Bo");
            assertNotNull(host.awaitState(s -> s.players().size() == 2));
            host.send("{\"type\":\"start\"}");

            StateMessage state = host.awaitState(
                    s -> "RUNNING".equals(s.phase()) && s.snakes().size() == 2 && !s.food().isEmpty());
            assertNotNull(state, "never saw a running board with food on it");

            assertEquals("state", state.type());
            assertEquals(code, state.room());
            assertEquals("RUNNING", state.phase());
            assertTrue(state.tick() >= 0);
            assertEquals(Room.BOARD_WIDTH, state.width());
            assertEquals(Room.BOARD_HEIGHT, state.height());
            assertNotNull(state.hostPlayerId());
            assertNull(state.winnerPlayerId(), "nobody has won yet");
            assertTrue(state.standings().isEmpty(), "standings are only for the results screen");

            // Food
            assertEquals(GameEngine.FOOD_ON_BOARD, state.food().size(), "the board is kept stocked");
            for (StateMessage.FoodView f : state.food()) {
                assertTrue(f.x() >= 0 && f.x() < state.width(), "food off board: " + f);
                assertTrue(f.y() >= 0 && f.y() < state.height(), "food off board: " + f);
                assertTrue(f.value() >= 1 && f.value() <= GameEngine.MAX_FOOD_VALUE,
                        "food worth " + f.value() + " is off the scale");
            }

            // Players
            assertEquals(2, state.players().size());
            StateMessage.PlayerView ann = state.players().stream()
                    .filter(p -> host.playerId.equals(p.playerId())).findFirst().orElseThrow();
            assertEquals("Ann", ann.name());
            assertTrue(ann.host(), "Ann created the room");
            assertTrue(ann.connected());
            assertFalse(ann.ready());
            assertEquals(state.hostPlayerId(), ann.playerId());

            // Snakes
            for (StateMessage.SnakeView snake : state.snakes()) {
                assertNotNull(snake.id());
                assertTrue(List.of("Ann", "Bo").contains(snake.name()), snake.name());
                assertEquals(1, snake.level(), "everyone starts at level 1");
                assertTrue(snake.length() >= 1);
                assertEquals(snake.length(), snake.body().size());
                assertFalse(snake.stunned(), "no protection on spawn");
                assertDoesNotThrow(() -> Direction.valueOf(snake.direction()));
                for (StateMessage.PointView p : snake.body()) {
                    assertTrue(p.x() >= 0 && p.x() < state.width(), "body off board: " + p);
                    assertTrue(p.y() >= 0 && p.y() < state.height(), "body off board: " + p);
                }
            }
            // Every snake belongs to a seated player.
            List<String> seated = state.players().stream()
                    .map(StateMessage.PlayerView::playerId).toList();
            for (StateMessage.SnakeView snake : state.snakes()) {
                assertTrue(seated.contains(snake.id()), "orphan snake " + snake.id());
            }
            // And the id from the joined handshake picks out exactly one snake.
            assertEquals(1, state.snakes().stream()
                    .filter(sv -> sv.id().equals(host.playerId)).count());
        }
    }

    // --- reconnecting on a brand new socket ---

    @Test
    void aReturningClientReclaimsItsSeatAndItsSnake() throws Exception {
        Client host = connect();
        String code;
        String originalId;
        StateMessage before;
        try {
            code = createRoom(host, "Ann");
            originalId = host.playerId;
            host.send("{\"type\":\"start\"}");
            before = host.awaitState(s -> "RUNNING".equals(s.phase()) && mySnake(s, host) != null);
            assertNotNull(before);
        } finally {
            host.close();
        }

        // A new socket means a new session id, so the seat can only be found
        // by the id the client was given.
        try (Client returning = connect()) {
            assertEquals(code, rejoinRoom(returning, code, originalId));

            assertEquals(originalId, returning.playerId, "same seat, same id");
            StateMessage after = returning.awaitState(s -> mySnake(s, returning) != null);
            assertNotNull(after, "the returning client could not find its snake");
            assertEquals(1, after.players().size(), "no second seat was handed out");
            assertEquals("Ann", after.players().get(0).name(), "the old name came back");
        }
    }

    @Test
    void claimingAnUnknownIdSeatsYouAsANewcomer() throws Exception {
        try (Client host = connect(); Client stranger = connect()) {
            String code = createRoom(host, "Ann");

            assertEquals(code, rejoinRoom(stranger, code, "not-a-real-player-id"));

            assertNotNull(stranger.playerId);
            assertNotEquals("not-a-real-player-id", stranger.playerId, "bogus ids are not honoured");
            assertNotEquals(host.playerId, stranger.playerId);
            assertNotNull(host.awaitState(s -> s.players().size() == 2));
        }
    }

    // --- 7. bad input ---

    @Test
    void anUnknownRoomCodeIsAnsweredWithAnError() throws Exception {
        try (Client client = connect()) {
            client.send("{\"type\":\"join\",\"room\":\"ZZZZ\",\"name\":\"Ann\"}");

            String error = client.await(p -> p.contains("\"type\":\"error\""));

            assertNotNull(error, "no error came back");
            assertTrue(error.contains("no such room"), error);
            assertNull(client.transportFailure());
        }
    }

    @Test
    void malformedJsonIsAnsweredWithAnError() throws Exception {
        try (Client client = connect()) {
            client.send("{\"type\": ");

            String error = client.await(p -> p.contains("\"type\":\"error\""));

            assertNotNull(error, "no error came back");
            assertNull(client.transportFailure(), "the socket stayed up");
        }
    }

    @Test
    void anInvalidDirectionIsRefusedWithoutMovingTheSnake() throws Exception {
        try (Client host = connect()) {
            createRoom(host, "Turner");
            host.send("{\"type\":\"start\"}");
            StateMessage running = host.awaitState(
                    s -> "RUNNING".equals(s.phase()) && mySnake(s, host) != null);
            assertNotNull(running);
            String facing = mySnake(running, host).direction();

            host.send("{\"type\":\"turn\",\"dir\":\"SIDEWAYS\"}");

            String error = host.await(p -> p.contains("\"type\":\"error\""));
            assertNotNull(error, "a bogus direction should be refused");

            StateMessage after = host.awaitState(s -> s.tick() > running.tick() + 2);
            assertNotNull(after, "the server stopped ticking");
            assertEquals(facing, mySnake(after, host).direction(),
                    "the snake changed course on a direction the server rejected");
        }
    }

    @Test
    void theServerKeepsTickingThroughAStreamOfJunk() throws Exception {
        try (Client host = connect()) {
            createRoom(host, "Sturdy");
            host.send("{\"type\":\"start\"}");
            StateMessage running = host.awaitState(s -> "RUNNING".equals(s.phase()));
            assertNotNull(running);

            for (int i = 0; i < 40; i++) {
                host.send("not json at all");
                host.send("{\"type\":\"selfDestruct\"}");
                host.send("{\"type\":\"turn\",\"dir\":42}");
                host.send("{\"type\":\"join\",\"room\":\"!!!!\"}");
                host.send("[]");
            }

            StateMessage after = host.awaitState(s -> s.tick() > running.tick() + 5);
            assertNotNull(after, "the loop stopped after bad input");
            assertEquals("RUNNING", after.phase());
            assertNull(host.transportFailure());
        }
    }
}
