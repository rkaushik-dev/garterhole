package io.github.rkaushikdev.garterhole.composition.music;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Musical patterns, written the way a composer writes them in a search.
 *
 * <p>A pattern is a run of bell symbols with {@code *} standing for any number
 * of bells and {@code ?} for exactly one. {@code *5678} is any row ending in the
 * four tenors in order, {@code 56*} is any row that starts with the five and
 * the six, and {@code *8*} is any row at all on eight bells.
 *
 * <p>There is no regular expression underneath. Patterns are matched by walking
 * the row, which keeps the meaning of {@code *} obvious and stops a stray
 * character turning into a regular expression operator.
 */
public final class Patterns {

    /** Any number of bells, including none. */
    private static final char ANY_RUN = '*';

    /** Exactly one bell. */
    private static final char ANY_ONE = '?';

    private Patterns() {}

    /** One piece of a pattern. */
    public sealed interface PatternPart permits Bell, Any, Run {}

    /**
     * A particular bell.
     *
     * @param bell the bell that has to be in this place
     */
    public record Bell(int bell) implements PatternPart {}

    /** Exactly one bell, whichever it is. */
    public record Any() implements PatternPart {}

    /** Any number of bells, including none. */
    public record Run() implements PatternPart {}

    /**
     * A row that matched, and where it was.
     *
     * @param index the index of the row in the rows searched, from zero
     * @param row the row
     */
    public record Match(int index, Row row) {}

    /**
     * Read a pattern into its parts.
     *
     * @param pattern the pattern, for example {@code *5678}
     * @return the parts, in order
     * @throws ValidationException when the pattern is empty
     * @throws NotationException naming a character that is not part of a pattern
     */
    public static List<PatternPart> parsePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new ValidationException("a pattern must be a non empty string");
        }
        List<PatternPart> parts = new ArrayList<>(pattern.length());
        for (int index = 0; index < pattern.length(); index += 1) {
            char character = pattern.charAt(index);
            if (character == ANY_RUN) {
                parts.add(new Run());
            } else if (character == ANY_ONE) {
                parts.add(new Any());
            } else if (Bells.isSymbol(character)) {
                parts.add(new Bell(Bells.fromSymbol(character)));
            } else {
                throw new NotationException(
                        Json.quote(String.valueOf(character)) + " at position " + (index + 1)
                                + " is not part of a pattern",
                        pattern,
                        index + 1);
            }
        }
        return List.copyOf(parts);
    }

    /**
     * @param row a row
     * @param pattern a pattern as written
     * @return true when the row matches the pattern
     * @throws ValidationException when there is no row
     */
    public static boolean matchesPattern(Row row, String pattern) {
        requireRow(row);
        return matchesPattern(row, parsePattern(pattern));
    }

    /**
     * @param row a row
     * @param parts a pattern that has already been read
     * @return true when the row matches the pattern
     * @throws ValidationException when there is no row
     */
    public static boolean matchesPattern(Row row, List<PatternPart> parts) {
        requireRow(row);
        return matchFrom(row.toArray(), 0, parts, 0);
    }

    /**
     * @param rows the rows
     * @param pattern a pattern as written
     * @return how many of the rows match it
     */
    public static int countPattern(Iterable<Row> rows, String pattern) {
        return findPattern(rows, pattern).size();
    }

    /**
     * @param rows the rows
     * @param pattern a pattern as written
     * @return the rows that match it, with the index each one was at
     */
    public static List<Match> findPattern(Iterable<Row> rows, String pattern) {
        List<PatternPart> parts = parsePattern(pattern);
        List<Match> found = new ArrayList<>();
        int index = 0;
        for (Row row : rows) {
            if (matchesPattern(row, parts)) {
                found.add(new Match(index, row));
            }
            index += 1;
        }
        return List.copyOf(found);
    }

    /**
     * The patterns a composer usually asks for at a stage: the tenors together
     * at the back, the little bells at the front, and the four bell runs.
     *
     * @param stage a number of bells, four or more
     * @return the name of each pattern against the pattern, in a stated order
     * @throws ValidationException below four bells
     */
    public static Map<String, String> commonPatterns(int stage) {
        if (stage < 4) {
            throw new ValidationException("there are no common patterns on " + stage);
        }
        StringBuilder back = new StringBuilder();
        for (int bell = stage - 3; bell <= stage; bell += 1) {
            back.append(Bells.symbol(bell));
        }
        String backwards = new StringBuilder(back).reverse().toString();
        StringBuilder front = new StringBuilder();
        for (int bell = 1; bell <= 4; bell += 1) {
            front.append(Bells.symbol(bell));
        }
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put(back + " at the back", "*" + back);
        patterns.put(backwards + " at the back", "*" + backwards);
        patterns.put(front + " at the front", front + "*");
        patterns.put("tenors together", "*" + Bells.symbol(stage - 1) + Bells.symbol(stage));
        return Collections.unmodifiableMap(patterns);
    }

    /**
     * Match a pattern against the bells, walking both at once. A run may
     * swallow any number of bells, so it tries the shortest match first and
     * lengthens it until the rest of the pattern fits.
     */
    private static boolean matchFrom(int[] bells, int bellIndex, List<PatternPart> parts, int partIndex) {
        if (partIndex == parts.size()) {
            return bellIndex == bells.length;
        }
        PatternPart part = parts.get(partIndex);
        if (part instanceof Run) {
            for (int taken = 0; bellIndex + taken <= bells.length; taken += 1) {
                if (matchFrom(bells, bellIndex + taken, parts, partIndex + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (bellIndex >= bells.length) {
            return false;
        }
        if (part instanceof Bell bell && bell.bell() != bells[bellIndex]) {
            return false;
        }
        return matchFrom(bells, bellIndex + 1, parts, partIndex + 1);
    }

    private static void requireRow(Row row) {
        if (row == null) {
            throw new ValidationException("a Row is needed to match a pattern against");
        }
    }
}
