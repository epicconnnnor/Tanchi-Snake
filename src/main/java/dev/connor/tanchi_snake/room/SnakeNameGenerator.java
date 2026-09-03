package dev.connor.tanchi_snake.room;

import java.util.List;
import java.util.Random;

/**
 * Names players who did not pick one, in the shape "Cheerful Viper".
 *
 * <p>No colour word goes in a name. Every player is already given a colour by
 * the room, and a name that claimed a different one was worse than no name at
 * all: "Bold Violet Snake" drawn in green reads as a bug.
 *
 * <p>Names are not required to be unique, so no collision handling here.
 */
public class SnakeNameGenerator {

    /** Longest name a player is allowed to set; anything longer is trimmed. */
    public static final int MAX_NAME_LENGTH = 20;

    /**
     * Longest name this class will hand out. The scoreboard column is narrow
     * and its type is no longer small, so a generated name has to fit it
     * rather than be clipped down to something ending in an ellipsis.
     */
    public static final int MAX_GENERATED_LENGTH = 18;

    static final List<String> ADJECTIVES = List.of(
            "Cheerful", "Restless", "Grumpy", "Anxious", "Bold",
            "Sleepy", "Curious", "Stubborn", "Gentle", "Reckless",
            "Jolly", "Solemn", "Timid", "Brave", "Clever",
            "Sneaky", "Patient", "Cranky", "Eager", "Placid",
            "Wary", "Smug", "Dreamy", "Frantic", "Serene",
            "Moody", "Prickly", "Sullen", "Bashful", "Jaunty");

    private static final int LONGEST_ADJECTIVE =
            ADJECTIVES.stream().mapToInt(String::length).max().orElse(0);

    /**
     * Real snakes whose common name stands on its own. Nothing here reads as
     * a colour, and nothing needs "Snake" tacked on the end to make sense.
     */
    private static final List<String> SPECIES_POOL = List.of(
            "Viper", "Python", "Cobra", "Adder", "Mamba",
            "Boa", "Krait", "Taipan", "Rattler", "Anaconda",
            "Asp", "Racer", "Garter", "Hognose", "Boomslang",
            "Ratsnake", "Kingsnake", "Bullsnake", "Coachwhip", "Keelback",
            "Whipsnake", "Moccasin", "Sea Krait", "Pit Viper", "Sand Boa",
            "Tree Boa", "Habu", "Rinkhals", "Mussurana", "Cantil");

    /** Every pairing of these with an adjective is safe to hand out. */
    static final List<String> SPECIES = withinCap(SPECIES_POOL, LONGEST_ADJECTIVE);

    /**
     * The species short enough to sit beside the longest adjective without
     * passing {@link #MAX_GENERATED_LENGTH}. Anything longer is dropped here
     * rather than left to produce a name the scoreboard would cut short.
     */
    static List<String> withinCap(List<String> pool, int longestAdjective) {
        return pool.stream()
                .filter(s -> longestAdjective + 1 + s.length() <= MAX_GENERATED_LENGTH)
                .toList();
    }

    private final Random random;

    public SnakeNameGenerator(Random random) {
        this.random = random;
    }

    public String next() {
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String species = SPECIES.get(random.nextInt(SPECIES.size()));
        return adjective + " " + species;
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
