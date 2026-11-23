package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Stroke;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Striking at handstroke and at backstroke, taken separately.
 *
 * <p>A band whose handstrokes are good and whose backstrokes are ragged needs
 * different advice from one that is middling at both, and a single accuracy
 * figure hides the difference. The commonest form of it in one ringer is
 * being odd struck: a bell that is consistently later at one stroke than at
 * the other, which the band hears as a limp in the rhythm however accurate
 * the bell is on average.
 *
 * <p>The first row of a recording is taken to be a handstroke, as the splitter
 * takes it.
 */
public final class StrokeAccuracy {

    /** How far apart a bell's two strokes have to be, on average, before it is called odd struck. */
    public static final double ODD_STRUCK_THRESHOLD = 20;

    private StrokeAccuracy() {}

    /**
     * A bell that is struck later at one stroke than at the other.
     *
     * @param bell the bell
     * @param handstrokeMean its mean error at handstroke, in milliseconds
     * @param backstrokeMean its mean error at backstroke, in milliseconds
     */
    public record OddStruck(int bell, double handstrokeMean, double backstrokeMean) {

        /**
         * @return how much later the handstroke is than the backstroke, negative when it is earlier
         */
        public double difference() {
            return handstrokeMean - backstrokeMean;
        }

        /**
         * @return the stroke that is the later of the two
         */
        public Stroke lateStroke() {
            return difference() > 0 ? Stroke.HANDSTROKE : Stroke.BACKSTROKE;
        }
    }

    /**
     * @param rowIndex a row of the recording, counted from nought
     * @return the stroke it was struck at
     */
    public static Stroke strokeOf(int rowIndex) {
        return Stroke.at(rowIndex, Stroke.HANDSTROKE);
    }

    /**
     * @param errors the blow errors
     * @return the accuracy at each stroke, handstroke first, leaving out a stroke with no blows
     */
    public static Map<Stroke, Summary> byStroke(List<BlowError> errors) {
        Map<Stroke, ErrorAccumulator<Void>> sums = new EnumMap<>(Stroke.class);
        for (BlowError entry : errors) {
            sums.computeIfAbsent(strokeOf(entry.rowIndex()), stroke -> new ErrorAccumulator<>())
                    .add(entry.error());
        }
        Map<Stroke, Summary> summaries = new EnumMap<>(Stroke.class);
        sums.forEach((stroke, sum) -> summaries.put(stroke, sum.summary()));
        return Collections.unmodifiableMap(summaries);
    }

    /**
     * The bells that are odd struck by more than the usual margin.
     *
     * @param errors the blow errors
     * @return the odd struck bells, the treble first
     */
    public static List<OddStruck> oddStruck(List<BlowError> errors) {
        return oddStruck(errors, ODD_STRUCK_THRESHOLD);
    }

    /**
     * The bells whose mean error at one stroke is further than a margin from
     * their mean error at the other. A bell rung at only one stroke is never
     * counted.
     *
     * @param errors the blow errors
     * @param threshold the margin in milliseconds
     * @return the odd struck bells, the treble first
     * @throws ValidationException when the margin is negative
     */
    public static List<OddStruck> oddStruck(List<BlowError> errors, double threshold) {
        if (threshold < 0) {
            throw new ValidationException(Json.number(threshold) + " is not a margin in milliseconds");
        }
        Map<Integer, Map<Stroke, ErrorAccumulator<Void>>> byBell = new TreeMap<>();
        for (BlowError entry : errors) {
            byBell.computeIfAbsent(entry.bell(), bell -> new EnumMap<>(Stroke.class))
                    .computeIfAbsent(strokeOf(entry.rowIndex()), stroke -> new ErrorAccumulator<>())
                    .add(entry.error());
        }
        List<OddStruck> found = new ArrayList<>();
        byBell.forEach((bell, strokes) -> {
            if (strokes.size() < 2) {
                return;
            }
            OddStruck bellStrokes = new OddStruck(
                    bell,
                    strokes.get(Stroke.HANDSTROKE).meanError(),
                    strokes.get(Stroke.BACKSTROKE).meanError());
            if (Math.abs(bellStrokes.difference()) > threshold) {
                found.add(bellStrokes);
            }
        });
        return List.copyOf(found);
    }
}
