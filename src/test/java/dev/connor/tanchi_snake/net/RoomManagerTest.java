package dev.connor.tanchi_snake.net;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

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
        JoinResult result = rm.join("ZZZZ", "s1", "Ann");

        assertFalse(result.ok());
        assertEquals(JoinResult.Failure.NO_SUCH_ROOM, result.failure());
        assertNull(result.room());
    }

    @Test
    void firstPlayerThroughTheDoorIsHost() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();

        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");

        assertTrue(room.isHost("s1"));
        assertFalse(room.isHost("s2"));
        assertEquals(2, room.size());
    }

    @Test
    void roomsFillUpAtEightPlayers() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();

        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            assertTrue(rm.join(room.code(), "s" + i, "p" + i).ok());
        }
        assertTrue(room.isFull());

        JoinResult overflow = rm.join(room.code(), "extra", "late");
        assertFalse(overflow.ok());
        assertEquals(JoinResult.Failure.ROOM_FULL, overflow.failure());
        assertEquals(Room.MAX_PLAYERS, room.size());
    }

    @Test
    void leavingReleasesTheSeat() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");

        rm.leave("s1");

        assertEquals(1, room.size());
        assertNull(room.player("s1"));
        assertNull(rm.roomOf("s1"));
    }

    @Test
    void disconnectKeepsTheSeatAndReconnectResumesIt() {
        RoomManager rm = manager(new TestClock(1_000));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");

        rm.disconnect("s1");
        assertEquals(1, room.size(), "seat is held for a return");
        assertFalse(room.player("s1").isConnected());
        assertEquals(1_000, room.player("s1").disconnectedAtMillis());

        JoinResult back = rm.join(room.code(), "s1", "Ann");
        assertTrue(back.ok());
        assertTrue(back.rejoined());
        assertTrue(room.player("s1").isConnected());
        assertEquals(1, room.size());
    }

    // --- host reassignment ---

    @Test
    void hostPassesToTheNextPlayerInJoinOrder() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");
        rm.join(room.code(), "s3", "Cy");

        rm.disconnect("s1");

        assertTrue(room.isHost("s2"), "second to join takes over");
    }

    @Test
    void hostSkipsPlayersWhoAreAlsoAway() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");
        rm.join(room.code(), "s3", "Cy");

        rm.disconnect("s2");
        rm.disconnect("s1");

        assertTrue(room.isHost("s3"), "s2 is away, so it falls through to s3");
    }

    @Test
    void hostReassignmentFollowsJoinOrderNotLeaveOrder() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");
        rm.join(room.code(), "s3", "Cy");

        rm.leave("s2");
        rm.leave("s1");

        assertTrue(room.isHost("s3"));
    }

    @Test
    void lastPlayerLeavingLeavesNoHost() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");

        rm.disconnect("s1");

        assertNull(room.hostSessionId());
        assertFalse(room.isHost("s1"));
    }

    @Test
    void aReturningPlayerTakesTheHostSeatIfNobodyHoldsIt() {
        RoomManager rm = manager(new TestClock(0));
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.disconnect("s1");
        assertNull(room.hostSessionId());

        rm.join(room.code(), "s1", "Ann");

        assertTrue(room.isHost("s1"));
    }

    // --- destruction timer ---

    @Test
    void emptyRoomSurvivesUntilTheDeadline() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
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
        rm.join(room.code(), "s1", "Ann");
        rm.disconnect("s1");

        clock.advance(Room.EMPTY_TTL_MILLIS);

        assertEquals(List.of(room.code()), rm.sweepEmptyRooms());
        assertNull(rm.find(room.code()));
        assertNull(rm.roomOf("s1"));
    }

    @Test
    void roomWithSomebodyConnectedIsNeverSwept() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");
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
        rm.join(room.code(), "s1", "Ann");
        rm.disconnect("s1");

        clock.advance(Room.EMPTY_TTL_MILLIS - 1);
        rm.join(room.code(), "s1", "Ann");
        clock.advance(Room.EMPTY_TTL_MILLIS * 5);

        assertTrue(rm.sweepEmptyRooms().isEmpty());
        assertNotNull(rm.find(room.code()));
    }

    @Test
    void theClockOnlyStartsWhenTheLastPlayerGoes() {
        TestClock clock = new TestClock(0);
        RoomManager rm = manager(clock);
        Room room = rm.create();
        rm.join(room.code(), "s1", "Ann");
        rm.join(room.code(), "s2", "Bo");

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
        rm.join(room.code(), "s1", "Ann");
        String code = room.code();
        rm.disconnect("s1");
        clock.advance(Room.EMPTY_TTL_MILLIS);
        rm.sweepEmptyRooms();

        assertFalse(rm.join(code, "s1", "Ann").ok(), "no state survives the teardown");

        Room fresh = rm.create();
        assertTrue(rm.join(fresh.code(), "s1", "Ann").ok());
        assertNotSame(room, fresh);
        assertEquals(1, fresh.size());
    }
}
