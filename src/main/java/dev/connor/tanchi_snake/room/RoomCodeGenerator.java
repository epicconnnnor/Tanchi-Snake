package dev.connor.tanchi_snake.room;

import java.util.Random;

/**
 * Makes the short codes players type to find a room.
 *
 * <p>The alphabet drops the characters people misread when copying a code off
 * a screen: zero and capital O, one and capital I.
 */
public class RoomCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int CODE_LENGTH = 4;

    private final Random random;

    public RoomCodeGenerator(Random random) {
        this.random = random;
    }

    public String next() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** True if the text is something this generator could have produced. */
    public static boolean isWellFormed(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
