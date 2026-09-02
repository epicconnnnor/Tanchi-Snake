package dev.connor.tanchi_snake.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    @Test
    void codesAreFourCharactersFromTheAllowedAlphabet() {
        RoomCodeGenerator codes = new RoomCodeGenerator(new Random(1));
        for (int i = 0; i < 500; i++) {
            String code = codes.next();
            assertEquals(RoomCodeGenerator.CODE_LENGTH, code.length());
            assertTrue(RoomCodeGenerator.isWellFormed(code), code);
        }
    }

    @Test
    void alphabetExcludesLookalikeCharacters() {
        for (char c : new char[] { '0', 'O', '1', 'I' }) {
            assertFalse(RoomCodeGenerator.ALPHABET.indexOf(c) >= 0,
                    "alphabet should not contain " + c);
        }
        // Uppercase letters and digits only.
        for (char c : RoomCodeGenerator.ALPHABET.toCharArray()) {
            assertTrue(Character.isUpperCase(c) || Character.isDigit(c), "bad char " + c);
        }
    }

    @Test
    void generatedCodesNeverContainLookalikes() {
        RoomCodeGenerator codes = new RoomCodeGenerator(new Random(9));
        for (int i = 0; i < 2000; i++) {
            String code = codes.next();
            for (char c : new char[] { '0', 'O', '1', 'I' }) {
                assertFalse(code.indexOf(c) >= 0, code + " contains " + c);
            }
        }
    }

    @Test
    void codesVaryAcrossTheSpace() {
        RoomCodeGenerator codes = new RoomCodeGenerator(new Random(4));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(codes.next());
        }
        // 32^4 possibilities, so 200 draws should rarely repeat.
        assertTrue(seen.size() > 190, "expected mostly distinct codes, got " + seen.size());
    }

    @Test
    void wellFormedRejectsJunk() {
        assertFalse(RoomCodeGenerator.isWellFormed(null));
        assertFalse(RoomCodeGenerator.isWellFormed(""));
        assertFalse(RoomCodeGenerator.isWellFormed("ABC"));
        assertFalse(RoomCodeGenerator.isWellFormed("ABCDE"));
        assertFalse(RoomCodeGenerator.isWellFormed("ABC0"));
        assertFalse(RoomCodeGenerator.isWellFormed("abcd"));
        assertTrue(RoomCodeGenerator.isWellFormed("ABCD"));
    }

    @Test
    void isReproducibleForAGivenSeed() {
        assertEquals(new RoomCodeGenerator(new Random(77)).next(),
                new RoomCodeGenerator(new Random(77)).next());
    }
}
