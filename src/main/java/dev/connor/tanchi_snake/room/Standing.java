package dev.connor.tanchi_snake.room;

/**
 * One line of the results screen.
 *
 * @param rank            1-based placing
 * @param levelReachedTick tick the player last climbed to {@code level}, which
 *                        is what separates players sitting on the same level
 * @param podium          true for the top three, which the results screen
 *                        highlights
 */
public record Standing(
        int rank,
        String sessionId,
        String name,
        int level,
        int levelReachedTick,
        boolean connected,
        boolean podium) {
}
