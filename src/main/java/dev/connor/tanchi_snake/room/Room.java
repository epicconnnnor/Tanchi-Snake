package dev.connor.tanchi_snake.room;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.connor.tanchi_snake.game.Direction;
import dev.connor.tanchi_snake.game.GameEngine;
import dev.connor.tanchi_snake.game.GameState;
import dev.connor.tanchi_snake.game.Snake;

/**
 * One room: its players, its board, and whose turn it is to press start.
 *
 * <p>Not thread safe. Every mutation happens on the scheduler thread; socket
 * threads only ever enqueue input.
 */
public class Room {

    public static final int MAX_PLAYERS = 8;
    /** The board is square and wraps at every edge. */
    public static final int BOARD_WIDTH = 48;
    public static final int BOARD_HEIGHT = 48;

    /** Nobody connected for this long and the room is torn down. */
    public static final long EMPTY_TTL_MILLIS = 20_000;

    /** Sentinel for "somebody is here", kept off the clock's value range. */
    private static final long NOT_EMPTY = -1;

    /** How long a dropped player's snake stays frozen on the board. */
    public static final long DISCONNECT_GRACE_MILLIS = 20_000;

    /** Places highlighted on the results screen. */
    public static final int PODIUM_SIZE = 3;

    /** Colour slots, one per seat. */
    public static final int COLOR_COUNT = MAX_PLAYERS;

    /**
     * Stun topped back up every tick while a player is away. Small on purpose:
     * the loop reapplies it, so the freeze does not depend on the tick rate.
     */
    private static final int FREEZE_STUN_TICKS = 5;

    private final String code;
    /** Swapped for a fresh board between rounds, so the room keeps its code. */
    private GameState state;
    private final GameEngine engine;
    private final Random random;
    /** Insertion ordered, which is what makes "next in join order" meaningful. */
    private final Map<String, Player> players = new LinkedHashMap<>();

    /**
     * Input from this room's players, filled by socket threads and emptied by
     * the scheduler thread. The queue is the whole handover: nothing else in
     * this class is safe to touch from a socket thread.
     */
    private final Queue<ClientCommand> inbox = new ConcurrentLinkedQueue<>();

    private String hostPlayerId;
    private RoomPhase phase = RoomPhase.LOBBY;

    /** When the room last went empty, or NOT_EMPTY while somebody is here. */
    private long emptySinceMillis = NOT_EMPTY;

    public Room(String code) {
        this(code, new Random());
    }

    public Room(String code, Random random) {
        this.code = code;
        this.random = random;
        this.state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
        this.engine = new GameEngine(random);
    }

    public GameEngine engine() {
        return engine;
    }

    public String code() {
        return code;
    }

    public GameState state() {
        return state;
    }

    public RoomPhase phase() {
        return phase;
    }

    public void setPhase(RoomPhase phase) {
        this.phase = phase;
    }

    public String hostPlayerId() {
        return hostPlayerId;
    }

    public boolean isHost(String playerId) {
        return hostPlayerId != null && hostPlayerId.equals(playerId);
    }

    /** Players in join order, connected or not. */
    public Collection<Player> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    public Player player(String playerId) {
        return players.get(playerId);
    }

    /** Finds the seat currently bound to a socket, or null. */
    public Player playerBySession(String sessionId) {
        for (Player p : players.values()) {
            if (sessionId.equals(p.sessionId())) {
                return p;
            }
        }
        return null;
    }

    public int size() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public long connectedCount() {
        return players.values().stream().filter(Player::isConnected).count();
    }

    /**
     * Seats a player. The first one through the door is the host.
     *
     * @return the seated player, or null if the room is already full
     */
    public Player add(Player p) {
        if (isFull()) {
            return null;
        }
        p.setColorIndex(lowestFreeColor());
        players.put(p.playerId(), p);
        if (hostPlayerId == null) {
            hostPlayerId = p.playerId();
        }
        emptySinceMillis = NOT_EMPTY;
        return p;
    }

    /**
     * The lowest colour slot nobody in this room is using. A player leaving
     * frees theirs, and the next arrival takes it rather than pushing everyone
     * further down the palette.
     */
    private int lowestFreeColor() {
        boolean[] taken = new boolean[COLOR_COUNT];
        for (Player seated : players.values()) {
            int c = seated.colorIndex();
            if (c >= 0 && c < COLOR_COUNT) {
                taken[c] = true;
            }
        }
        for (int i = 0; i < COLOR_COUNT; i++) {
            if (!taken[i]) {
                return i;
            }
        }
        // Unreachable while the seat count and the palette match, but a colour
        // is better than a crash if they ever drift apart.
        return 0;
    }

