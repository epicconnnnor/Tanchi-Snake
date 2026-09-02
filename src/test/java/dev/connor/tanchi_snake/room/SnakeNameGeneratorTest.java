package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SnakeNameGeneratorTest {

    @Test
    void generatedNamesAreAdjectiveColourSnake() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(1));
        for (int i = 0; i < 200; i++) {
            String name = names.next();
            String[] parts = name.split(" ");
            assertEquals(3, parts.length, name);
            assertTrue(SnakeNameGenerator.ADJECTIVES.contains(parts[0]), name);
            assertTrue(SnakeNameGenerator.COLORS.contains(parts[1]), name);
            assertEquals("Snake", parts[2], name);
        }
    }

    @Test
    void blankNamesGetOneGenerated() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(2));

        for (String blank : new String[] { null, "", "   ", "\t", "\n  " }) {
            String assigned = names.normalise(blank);
            assertTrue(assigned.endsWith(" Snake"), assigned);
            assertEquals(3, assigned.split(" ").length, assigned);
        }
    }

    @Test
    void suppliedNamesAreKeptAndTrimmed() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(3));

        assertEquals("Ann", names.normalise("Ann"));
        assertEquals("Ann", names.normalise("  Ann  "));
    }

    @Test
    void overlongNamesAreCutToTheLimit() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(4));
        String longName = "x".repeat(SnakeNameGenerator.MAX_NAME_LENGTH + 50);

        assertEquals(SnakeNameGenerator.MAX_NAME_LENGTH, names.normalise(longName).length());
    }

    @Test
    void namesNeedNotBeUnique() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(5));
        assertEquals("Ann", names.normalise("Ann"));
        assertEquals("Ann", names.normalise("Ann"));
    }

    @Test
    void generatedNamesVary() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(6));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(names.next());
        }
        assertTrue(seen.size() > 20, "expected variety, got " + seen.size());
    }

    @Test
    void isReproducibleForAGivenSeed() {
        assertEquals(new SnakeNameGenerator(new Random(42)).next(),
                new SnakeNameGenerator(new Random(42)).next());
    }
}
