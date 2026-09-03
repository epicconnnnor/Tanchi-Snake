package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RoomColorTest {

    private static Room room() {
        return new Room("ABCD", new Random(19));
    }

    private static Player seat(Room room, String id) {
        return room.add(new Player(id, id, "name-" + id));
    }

    @Test
    void coloursAreHandedOutInJoinOrder() {
        Room room = room();

        assertEquals(0, seat(room, "a").colorIndex());
        assertEquals(1, seat(room, "b").colorIndex());
        assertEquals(2, seat(room, "c").colorIndex());
    }

    @Test
    void everySeatInAFullRoomGetsItsOwnColour() {
        Room room = room();
        Set<Integer> used = new HashSet<>();

        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            Player p = seat(room, "p" + i);
            assertTrue(p.colorIndex() >= 0 && p.colorIndex() < Room.COLOR_COUNT,
                    "colour out of palette: " + p.colorIndex());
            assertTrue(used.add(p.colorIndex()), "colour " + p.colorIndex() + " handed out twice");
        }
        assertEquals(Room.MAX_PLAYERS, used.size());
    }

    @Test
    void aFreedColourIsReusedByTheNextArrival() {
        Room room = room();
        seat(room, "a");
        seat(room, "b");
        seat(room, "c");

        room.remove("b", 0); // frees colour 1

        assertEquals(1, seat(room, "d").colorIndex(), "should fill the gap, not take 3");
    }

    @Test
    void theLowestGapIsFilledFirst() {
        Room room = room();
        for (int i = 0; i < 5; i++) {
            seat(room, "p" + i);
        }

        room.remove("p3", 0);
        room.remove("p1", 0);

        assertEquals(1, seat(room, "x").colorIndex(), "lowest free slot first");
        assertEquals(3, seat(room, "y").colorIndex());
        assertEquals(5, seat(room, "z").colorIndex(), "then carry on past the end");
    }

    @Test
    void aColourIsKeptWhileTheSeatIsHeld() {
        Room room = room();
        seat(room, "a");
        Player bo = seat(room, "b");
        int was = bo.colorIndex();

        // Dropping out holds the seat, so the colour is not up for grabs.
        room.markDisconnected("b", 0);

        assertEquals(was, room.player("b").colorIndex());
        assertEquals(2, seat(room, "c").colorIndex(), "an away player still owns colour 1");
    }

    @Test
    void aReturningPlayerKeepsTheColourTheyLeftWith() {
        Room room = room();
        seat(room, "a");
        Player bo = seat(room, "b");
        int was = bo.colorIndex();

        room.markDisconnected("b", 0);
        room.markConnected("b", "a-new-socket");

        assertEquals(was, room.player("b").colorIndex());
    }

    @Test
    void colourSurvivesAReturnToTheLobby() {
        Room room = room();
        seat(room, "a");
        Player bo = seat(room, "b");
        int was = bo.colorIndex();
        room.startRound("a");
        room.state().setWinner(room.snakeOf("a"));
        room.finishIfWon();

        room.returnToLobby();

        assertEquals(was, room.player("b").colorIndex());
    }

    @Test
    void coloursStayUniqueAcrossChurn() {
        Room room = room();
        List<String> present = new ArrayList<>();
        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            seat(room, "p" + i);
            present.add("p" + i);
        }

        // Cycle the room a few times over and never hand out a duplicate.
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                String leaving = present.remove(0);
                room.remove(leaving, 0);
            }
            for (int i = 0; i < 4; i++) {
                String id = "r" + round + "-" + i;
                seat(room, id);
                present.add(id);
            }

            Set<Integer> used = new HashSet<>();
            for (Player p : room.players()) {
                assertTrue(p.colorIndex() >= 0 && p.colorIndex() < Room.COLOR_COUNT,
                        "colour out of palette: " + p.colorIndex());
                assertTrue(used.add(p.colorIndex()),
                        "duplicate colour " + p.colorIndex() + " in round " + round);
            }
        }
    }

    @Test
    void roomsColourIndependently() {
        Room one = room();
        Room two = new Room("EFGH", new Random(20));

        seat(one, "a");
        Player first = seat(two, "z");

        assertEquals(0, first.colorIndex(), "a new room starts its palette over");
    }

    @Test
    void anUnseatedPlayerHasNoColourYet() {
        assertEquals(Player.UNASSIGNED, new Player("a", "a", "Ann").colorIndex());
    }
}
