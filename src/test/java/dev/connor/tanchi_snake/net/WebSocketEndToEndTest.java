package dev.connor.tanchi_snake.net;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
        String joined = client.await(p -> p.contains("\"type\":\"joined\""));
        assertNotNull(joined, "no joined message came back");
        return roomCodeOf(joined);
    }

    private static String roomCodeOf(String joinedPayload) {
        try {
            return (String) JSON.readValue(joinedPayload, java.util.Map.class).get("room");
        } catch (Exception e) {
            return null;
        }
    }

    private static SnakeMatch snakeNamed(StateMessage state, String name) {
        for (StateMessage.SnakeView s : state.snakes()) {
            if (name.equals(s.name())) {
                return new SnakeMatch(s);
            }
        }
        return null;
    }

    private record SnakeMatch(StateMessage.SnakeView snake) {
    }

    // --- 1. connect and create ---

    @Test
    void creatingARoomReturnsAValidFourCharacterCode() throws Exception {
        try (Client host = connect()) {
            String code = createRoom(host, "Ann");

            assertNotNull(code, "joined message carried no room code");
            assertEquals(RoomCodeGenerator.CODE_LENGTH, code.length());
            assertTrue(RoomCodeGenerator.isWellFormed(code), "bad code: " + code);
            assertNull(host.transportFailure());
        }
    }

    // --- 2. a second client joins ---

    @Test
    void bothClientsSeeTheLobbyAfterTheSecondJoins() throws Exception {
        try (Client host = connect(); Client guest = connect()) {
            String code = createRoom(host, "Ann");

            guest.send("{\"type\":\"join\",\"room\":\"" + code + "\",\"name\":\"Bo\"}");
            assertNotNull(guest.await(p -> p.contains("\"type\":\"joined\"")));

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
            }
        }
    }

    // --- 3. the host starts the round ---

    @Test
    void startingTheRoundPutsBothClientsOnTheTick() throws Exception {
        try (Client host = connect(); Client guest = connect()) {
            String code = createRoom(host, "Ann");
            guest.send("{\"type\":\"join\",\"room\":\"" + code + "\",\"name\":\"Bo\"}");
            assertNotNull(host.awaitState(s -> s.players().size() == 2));

            host.send("{\"type\":\"start\"}");

            StateMessage running = host.awaitState(s -> "RUNNING".equals(s.phase()));
            assertNotNull(running, "round never started");
            assertNotNull(guest.awaitState(s -> "RUNNING".equals(s.phase())));

            assertEquals(2, running.snakes().size(), "both players are on the board");

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
                    s -> "RUNNING".equals(s.phase()) && snakeNamed(s, "Solo") != null);
            assertNotNull(running);

            Direction facing = Direction.valueOf(snakeNamed(running, "Solo").snake().direction());
            Direction turnTo = perpendicularTo(facing);

            host.send("{\"type\":\"turn\",\"dir\":\"" + turnTo.name() + "\"}");

            StateMessage turned = host.awaitState(s -> {
                SnakeMatch mine = snakeNamed(s, "Solo");
                return mine != null && turnTo.name().equals(mine.snake().direction());
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
            guest.send("{\"type\":\"join\",\"room\":\"" + code + "\",\"name\":\"Bo\"}");
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
            return JSON.readValue(frame, java.util.Map.class).containsKey("type");
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
            guest.send("{\"type\":\"join\",\"room\":\"" + code + "\",\"name\":\"Bo\"}");
            assertNotNull(host.awaitState(s -> s.players().size() == 2));
            host.send("{\"type\":\"start\"}");

            StateMessage state = host.awaitState(
                    s -> "RUNNING".equals(s.phase()) && s.snakes().size() == 2 && !s.food().isEmpty());
            assertNotNull(state, "never saw a running board with food on it");

            assertEquals("state", state.type());
            assertEquals(code, state.room());
            assertEquals("RUNNING", state.phase());
            assertTrue(state.tick() >= 0);
            assertEquals(32, state.width());
            assertEquals(32, state.height());
            assertNotNull(state.hostSessionId());
            assertNull(state.winnerSessionId(), "nobody has won yet");
            assertTrue(state.standings().isEmpty(), "standings are only for the results screen");

            // Food
            assertEquals(5, state.food().size(), "the board is kept stocked");
            for (StateMessage.PointView f : state.food()) {
                assertTrue(f.x() >= 0 && f.x() < state.width(), "food off board: " + f);
                assertTrue(f.y() >= 0 && f.y() < state.height(), "food off board: " + f);
            }

            // Players
            assertEquals(2, state.players().size());
            StateMessage.PlayerView ann = state.players().stream()
                    .filter(p -> "Ann".equals(p.name())).findFirst().orElseThrow();
            assertTrue(ann.host(), "Ann created the room");
            assertTrue(ann.connected());
            assertFalse(ann.ready());
            assertNotNull(ann.sessionId());
            assertEquals(state.hostSessionId(), ann.sessionId());

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
                    .map(StateMessage.PlayerView::sessionId).toList();
            for (StateMessage.SnakeView snake : state.snakes()) {
                assertTrue(seated.contains(snake.id()), "orphan snake " + snake.id());
            }
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
                    s -> "RUNNING".equals(s.phase()) && snakeNamed(s, "Turner") != null);
            assertNotNull(running);
            String facing = snakeNamed(running, "Turner").snake().direction();

            host.send("{\"type\":\"turn\",\"dir\":\"SIDEWAYS\"}");

            String error = host.await(p -> p.contains("\"type\":\"error\""));
            assertNotNull(error, "a bogus direction should be refused");

            StateMessage after = host.awaitState(s -> s.tick() > running.tick() + 2);
            assertNotNull(after, "the server stopped ticking");
            assertEquals(facing, snakeNamed(after, "Turner").snake().direction(),
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
