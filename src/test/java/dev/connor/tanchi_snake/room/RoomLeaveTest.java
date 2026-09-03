package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.connor.tanchi_snake.game.Snake;

/**
 * Walking out of a room, as opposed to a socket dropping. The seat goes back
 * at once instead of being held for the reconnect window.
 */
class RoomLeaveTest {

    private static Room room() {
        return new Room("ABCD", new Random(5));
    }

    private static void seat(Room room, String id, String name) {
        room.add(new Player(id, id, name));
    }

    // --- leaving the lobby ---

    @Test
    void leavingTheLobbyGivesUpTheSeat() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");

        assertNotNull(room.leaveNow("s2", 0));

        assertEquals(1, room.size(), "the seat is free again straight away");
        assertNull(room.player("s2"));
    }

    @Test
    void leavingIsNotTheSameAsDroppingOut() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");

        room.markDisconnected("s2", 0);
        assertEquals(2, room.size(), "a dropped player keeps their seat for a while");

        room.leaveNow("s2", 0);
        assertEquals(1, room.size(), "leaving does not wait for the window");
    }

    @Test
    void theHostLeavingHandsTheRoomOn() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        assertEquals("s1", room.hostPlayerId());

        room.leaveNow("s1", 0);

        assertEquals("s2", room.hostPlayerId(), "the next player takes it");
        assertTrue(room.isHost("s2"));
    }

    @Test
    void theLastPlayerLeavingStartsTheRoomExpiring() {
        Room room = room();
        seat(room, "s1", "Ann");

        room.leaveNow("s1", 1_000);

        assertEquals(0, room.size());
        assertTrue(room.isExpired(1_000 + Room.EMPTY_TTL_MILLIS),
                "an empty room should be swept eventually");
    }

    @Test
    void leavingARoomYouAreNotInDoesNothing() {
        Room room = room();
        seat(room, "s1", "Ann");

        assertNull(room.leaveNow("nobody", 0));
        assertEquals(1, room.size());
    }

    // --- leaving mid-round ---

    @Test
    void leavingMidRoundTakesTheSnakeOffTheBoard() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        assertNotNull(room.snakeOf("s2"), "it was on the board to begin with");

        room.leaveNow("s2", 0);

        assertNull(room.snakeOf("s2"), "and it is gone, not left sitting there");
        assertEquals(1, room.state().snakes().size());
    }

    @Test
    void aLeaverStillRanksInTheRoundTheyWalkedOutOf() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        Snake s = room.snakeOf("s2");
        s.setLevel(6);
        s.setLevelReachedTick(40);

        Player gone = room.leaveNow("s2", 0);

        assertNotNull(gone);
        assertEquals(6, gone.lastKnownLevel(), "the level they reached is kept");
        assertEquals(40, gone.lastKnownLevelTick());
    }

    @Test
    void theHostLeavingMidRoundHandsTheRoomOn() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");

        room.leaveNow("s1", 0);

        assertEquals("s2", room.hostPlayerId());
        assertEquals(RoomPhase.RUNNING, room.phase(), "the round carries on without them");
    }

    @Test
    void theSeatIsFreeForSomebodyElseImmediately() {
        Room room = room();
        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            seat(room, "s" + i, "P" + i);
        }
        assertTrue(room.isFull());

        room.leaveNow("s3", 0);

        assertFalse(room.isFull(), "the seat is back in the pool");
        assertNotNull(room.add(new Player("late", "late", "Late")));
        assertTrue(room.isFull());
    }
}
