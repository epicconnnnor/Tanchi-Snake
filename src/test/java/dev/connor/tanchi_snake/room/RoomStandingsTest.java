package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.connor.tanchi_snake.game.Snake;

class RoomStandingsTest {

    private static Room startedRoom(String... sessionIds) {
        Room room = new Room("ABCD", new Random(23));
        for (String id : sessionIds) {
            room.add(new Player(id, "name-" + id));
        }
        room.startRound(sessionIds[0]);
        return room;
    }

    /** Puts a player on a level, as if they had climbed to it on that tick. */
    private static void place(Room room, String sessionId, int level, int levelTick) {
        Snake s = room.snakeOf(sessionId);
        s.setLevel(level);
        s.setLevelReachedTick(levelTick);
    }

    private static List<String> order(Room room) {
        List<String> ids = new ArrayList<>();
        for (Standing s : room.standings()) {
            ids.add(s.sessionId());
        }
        return ids;
    }

    @Test
    void higherLevelRanksFirst() {
        Room room = startedRoom("s1", "s2", "s3");
        place(room, "s1", 3, 100);
        place(room, "s2", 9, 100);
        place(room, "s3", 5, 100);

        assertEquals(List.of("s2", "s3", "s1"), order(room));
    }

    @Test
    void aTieGoesToWhoeverGotThereFirst() {
        Room room = startedRoom("s1", "s2", "s3");
        place(room, "s1", 7, 900);
        place(room, "s2", 7, 120);
        place(room, "s3", 7, 450);

        assertEquals(List.of("s2", "s3", "s1"), order(room));
    }

    @Test
    void levelOutranksTheTiebreak() {
        Room room = startedRoom("s1", "s2");
        // s2 got to its level much earlier, but it is a lower level.
        place(room, "s1", 8, 5_000);
        place(room, "s2", 7, 1);

        assertEquals(List.of("s1", "s2"), order(room));
    }

    @Test
    void tiesAreBrokenAllTheWayDownTheTable() {
        Room room = startedRoom("s1", "s2", "s3", "s4");
        place(room, "s1", 5, 300);
        place(room, "s2", 5, 100);
        place(room, "s3", 2, 800);
        place(room, "s4", 2, 200);

        assertEquals(List.of("s2", "s1", "s4", "s3"), order(room));
    }

    @Test
    void ranksAreConsecutiveFromOne() {
        Room room = startedRoom("s1", "s2", "s3");
        place(room, "s1", 4, 10);
        place(room, "s2", 4, 10);
        place(room, "s3", 1, 0);

        List<Standing> table = room.standings();
        for (int i = 0; i < table.size(); i++) {
            assertEquals(i + 1, table.get(i).rank());
        }
    }

    @Test
    void aFullyTiedTableStillOrdersDeterministically() {
        // Same level and same tick: the ordering must not wobble between runs.
        List<String> first = order(tiedRoom());
        List<String> second = order(tiedRoom());

        assertEquals(first, second);
        assertEquals(3, first.size());
    }

    private static Room tiedRoom() {
        Room room = startedRoom("s3", "s1", "s2");
        place(room, "s1", 4, 50);
        place(room, "s2", 4, 50);
        place(room, "s3", 4, 50);
        return room;
    }

    @Test
    void theTopThreeAreFlaggedForThePodium() {
        Room room = startedRoom("s1", "s2", "s3", "s4", "s5");
        place(room, "s1", 9, 10);
        place(room, "s2", 8, 10);
        place(room, "s3", 7, 10);
        place(room, "s4", 6, 10);
        place(room, "s5", 5, 10);

        List<Standing> table = room.standings();

        assertEquals(Room.PODIUM_SIZE, table.stream().filter(Standing::podium).count());
        for (Standing s : table) {
            assertEquals(s.rank() <= Room.PODIUM_SIZE, s.podium(), s.sessionId());
        }
    }

    @Test
    void everyPlayerAppearsIncludingThoseWhoDropped() {
        Room room = startedRoom("s1", "s2", "s3");
        place(room, "s1", 4, 10);
        place(room, "s2", 6, 10);
        place(room, "s3", 2, 10);
        room.markDisconnected("s3", 0);

        List<Standing> table = room.standings();

        assertEquals(3, table.size());
        assertEquals(List.of("s2", "s1", "s3"), order(room));
        assertFalse(table.get(2).connected(), "shown, but marked away");
    }

    @Test
    void aPlayerWhoseSnakeIsGoneKeepsTheirLastStanding() {
        Room room = startedRoom("s1", "s2");
        place(room, "s1", 3, 400);
        place(room, "s2", 8, 50);

        // s2 drops and the window lapses, taking their snake off the board.
        room.markDisconnected("s2", 0);
        room.dropExpiredPlayers(Room.DISCONNECT_GRACE_MILLIS);

        List<Standing> table = room.standings();
        assertEquals(1, table.size(), "the seat is gone once the window lapses");
        assertEquals("s1", table.get(0).sessionId());
    }

    @Test
    void namesRideAlongForTheResultsScreen() {
        Room room = new Room("ABCD", new Random(23));
        room.add(new Player("s1", "Ann"));
        room.add(new Player("s2", "Sleepy Green Snake"));
        room.startRound("s1");
        place(room, "s1", 2, 10);
        place(room, "s2", 5, 10);

        List<Standing> table = room.standings();

        assertEquals("Sleepy Green Snake", table.get(0).name());
        assertEquals("Ann", table.get(1).name());
    }

    @Test
    void standingsAreEmptyForAnEmptyRoom() {
        assertTrue(new Room("ABCD", new Random(23)).standings().isEmpty());
    }

    @Test
    void lobbyPlayersWithNoSnakeRankAtLevelZero() {
        Room room = new Room("ABCD", new Random(23));
        room.add(new Player("s1", "Ann"));
        room.add(new Player("s2", "Bo"));

        List<Standing> table = room.standings();

        assertEquals(2, table.size());
        assertEquals(0, table.get(0).level());
        assertEquals(0, table.get(1).level());
    }
}
