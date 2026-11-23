package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stroke;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Turning a stream of blows into rows.
 *
 * <p>The bells strike in order, so every run of {@code stage} blows is a row
 * and the order they struck in is the row itself. That is the whole idea, and
 * it works as long as nobody drops a blow. When somebody does, the run of blows
 * is no longer a permutation, and saying so at once is far better than carrying
 * on and reporting every later row as badly struck.
 */
public final class RowSplitter {

    private RowSplitter() {}

    /**
     * What a lenient split gives back: every row it could read, and where and
     * why it stopped when it could not read one.
     *
     * @param rows the rows before the first bad one
     * @param stoppedAt the index of the first row that was not a permutation, if any
     * @param reason what was wrong with that row, if any
     */
    public record LenientSplit(List<StruckRow> rows, OptionalInt stoppedAt, Optional<String> reason) {

        /**
         * Copies the rows, so that the split cannot change after it is made.
         */
        public LenientSplit {
            rows = List.copyOf(rows);
        }
    }

    /**
     * Split a stream into rows, leaving out a part row at the end.
     *
     * @param stream the stream
     * @return the rows, in order
     * @throws ValidationException when there is no stream, or a run of blows is not a permutation
     */
    public static List<StruckRow> splitRows(StrikeStream stream) {
        requireStream(stream);
        int stage = stream.stage();
        List<StruckRow> rows = new ArrayList<>(stream.length() / stage);
        for (int start = 0; start + stage <= stream.length(); start += stage) {
            rows.add(struckRow(stream.strikes().subList(start, start + stage), start / stage));
        }
        return List.copyOf(rows);
    }

    /**
     * How many blows are left over at the end of a stream, which is a partial
     * row the analysis should ignore.
     *
     * @param stream the stream
     * @return the blows of the part row
     */
    public static int trailingBlows(StrikeStream stream) {
        return stream.length() % stream.stage();
    }

    /**
     * Split a stream and report the first row that is not a permutation instead
     * of throwing, so that a recorder with one bad row still gives an analysis
     * of everything before it.
     *
     * @param stream the stream
     * @return the rows that could be read, and where reading stopped
     * @throws ValidationException when there is no stream
     */
    public static LenientSplit splitRowsLeniently(StrikeStream stream) {
        requireStream(stream);
        int stage = stream.stage();
        List<StruckRow> rows = new ArrayList<>();
        for (int start = 0; start + stage <= stream.length(); start += stage) {
            int index = start / stage;
            try {
                rows.add(struckRow(stream.strikes().subList(start, start + stage), index));
            } catch (ValidationException notAPermutation) {
                return new LenientSplit(rows, OptionalInt.of(index), Optional.of(notAPermutation.getMessage()));
            }
        }
        return new LenientSplit(rows, OptionalInt.empty(), Optional.empty());
    }

    /**
     * The time each row took, from its first blow to the first blow of the next.
     *
     * @param rows the struck rows
     * @return one entry shorter than the rows, since the last has no next
     */
    public static List<Double> rowDurations(List<StruckRow> rows) {
        List<Double> durations = new ArrayList<>(Math.max(0, rows.size() - 1));
        for (int index = 1; index < rows.size(); index += 1) {
            durations.add(rows.get(index).start() - rows.get(index - 1).start());
        }
        return durations;
    }

    /**
     * Whether each row was struck at a handstroke, given that the first row of a
     * touch is. Handstroke and backstroke alternate all the way through.
     *
     * @param rows the struck rows
     * @return true for each handstroke row
     */
    public static List<Boolean> handstrokes(List<StruckRow> rows) {
        return strokes(rows).stream().map(stroke -> stroke == Stroke.HANDSTROKE).toList();
    }

    /**
     * The stroke of each row, given that the first row of a touch is a handstroke.
     *
     * @param rows the struck rows
     * @return the stroke of each, in order
     */
    public static List<Stroke> strokes(List<StruckRow> rows) {
        return rows.stream().map(row -> StrokeAccuracy.strokeOf(row.index())).toList();
    }

    private static StruckRow struckRow(List<Strike> strikes, int index) {
        return new StruckRow(
                rowFrom(strikes, index),
                index,
                strikes,
                strikes.get(0).at(),
                strikes.get(strikes.size() - 1).at());
    }

    private static Row rowFrom(List<Strike> strikes, int index) {
        int[] bells = strikes.stream().mapToInt(Strike::bell).toArray();
        try {
            return new Row(bells);
        } catch (IllegalArgumentException notAPermutation) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("index", index);
            details.put("bells", strikes.stream().map(Strike::bell).toList());
            throw new ValidationException(
                    "row " + index + " is not a permutation: " + notAPermutation.getMessage(), details);
        }
    }

    private static void requireStream(StrikeStream stream) {
        if (stream == null) {
            throw new ValidationException("a StrikeStream is needed to split it into rows");
        }
    }
}