    /** Drops a player for good. Contrast with {@link #markDisconnected}. */
    public Player remove(String playerId, long nowMillis) {
        Player gone = players.remove(playerId);
        if (gone != null) {
            afterLeaving(playerId, nowMillis);
        }
        return gone;
    }

    /**
     * Keeps the player's seat but marks them away, so a mid-round snake can be
     * frozen and picked back up if they return.
     */
    public Player markDisconnected(String playerId, long nowMillis) {
        Player p = players.get(playerId);
        if (p == null) {
            return null;
        }
        p.markDisconnected(nowMillis);
        afterLeaving(playerId, nowMillis);
        return p;
    }

    /** Brings a player back, on whatever socket they reconnected with. */
    public Player markConnected(String playerId, String sessionId) {
        Player p = players.get(playerId);
        if (p != null) {
            p.bindSession(sessionId);
            p.markConnected();
            emptySinceMillis = NOT_EMPTY;
            if (hostPlayerId == null) {
                hostPlayerId = playerId;
            }
        }
        return p;
    }

    private void afterLeaving(String playerId, long nowMillis) {
        if (playerId.equals(hostPlayerId)) {
            reassignHost();
        }
        if (connectedCount() == 0 && emptySinceMillis == NOT_EMPTY) {
            emptySinceMillis = nowMillis;
        }
    }

    /** Hands the room to the next connected player in join order. */
    private void reassignHost() {
        hostPlayerId = null;
        for (Player p : players.values()) {
            if (p.isConnected()) {
                hostPlayerId = p.playerId();
                return;
            }
        }
    }

    public long emptySinceMillis() {
        return emptySinceMillis;
    }

    public boolean isExpired(long nowMillis) {
        return emptySinceMillis != NOT_EMPTY && nowMillis - emptySinceMillis >= EMPTY_TTL_MILLIS;
    }

    /** Safe to call from any thread; everything else here is not. */
    public void enqueue(ClientCommand command) {
        if (command != null) {
            inbox.add(command);
        }
    }

    /** Empties the inbox for the scheduler thread to work through. */
    public List<ClientCommand> drainInbox() {
        List<ClientCommand> taken = new ArrayList<>();
        for (ClientCommand c = inbox.poll(); c != null; c = inbox.poll()) {
            taken.add(c);
        }
        return taken;
    }

    public int inboxSize() {
        return inbox.size();
    }

    /** The sockets this room's players are on, for broadcasting to just them. */
    public List<String> sessionIds() {
        List<String> ids = new ArrayList<>(players.size());
        for (Player p : players.values()) {
            if (p.sessionId() != null) {
                ids.add(p.sessionId());
            }
        }
        return ids;
    }

    /** Player ids in join order. */
    public List<String> playerIds() {
        return new ArrayList<>(players.keySet());
    }

    // --- lobby ---

    /** True when every connected player has ticked ready. */
    public boolean allReady() {
        boolean any = false;
        for (Player p : players.values()) {
            if (p.isConnected()) {
                any = true;
                if (!p.isReady()) {
                    return false;
                }
            }
        }
        return any;
    }

    public boolean toggleReady(String playerId) {
        Player p = players.get(playerId);
        if (p == null || phase != RoomPhase.LOBBY) {
            return false;
        }
        p.setReady(!p.isReady());
        return p.isReady();
    }

    // --- round ---

    /**
     * Starts the round at the host's word. Ready flags are shown to players but
     * do not gate this: the host decides when everyone has waited long enough.
     *
     * @return false if the caller is not the host, or the room is not in a
     *         lobby to start from
     */
    public boolean startRound(String playerId) {
        if (!isHost(playerId) || phase != RoomPhase.LOBBY) {
            return false;
        }
        for (Player p : players.values()) {
            if (p.isConnected()) {
                spawnSnakeFor(p);
            }
        }
        phase = RoomPhase.RUNNING;
        return true;
    }

    /**
     * Puts a snake on the board for a player, facing a random way. Used both at
     * the start and for anyone joining mid-round, who gets no protection and
     * starts at level 1 like everyone else did.
     *
     * @return the snake, or null if the board had no room for it
     */
    public Snake spawnSnakeFor(Player p) {
        if (snakeOf(p.playerId()) != null) {
            return null;
        }
        Direction facing = Direction.values()[random.nextInt(Direction.values().length)];
        // Keyed by the stable id, so the snake outlives the socket.
        return engine.spawnSnake(state, p.playerId(), facing);
    }

