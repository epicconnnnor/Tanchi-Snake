package dev.connor.tanchi_snake.net;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.connor.tanchi_snake.game.GameEngine;
import dev.connor.tanchi_snake.game.Point;
import dev.connor.tanchi_snake.game.Snake;
import dev.connor.tanchi_snake.room.Player;
import dev.connor.tanchi_snake.room.Room;

class StateMessageTest {

    /** A room mid-round, with playerId and sessionId deliberately different. */
    private static Room startedRoom() {
        Room room = new Room("ABCD", new Random(23));
        room.add(new Player("player-1", "session-1", "Ann"));
        room.add(new Player("player-2", "session-2", "Bo"));
        room.startRound("player-1");
        return room;
    }

    @Test
    void winnerPlayerIdIsNullBeforeAnyoneHasWon() {
        assertNull(StateMessage.of(startedRoom()).winnerPlayerId());
    }

    /*
     * The field used to be called winnerSessionId while always carrying a
     * playerId. The two are distinct here, so this fails if the wrong one is
     * ever put on the wire.
     */
    @Test
    void winnerPlayerIdCarriesThePlayerIdNotTheSessionId() {
        Room room = startedRoom();
        Snake winner = room.snakeOf("player-2");
        room.state().setWinner(winner);

        StateMessage state = StateMessage.of(room);

        assertEquals("player-2", state.winnerPlayerId());
        assertNotEquals("session-2", state.winnerPlayerId());
    }

    @Test
    void foodGoesOnTheWireWithWhatItIsWorth() {
        Room room = startedRoom();
        room.state().addFood(new Point(4, 9), 3);

        StateMessage state = StateMessage.of(room);

        StateMessage.FoodView rich = state.food().stream()
                .filter(f -> f.x() == 4 && f.y() == 9)
                .findFirst().orElseThrow();
        assertEquals(3, rich.value());

        for (StateMessage.FoodView f : state.food()) {
            assertTrue(f.value() >= 1 && f.value() <= GameEngine.MAX_FOOD_VALUE,
                    "food worth " + f.value() + " is off the scale");
        }
    }

    @Test
    void theWinnerMatchesAPlayerOnTheWire() {
        Room room = startedRoom();
        room.state().setWinner(room.snakeOf("player-1"));

        StateMessage state = StateMessage.of(room);

        assertTrue(state.players().stream()
                .anyMatch(p -> p.playerId().equals(state.winnerPlayerId())),
                "the results screen looks the winner up in the player list");
    }
}
