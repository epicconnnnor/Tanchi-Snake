package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.connor.tanchi_snake.game.GameEngine;
import dev.connor.tanchi_snake.game.Snake;

class RoomLobbyTest {

    private static Room room() {
        return new Room("ABCD", new Random(17));
    }

    private static Player seat(Room room, String id, String name) {
        return room.add(new Player(id, name));
    }

    // --- ready flags ---

    @Test
    void playersStartUnreadyAndToggle() {
        Room room = room();
        seat(room, "s1", "Ann");

        assertFalse(room.player("s1").isReady());
        assertTrue(room.toggleReady("s1"));
        assertTrue(room.player("s1").isReady());
        assertFalse(room.toggleReady("s1"));
        assertFalse(room.player("s1").isReady());
    }

    @Test
    void allReadyNeedsEveryConnectedPlayer() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");

        assertFalse(room.allReady());
        room.toggleReady("s1");
        assertFalse(room.allReady());
        room.toggleReady("s2");
        assertTrue(room.allReady());
    }

    @Test
    void anAwayPlayerDoesNotHoldUpAllReady() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.toggleReady("s1");

        room.markDisconnected("s2", 0);

        assertTrue(room.allReady(), "only connected players count");
    }

    @Test
    void anEmptyRoomIsNotAllReady() {
        assertFalse(room().allReady());
    }

    @Test
    void togglingIsIgnoredOutsideTheLobby() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");

        assertFalse(room.toggleReady("s1"));
        assertFalse(room.player("s1").isReady());
    }

    @Test
    void togglingAnUnknownPlayerIsIgnored() {
        assertFalse(room().toggleReady("nobody"));
    }

    // --- starting ---

    @Test
    void onlyTheHostCanStart() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");

        assertFalse(room.startRound("s2"), "not the host");
        assertEquals(RoomPhase.LOBBY, room.phase());

        assertTrue(room.startRound("s1"));
        assertEquals(RoomPhase.RUNNING, room.phase());
    }

    @Test
    void startingPutsEveryConnectedPlayerOnTheBoard() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        seat(room, "s3", "Cy");
        room.markDisconnected("s3", 0);

        room.startRound("s1");

        assertNotNull(room.snakeOf("s1"));
        assertNotNull(room.snakeOf("s2"));
        assertNull(room.snakeOf("s3"), "away players are not spawned");
        assertEquals(1, room.snakeOf("s1").level());
    }

    @Test
    void startingTwiceIsRejected() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");

        assertFalse(room.startRound("s1"));
    }

    @Test
    void startingWithAnUnknownSessionIsIgnored() {
        Room room = room();
        seat(room, "s1", "Ann");

        assertFalse(room.startRound("nobody"));
        assertEquals(RoomPhase.LOBBY, room.phase());
    }

    // --- mid-round join, freeze, resume ---

    @Test
    void aMidRoundJoinerSpawnsAtLevelOne() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");

        Player late = seat(room, "s2", "Bo");
        Snake snake = room.spawnSnakeFor(late);

        assertNotNull(snake);
        assertEquals(1, snake.level());
        assertEquals(0, snake.stunTicks(), "no protection on arrival");
    }

    @Test
    void spawningTwiceForOnePlayerIsRefused() {
        Room room = room();
        Player p = seat(room, "s1", "Ann");
        room.startRound("s1");

        assertNull(room.spawnSnakeFor(p));
    }

    @Test
    void aDroppedPlayersSnakeFreezesButStaysOnTheBoard() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");

        room.markDisconnected("s2", 0);
        room.holdDisconnectedSnakes();

        Snake frozen = room.snakeOf("s2");
        assertNotNull(frozen, "the snake stays put, it does not vanish");
        assertTrue(frozen.stunTicks() > 0, "and it is stunned, so it is lethal but still");
    }

    @Test
    void theFreezeIsToppedUpEveryTickSoItOutlastsTheStunCountdown() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        room.markDisconnected("s2", 0);

        GameEngine engine = room.engine();
        for (int i = 0; i < GameEngine.STUN_TICKS * 3; i++) {
            room.holdDisconnectedSnakes();
            engine.tick(room.state());
        }

        assertTrue(room.snakeOf("s2").stunTicks() > 0, "still frozen well past STUN_TICKS");
    }

    @Test
    void returningWithinTheWindowResumesTheSameSnake() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        Snake before = room.snakeOf("s2");

        room.markDisconnected("s2", 0);
        room.holdDisconnectedSnakes();
        room.markConnected("s2");
        room.resumeSnake("s2");

        assertSame(before, room.snakeOf("s2"), "same snake, not a new one");
        assertEquals(0, room.snakeOf("s2").stunTicks(), "and it can move again");
    }

    @Test
    void theSnakeSurvivesRightUpToTheDeadline() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        room.markDisconnected("s2", 1_000);

        assertTrue(room.dropExpiredPlayers(1_000 + Room.DISCONNECT_GRACE_MILLIS - 1).isEmpty());
        assertNotNull(room.snakeOf("s2"));
    }

    @Test
    void theSnakeIsRemovedOnceTheWindowLapses() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        room.markDisconnected("s2", 1_000);

        List<String> dropped = room.dropExpiredPlayers(1_000 + Room.DISCONNECT_GRACE_MILLIS);

        assertEquals(List.of("s2"), dropped);
        assertNull(room.snakeOf("s2"), "snake is off the board");
        assertNull(room.player("s2"), "and the seat is given up");
    }

    @Test
    void connectedPlayersAreNeverDropped() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");

        assertTrue(room.dropExpiredPlayers(Room.DISCONNECT_GRACE_MILLIS * 100).isEmpty());
        assertNotNull(room.snakeOf("s1"));
    }

    // --- finishing and returning to the lobby ---

    @Test
    void theRoundEndsWhenTheBoardHasAWinner() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");

        assertFalse(room.finishIfWon());
        assertEquals(RoomPhase.RUNNING, room.phase());

        room.state().setWinner(room.snakeOf("s1"));

        assertTrue(room.finishIfWon());
        assertEquals(RoomPhase.RESULTS, room.phase());
        assertFalse(room.finishIfWon(), "only fires once");
    }

    @Test
    void everyoneGoesBackToTheSameRoomNotANewOne() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.startRound("s1");
        room.toggleReady("s1");
        room.state().setWinner(room.snakeOf("s1"));
        room.finishIfWon();
        String code = room.code();

        room.returnToLobby();

        assertEquals(RoomPhase.LOBBY, room.phase());
        assertEquals(code, room.code(), "same room, same code");
        assertEquals(2, room.size(), "everyone is still seated");
        assertTrue(room.isHost("s1"), "host is unchanged");
    }

    @Test
    void theBoardIsWipedForTheNextRound() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");
        room.state().setWinner(room.snakeOf("s1"));
        room.finishIfWon();

        room.returnToLobby();

        assertNull(room.snakeOf("s1"), "no snakes carried over");
        assertTrue(room.state().snakes().isEmpty());
        assertNull(room.state().winner(), "winner cleared, or the next round cannot run");
        assertEquals(0, room.state().tick());
    }

    @Test
    void readyFlagsAreClearedOnTheWayBack() {
        Room room = room();
        seat(room, "s1", "Ann");
        seat(room, "s2", "Bo");
        room.toggleReady("s1");
        room.toggleReady("s2");
        room.startRound("s1");
        room.state().setWinner(room.snakeOf("s1"));
        room.finishIfWon();

        room.returnToLobby();

        assertFalse(room.player("s1").isReady());
        assertFalse(room.player("s2").isReady());
        assertFalse(room.allReady());
    }

    @Test
    void theRoomCanRunAnotherRoundAfterReturning() {
        Room room = room();
        seat(room, "s1", "Ann");
        room.startRound("s1");
        room.state().setWinner(room.snakeOf("s1"));
        room.finishIfWon();
        room.returnToLobby();

        assertTrue(room.startRound("s1"));
        assertEquals(RoomPhase.RUNNING, room.phase());
        assertNotNull(room.snakeOf("s1"));
    }
}