    /** The snake belonging to a player, or null if they have none on the board. */
    public Snake snakeOf(String playerId) {
        for (Snake s : state.snakes()) {
            if (s.id().equals(playerId)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Holds the snakes of players who have dropped. They stop moving but stay
     * on the board, and a stunned body is still lethal, so nobody can run
     * through the space a missing player left behind.
     *
     * <p>Called every tick, which is why the stun value is small rather than
     * sized to the grace period.
     */
    public void holdDisconnectedSnakes() {
        for (Player p : players.values()) {
            if (!p.isConnected()) {
                Snake s = snakeOf(p.playerId());
                if (s != null) {
                    s.stun(FREEZE_STUN_TICKS);
                    // Being held for an absent player is not the engine's
                    // stun-forever loop, so it must not count towards the
                    // death that breaks that loop. This snake is waiting for
                    // its player, and the disconnect grace period is what
                    // decides how long that lasts.
                    s.clearStuckTicks();
                }
            }
        }
    }

    /** Hands a returning player their snake back, unfrozen. */
    public void resumeSnake(String playerId) {
        Snake s = snakeOf(playerId);
        if (s != null) {
            s.stun(0);
        }
    }

    /**
     * Clears out snakes whose players never came back, and gives up their
     * seats. Their standing is remembered so they still appear in the results.
     *
     * @return the session ids that were dropped
     */
    public List<String> dropExpiredPlayers(long nowMillis) {
        List<String> dropped = new ArrayList<>();
        for (Player p : new ArrayList<>(players.values())) {
            if (p.isConnected()) {
                continue;
            }
            if (nowMillis - p.disconnectedAtMillis() < DISCONNECT_GRACE_MILLIS) {
                continue;
            }
            releaseSnake(p);
            remove(p.playerId(), nowMillis);
            dropped.add(p.playerId());
        }
        return dropped;
    }

    /**
     * A player leaving of their own accord. Their seat goes back immediately
     * rather than after the reconnect window, but the snake comes off the
     * board the same way, so the round they walked out of still ranks them.
     *
     * @return the player who left, or null if they were not in this room
     */
    public Player leaveNow(String playerId, long nowMillis) {
        Player p = players.get(playerId);
        if (p == null) {
            return null;
        }
        releaseSnake(p);
        return remove(playerId, nowMillis);
    }

    /** Takes a player's snake off the board, keeping the standing it earned. */
    private void releaseSnake(Player p) {
        Snake s = snakeOf(p.playerId());
        if (s != null) {
            p.rememberStanding(s.level(), s.levelReachedTick());
            state.removeSnake(p.playerId());
        }
    }

    /** Moves to the results screen once the board has a winner. */
    public boolean finishIfWon() {
        if (phase == RoomPhase.RUNNING && state.hasWinner()) {
            phase = RoomPhase.RESULTS;
            return true;
        }
        return false;
    }

    /**
     * Sends everyone back to this same lobby, keeping the room and its code.
     * The board is wiped and ready flags cleared so the next round starts even.
     */
    public void returnToLobby() {
        for (Player p : players.values()) {
            p.setReady(false);
            p.rememberStanding(0, 0);
        }
        // A fresh board clears the snakes, the food, the tick count and the
        // winner in one go, which the next round needs to run at all.
        state = new GameState(BOARD_WIDTH, BOARD_HEIGHT);
        phase = RoomPhase.LOBBY;
    }

    // --- results ---

    /**
     * Every player in the room, best first: highest level, and on a tie the one
     * who got there first. Players whose snake has already left the board are
     * ranked on the standing remembered when it went.
     */
    public List<Standing> standings() {
        record Row(Player player, int level, int levelTick) {
        }

        List<Row> rows = new ArrayList<>();
        for (Player p : players.values()) {
            Snake s = snakeOf(p.playerId());
            rows.add(s != null
                    ? new Row(p, s.level(), s.levelReachedTick())
                    : new Row(p, p.lastKnownLevel(), p.lastKnownLevelTick()));
        }

        rows.sort(Comparator
                .comparingInt(Row::level).reversed()
                .thenComparingInt(Row::levelTick)
                .thenComparing(r -> r.player().playerId()));

        List<Standing> table = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rank = i + 1;
            table.add(new Standing(
                    rank,
                    r.player().playerId(),
                    r.player().name(),
                    r.level(),
                    r.levelTick(),
                    r.player().isConnected(),
                    rank <= PODIUM_SIZE));
        }
        return table;
    }
}
