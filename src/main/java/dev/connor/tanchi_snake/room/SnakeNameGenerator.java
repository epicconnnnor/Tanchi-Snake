package dev.connor.tanchi_snake.room;

import java.util.List;
import java.util.Random;

/**
 * Names players who did not pick one, in the shape "Sleepy Green Snake".
 *
 * <p>Names are not required to be unique, so no collision handling here.
 */
public class SnakeNameGenerator {

    /** Longest name a player is allowed to set; anything longer is trimmed. */
    public static final int MAX_NAME_LENGTH = 20;

    static final List<String> ADJECTIVES = List.of(
            "Sleepy", "Speedy", "Grumpy", "Clever", "Brave", "Sneaky",
            "Jolly", "Fuzzy", "Lucky", "Nimble", "Silly", "Bold",
            "Quiet", "Restless", "Cheerful", "Solemn");

    static final List<String> COLORS = List.of(
            "Green", "Blue", "Red", "Purple", "Orange", "Yellow",
            "Teal", "Pink", "Silver", "Crimson", "Amber", "Violet");

    private final Random random;

    public SnakeNameGenerator(Random random) {
        this.random = random;
    }

    public String next() {
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String color = COLORS.get(random.nextInt(COLORS.size()));
        return adjective + " " + color + " Snake";
    }

    /**
     * Cleans up whatever the client sent: blank or missing names get one
     * generated, and the rest are trimmed to something a scoreboard can show.
     */
    public String normalise(String requested) {
        if (requested == null || requested.isBlank()) {
            return next();
        }
        String trimmed = requested.trim();
        return trimmed.length() <= MAX_NAME_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_NAME_LENGTH);
    }
}
