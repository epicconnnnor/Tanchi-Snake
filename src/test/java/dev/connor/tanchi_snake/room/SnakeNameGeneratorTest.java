package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SnakeNameGeneratorTest {

    /** Splits on the first space: the adjective is one word, the species may be two. */
    private static String[] split(String name) {
        int cut = name.indexOf(' ');
        return new String[] { name.substring(0, cut), name.substring(cut + 1) };
    }

    @Test
    void generatedNamesAreAdjectiveThenSpecies() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(1));
        for (int i = 0; i < 200; i++) {
            String name = names.next();
            String[] parts = split(name);
            assertTrue(SnakeNameGenerator.ADJECTIVES.contains(parts[0]), name);
            assertTrue(SnakeNameGenerator.SPECIES.contains(parts[1]), name);
        }
    }

    /*
     * The colour word is the whole reason this format changed: a player is
     * given a colour by the room, and a name claiming a different one made
     * the board look broken.
     */
    @Test
    void noNamePartIsAColour() {
        Set<String> colours = Set.of(
                "Green", "Blue", "Red", "Purple", "Orange", "Yellow",
                "Teal", "Pink", "Silver", "Crimson", "Amber", "Violet");
        for (String adjective : SnakeNameGenerator.ADJECTIVES) {
            assertFalse(colours.contains(adjective), adjective);
        }
        for (String species : SnakeNameGenerator.SPECIES) {
            for (String word : species.split(" ")) {
                assertFalse(colours.contains(word), species);
            }
        }
    }

    @Test
    void nothingEndsInSnakeAnyMore() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(7));
        for (int i = 0; i < 200; i++) {
            String name = names.next();
            assertFalse(name.endsWith(" Snake"), "the species already says snake: " + name);
            assertTrue(name.contains(" "), "a name is an adjective and a species: " + name);
        }
    }

    /** Every pairing has to fit, not just the ones a seed happens to pick. */
    @Test
    void noPairingCanExceedTheGeneratedLimit() {
        for (String adjective : SnakeNameGenerator.ADJECTIVES) {
            for (String species : SnakeNameGenerator.SPECIES) {
                String name = adjective + " " + species;
                assertTrue(name.length() <= SnakeNameGenerator.MAX_GENERATED_LENGTH,
                        name + " is " + name.length() + " characters");
            }
        }
    }

    @Test
    void speciesTooLongForTheLongestAdjectiveAreDropped() {
        int longestAdjective = SnakeNameGenerator.ADJECTIVES.stream()
                .mapToInt(String::length).max().orElseThrow();
        int room = SnakeNameGenerator.MAX_GENERATED_LENGTH - longestAdjective - 1;

        for (String species : SnakeNameGenerator.SPECIES) {
            assertTrue(species.length() <= room,
                    species + " cannot sit beside a " + longestAdjective + " character adjective");
        }
    }

    @Test
    void aSpeciesTooLongForTheCapIsDropped() {
        // Beside an eight character adjective and a space, nine are left.
        assertEquals(java.util.List.of("Viper", "Boa"),
                SnakeNameGenerator.withinCap(
                        java.util.List.of("Viper", "Sidewinder", "Copperhead", "Boa"), 8));
    }

    @Test
    void aShorterAdjectiveLeavesRoomForMore() {
        // Drop the longest adjective to seven and the ten character species fit.
        assertEquals(java.util.List.of("Viper", "Sidewinder", "Copperhead", "Boa"),
                SnakeNameGenerator.withinCap(
                        java.util.List.of("Viper", "Sidewinder", "Copperhead", "Boa"), 7));
    }

    @Test
    void thereAreEnoughOfBothToGoRound() {
        assertTrue(SnakeNameGenerator.ADJECTIVES.size() >= 25,
                "adjectives: " + SnakeNameGenerator.ADJECTIVES.size());
        assertTrue(SnakeNameGenerator.SPECIES.size() >= 25,
                "species: " + SnakeNameGenerator.SPECIES.size());
        assertEquals(SnakeNameGenerator.ADJECTIVES.size(),
                new HashSet<>(SnakeNameGenerator.ADJECTIVES).size(), "duplicate adjective");
        assertEquals(SnakeNameGenerator.SPECIES.size(),
                new HashSet<>(SnakeNameGenerator.SPECIES).size(), "duplicate species");
    }

    @Test
    void blankNamesGetOneGenerated() {
        SnakeNameGenerator names = new SnakeNameGenerator(new Random(2));

        for (String blank : new String[] { null, "", "   ", "\t", "\n  " }) {
            String assigned = names.normalise(blank);
            String[] parts = split(assigned);
            assertTrue(SnakeNameGenerator.ADJECTIVES.contains(parts[0]), assigned);
            assertTrue(SnakeNameGenerator.SPECIES.contains(parts[1]), assigned);
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
