package dev.connor.tanchi_snake.net;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.connor.tanchi_snake.room.Player;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomCodeGenerator;

class RoomManagerTest {

    /** A clock the test drives by hand, so the timers need no real waiting. */
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

    private static RoomManager manager(TestClock clock) {
        return new RoomManager(new Random(5), clock);
    }

    /** The stable id minted for whoever is on this socket. */
    private static String pid(Room room, String sessionId) {
        Player p = room.playerBySession(sessionId);
        return p == null ? null : p.playerId();
    }

    // --- creation ---

    @Test
    void createdRoomsGetWellFormedDistinctCodes() {
        RoomManager rm = manager(new TestClock(0));
        Room a = rm.create();
        Room b = rm.create();

        assertTrue(RoomCodeGenerator.isWellFormed(a.code()));
        assertTrue(RoomCodeGenerator.isWellFormed(b.code()));
        assertNotEquals(a.code(), b.code());
        assertSame(a, rm.find(a.code()));
    }

    @Test
    void eachRoomGetsItsOwnBoard() {
        RoomManager rm = manager(new TestClock(0));
        assertNotSame(rm.create().state(), rm.create().state());
    }

    @Test
    void findToleratesLowercaseWhitespaceAndNonsense() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();

        assertSame(room, rm.find(room.code().toLowerCase()));
        assertSame(room, rm.find("  " + room.code() + " "));
        assertNull(rm.find("ZZZZ"));
        assertNull(rm.find(null));
        assertNull(rm.find(""));
    }

    // --- join and leave ---

    @Test
    void joiningAnUnknownRoomFailsWithoutThrowing() {
        RoomManager rm = manager(new TestClock(0));
        JoinResult result = rm.join("ZZZZ", "s1", null, "Ann");

        assertFalse(result.ok());
        assertEquals(JoinResult.Failure.NO_SUCH_ROOM, result.failure());
        assertNull(result.room());
    }

    @Test
    void firstPlayerThroughTheDoorIsHost() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();

        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");

        assertTrue(room.isHost(pid(room, "s1")));
        assertFalse(room.isHost(pid(room, "s2")));
        assertEquals(2, room.size());
    }

    @Test
    void roomsFillUpAtEightPlayers() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();

        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            assertTrue(rm.join(room.code(), "s" + i, null, "p" + i).ok());
        }
        assertTrue(room.isFull());

        JoinResult overflow = rm.join(room.code(), "extra", null, "late");
        assertFalse(overflow.ok());
        assertEquals(JoinResult.Failure.ROOM_FULL, overflow.failure());
        assertEquals(Room.MAX_PLAYERS, room.size());
    }

    @Test
    void leavingReleasesTheSeat() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");

        rm.leave("s1");

        assertEquals(1, room.size());
        assertNull(room.playerBySession("s1"));
        assertNull(rm.roomOf("s1"));
    }

    @Test
    void disconnectKeepsTheSeatAndReconnectResumesIt() {
        RoomManager rm = manager(new TestClock(1_000));
        Room room = rm.create();
        String was = rm.join(room.code(), "s1", null, "Ann").player().playerId();

        rm.disconnect("s1");
        assertEquals(1, room.size(), "seat is held for a return");
        assertFalse(room.player(was).isConnected());
        assertEquals(1_000, room.player(was).disconnectedAtMillis());

        JoinResult back = rm.join(room.code(), "s1-again", room.player(was).reconnectToken(), "Ann");
        assertTrue(back.ok());
        assertTrue(back.rejoined());
        assertTrue(room.player(was).isConnected());
        assertEquals(1, room.size());
        assertEquals("s1-again", room.player(was).sessionId(), "rebound to the new socket");
    }

    @Test
    void aPublicPlayerIdCannotReclaimSomeoneElsesSeat() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        String hostId = rm.join(room.code(), "host", null, "Ann").player().playerId();

        rm.disconnect("host");
        JoinResult attacker = rm.join(room.code(), "attacker", hostId, "Eve");

        assertTrue(attacker.ok());
        assertFalse(attacker.rejoined());
        assertNotEquals(hostId, attacker.player().playerId());
        assertEquals(2, room.size());
    }

    @Test
    void aConnectedSessionCannotJoinAnotherRoomAndLeaveAGhostSeat() {
        RoomManager rm = manager(new TestClock(0));
        Room first = rm.create();
        Room second = rm.create();
        rm.join(first.code(), "s1", null, "Ann");

        JoinResult attempt = rm.join(second.code(), "s1", null, "Ann");

        assertFalse(attempt.ok());
        assertEquals(JoinResult.Failure.ALREADY_IN_ROOM, attempt.failure());
        assertEquals(1, first.size());
        assertEquals(0, second.size());
    }

    // --- host reassignment ---

    @Test
    void hostPassesToTheNextPlayerInJoinOrder() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");
        rm.join(room.code(), "s3", null, "Cy");

        rm.disconnect("s1");

        assertTrue(room.isHost(pid(room, "s2")), "second to join takes over");
    }

    @Test
    void hostSkipsPlayersWhoAreAlsoAway() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");
        rm.join(room.code(), "s3", null, "Cy");

        rm.disconnect("s2");
        rm.disconnect("s1");

        assertTrue(room.isHost(pid(room, "s3")), "s2 is away, so it falls through to s3");
    }

    @Test
    void hostReassignmentFollowsJoinOrderNotLeaveOrder() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");
        rm.join(room.code(), "s3", null, "Cy");

        rm.leave("s2");
        rm.leave("s1");

        assertTrue(room.isHost(pid(room, "s3")));
    }

    @Test
    void lastPlayerLeavingLeavesNoHost() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");

        rm.disconnect("s1");

        assertNull(room.hostPlayerId());
        assertFalse(room.isHost(pid(room, "s1")));
    }

    @Test
    void aReturningPlayerTakesTheHostSeatIfNobodyHoldsIt() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        String was = rm.join(room.code(), "s1", null, "Ann").player().playerId();
        rm.disconnect("s1");
        assertNull(room.hostPlayerId());

        rm.join(room.code(), "s1-again", room.player(was).reconnectToken(), "Ann");

        assertTrue(room.isHost(was));
    }

    // --- destruction timer ---

    @Test
    void emptyRoomSurvivesUntilTheDeadline() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.disconnect("s1");

        clock.advance(Room.EMPTY_TTL_MILLIS - 1);

        assertTrue(rm.sweepEmptyRooms().isEmpty());
        assertNotNull(rm.find(room.code()));
    }

    @Test
    void emptyRoomIsDestroyedOnceTheDeadlinePasses() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.disconnect("s1");

        clock.advance(Room.EMPTY_TTL_MILLIS);

        assertEquals(List.of(room.code()), rm.sweepEmptyRooms());
        assertNull(rm.find(room.code()));
        assertNull(rm.roomOf("s1"));
        assertNull(rm.playerIdOf("s1"));
    }

    @Test
    void roomWithSomebodyConnectedIsNeverSwept() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");
        rm.disconnect("s1");

        clock.advance(Room.EMPTY_TTL_MILLIS * 10);

        assertTrue(rm.sweepEmptyRooms().isEmpty(), "s2 is still connected");
        assertNotNull(rm.find(room.code()));
    }

    @Test
    void reconnectingBeforeTheDeadlineSavesTheRoom() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.disconnect("s1");

        clock.advance(Room.EMPTY_TTL_MILLIS - 1);
        rm.join(room.code(), "s1-again", room.player(pid(room, "s1")).reconnectToken(), "Ann");
        clock.advance(Room.EMPTY_TTL_MILLIS * 5);

        assertTrue(rm.sweepEmptyRooms().isEmpty());
        assertNotNull(rm.find(room.code()));
    }

    @Test
    void theClockOnlyStartsWhenTheLastPlayerGoes() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        rm.join(room.code(), "s2", null, "Bo");

        rm.disconnect("s1");
        clock.advance(Room.EMPTY_TTL_MILLIS - 1);
        rm.disconnect("s2");
        clock.advance(Room.EMPTY_TTL_MILLIS - 1);

        assertTrue(rm.sweepEmptyRooms().isEmpty(), "deadline runs from the last departure");

        clock.advance(1);
        assertEquals(1, rm.sweepEmptyRooms().size());
    }

    @Test
    void aDestroyedRoomIsGoneAndRejoiningStartsFresh() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", null, "Ann");
        String code = room.code();
        rm.disconnect("s1");
        clock.advance(Room.EMPTY_TTL_MILLIS);
        rm.sweepEmptyRooms();

        assertFalse(rm.join(code, "s1", null, "Ann").ok(), "no state survives the teardown");

        Room fresh = rm.create();
        assertTrue(rm.join(fresh.code(), "s1", null, "Ann").ok());
        assertNotSame(room, fresh);
        assertEquals(1, fresh.size());
    }
}
