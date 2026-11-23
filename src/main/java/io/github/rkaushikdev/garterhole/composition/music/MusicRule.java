package io.github.rkaushikdev.garterhole.composition.music;

import io.github.rkaushikdev.garterhole.core.Stroke;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * One rule of a music scheme: a name, a weight, and the one thing it counts,
 * which is a pattern, a run length or a named row.
 *
 * <p>A scheme is data rather than code, so a rule can arrive incomplete from a
 * scheme somebody wrote by hand. The scorer refuses such a rule when it is
 * applied, naming it, rather than this record refusing to hold it.
 *
 * <p>A rule may be tied to one stroke. Rows rung at backstroke carry further
 * than the same rows at handstroke, so a scheme can weight a backstroke run
 * above a handstroke one, or count the tenors reversed only where it is heard.
 *
 * @param name what the rule is called on a report
 * @param weight how much each row it counts is worth
 * @param pattern a pattern to count, as {@link Patterns} reads it
 * @param runLength the shortest run to count, as {@link Runs} counts it
 * @param namedRow the name of a row to count, as {@code NamedRows} names it
 * @param stroke the stroke it counts at, or nothing for both
 */
public record MusicRule(
        String name,
        OptionalDouble weight,
        Optional<String> pattern,
        OptionalInt runLength,
        Optional<String> namedRow,
        Optional<Stroke> stroke) {

    /**
     * A rule that counts at either stroke.
     *
     * @param name what the rule is called on a report
     * @param weight how much each row it counts is worth
     * @param pattern a pattern to count
     * @param runLength the shortest run to count
     * @param namedRow the name of a row to count
     */
    public MusicRule(
            String name,
            OptionalDouble weight,
            Optional<String> pattern,
            OptionalInt runLength,
            Optional<String> namedRow) {
        this(name, weight, pattern, runLength, namedRow, Optional.empty());
    }

    /**
     * @param only the stroke to count at
     * @return this rule counting only the rows rung at that stroke
     */
    public MusicRule atStroke(Stroke only) {
        return new MusicRule(name, weight, pattern, runLength, namedRow, Optional.of(only));
    }

    /**
     * @param name what the rule is called
     * @param pattern the pattern
     * @param weight what each matching row is worth
     * @return a rule that counts rows matching a pattern
     */
    public static MusicRule pattern(String name, String pattern, double weight) {
        return new MusicRule(
                name, OptionalDouble.of(weight), Optional.of(pattern), OptionalInt.empty(), Optional.empty());
    }

    /**
     * @param name what the rule is called
     * @param length the shortest run counted
     * @param weight what each row with a run is worth
     * @return a rule that counts rows with a run at either end
     */
    public static MusicRule runs(String name, int length, double weight) {
        return new MusicRule(
                name, OptionalDouble.of(weight), Optional.empty(), OptionalInt.of(length), Optional.empty());
    }

    /**
     * @param name what the rule is called
     * @param row the name of the row, for example {@code Queens}
     * @param weight what each time it is rung is worth
     * @return a rule that counts a named row
     */
    public static MusicRule namedRow(String name, String row, double weight) {
        return new MusicRule(name, OptionalDouble.of(weight), Optional.empty(), OptionalInt.empty(), Optional.of(row));
    }
}
