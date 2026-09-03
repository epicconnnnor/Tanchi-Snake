package dev.connor.tanchi_snake.net;

import java.util.ArrayList;
import java.util.List;

import dev.connor.tanchi_snake.game.Point;
import dev.connor.tanchi_snake.game.Snake;
import dev.connor.tanchi_snake.room.Player;
import dev.connor.tanchi_snake.room.Room;
import dev.connor.tanchi_snake.room.RoomPhase;
import dev.connor.tanchi_snake.room.Standing;

/**
 * The whole room, flattened for the wire. Sent in full every tick; there is no
 * delta encoding yet and none is needed at eight players on a 32x32 board.
 */
public record StateMessage(
        String type,
        String room,
        String phase,
        int tick,
        int width,
        int height,
        String hostPlayerId,
        String winnerPlayerId,
        List<SnakeView> snakes,
        List<PointView> food,
        List<PlayerView> players,
        List<Standing> standings) {

    public record PointView(int x, int y) {
    }

    public record SnakeView(
            String id,
            String name,
            int colorIndex,
            int level,
            int length,
            boolean stunned,
            String direction,
            List<PointView> body) {
    }

    public record PlayerView(
            String playerId,
            String name,
            int colorIndex,
            boolean ready,
            boolean connected,
            boolean host) {
    }

    /**
     * Snapshots a room. Called on the scheduler thread only, which is what
     * makes reading the mutable game state here safe.
     */
    public static StateMessage of(Room room) {
        List<SnakeView> snakes = new ArrayList<>();
        for (Snake s : room.state().snakes()) {
            Player owner = room.player(s.id());
            snakes.add(new SnakeView(
                    s.id(),
                    owner == null ? s.id() : owner.name(),
                    owner == null ? 0 : owner.colorIndex(),
                    s.level(),
                    s.length(),
                    s.stunTicks() > 0,
                    s.direction().name(),
                    points(s.body())));
        }

        List<PlayerView> players = new ArrayList<>();
        for (Player p : room.players()) {
            players.add(new PlayerView(
                    p.playerId(),
                    p.name(),
                    p.colorIndex(),
                    p.isReady(),
                    p.isConnected(),
                    room.isHost(p.playerId())));
        }

        Snake winner = room.state().winner();

        return new StateMessage(
                "state",
                room.code(),
                room.phase().name(),
                room.state().tick(),
                room.state().width(),
                room.state().height(),
                room.hostPlayerId(),
                winner == null ? null : winner.id(),
                snakes,
                points(room.state().food()),
                players,
                room.phase() == RoomPhase.RESULTS ? room.standings() : List.of());
    }

    private static List<PointView> points(Iterable<Point> source) {
        List<PointView> out = new ArrayList<>();
        for (Point p : source) {
            out.add(new PointView(p.x(), p.y()));
        }
        return out;
    }
}
