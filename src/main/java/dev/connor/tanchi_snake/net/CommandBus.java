package dev.connor.tanchi_snake.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import dev.connor.tanchi_snake.room.ClientCommand;

/**
 * Holding pen for commands that are not tied to a room yet: creating one,
 * joining one, and dropping off the end of a socket.
 *
 * <p>Commands from players already seated go on their own room's queue
 * instead. Either way a socket thread only ever enqueues; the scheduler thread
 * is the only one that drains.
 */
@Component
public class CommandBus {

    private final Queue<ClientCommand> queue = new ConcurrentLinkedQueue<>();

    public void submit(ClientCommand command) {
        if (command != null) {
            queue.add(command);
        }
    }

    /** Takes everything queued so far, leaving anything that arrives mid-drain. */
    public List<ClientCommand> drain() {
        List<ClientCommand> taken = new ArrayList<>();
        for (ClientCommand c = queue.poll(); c != null; c = queue.poll()) {
            taken.add(c);
        }
        return taken;
    }

    public int size() {
        return queue.size();
    }
}
