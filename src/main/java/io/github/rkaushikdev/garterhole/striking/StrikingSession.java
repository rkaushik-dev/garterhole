package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stroke;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.striking.RowSplitter.LenientSplit;
import io.github.rkaushikdev.garterhole.striking.StrikingMetrics.Accuracy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A whole piece of ringing, analysed end to end.
 *
 * <p>This is the front door of the striking package: hand it what the recorder
 * produced and it gives back everything the other classes can work out. The
 * band is not interested in the intermediate objects, only in the answer.
 *
 * <p>The timing model is fitted to the ringing rather than assumed, because a
 * band rings at the speed it rings at and judging it against somebody else's
 * speed measures the wrong thing. A session is worked out once, when it is
 * made, and never changes afterwards.
 */
public final class StrikingSession {

    /** The threshold, in milliseconds, above which a row is worth listening to again. */
    public static final double WORST_ROWS_THRESHOLD = 50;

    private final StrikeStream stream;
    private final List<StruckRow> rows;
    private final OptionalInt stoppedAt;
    private final Optional<String> stoppedReason;
    private final TimingModel model;
    private final Thresholds thresholds;
    private final List<BlowError> errors;

    /**
     * How a session is analysed.
     *
     * @param model the model to judge against, or nothing to fit one to the ringing
     * @param thresholds how far out a blow has to be to be named as a fault
     * @param lenient true to stop at the first row that is not a permutation
     *     rather than refusing the whole recording
     */
    public record Options(Optional<TimingModel> model, Thresholds thresholds, boolean lenient) {

        /**
         * Reads a missing model as "fit one" and missing thresholds as the defaults.
         */
        public Options {
            model = model == null ? Optional.empty() : model;
            thresholds = thresholds == null ? Thresholds.DEFAULT : thresholds;
        }

        /**
         * @return a fitted model, the default thresholds, and no leniency
         */
        public static Options defaults() {
            return new Options(Optional.empty(), Thresholds.DEFAULT, false);
        }

        /**
         * @param given the model to judge against
         * @return these options with the model replaced
         */
        public Options withModel(TimingModel given) {
            return new Options(Optional.of(given), thresholds, lenient);
        }

        /**
         * @param given the thresholds faults are named by
         * @return these options with the thresholds replaced
         */
        public Options withThresholds(Thresholds given) {
            return new Options(model, given, lenient);
        }

        /**
         * @param tolerant true to stop at a bad row rather than refuse the recording
         * @return these options with the leniency replaced
         */
        public Options withLenient(boolean tolerant) {
            return new Options(model, thresholds, tolerant);
        }
    }

    /**
     * Analyse a stream with the default options.
     *
     * @param stream what the recorder heard
     * @throws ValidationException when the stream has fewer than two rows or a row is not a permutation
     */
    public StrikingSession(StrikeStream stream) {
        this(stream, Options.defaults());
    }

    /**
     * Analyse a stream.
     *
     * @param stream what the recorder heard
     * @param options how to analyse it
     * @throws ValidationException when the options or the stream are missing, the
     *     stream has fewer than two rows, or a row is not a permutation and the
     *     session is not lenient
     */
    public StrikingSession(StrikeStream stream, Options options) {
        if (options == null) {
            throw new ValidationException("a striking session needs its options");
        }
        if (stream == null) {
            throw new ValidationException("a striking session needs a StrikeStream");
        }
        LenientSplit split = options.lenient()
                ? RowSplitter.splitRowsLeniently(stream)
                : new LenientSplit(RowSplitter.splitRows(stream), OptionalInt.empty(), Optional.empty());
        if (split.rows().size() < 2) {
            throw new ValidationException("a striking session needs at least two rows to say anything");
        }
        this.stream = stream;
        this.rows = split.rows();
        this.stoppedAt = split.stoppedAt();
        this.stoppedReason = split.reason();
        this.model = options.model().orElseGet(() -> TimingModel.fit(split.rows(), stream.stage()));
        this.thresholds = options.thresholds();
        this.errors = StrikingMetrics.blowErrors(split.rows(), model);
    }

    /**
     * Build a session straight from raw strike data, sorting it into time order.
     *
     * @param stage the stage the bells were rung at
     * @param strikes the blows, in any order
     * @return the session
     */
    public static StrikingSession analyse(int stage, List<Strike> strikes) {
        return analyse(stage, strikes, Options.defaults());
    }

    /**
     * Build a session straight from raw strike data, sorting it into time order.
     *
     * @param stage the stage the bells were rung at
     * @param strikes the blows, in any order
     * @param options how to analyse it
     * @return the session
     */
    public static StrikingSession analyse(int stage, List<Strike> strikes, Options options) {
        return new StrikingSession(StrikeStream.from(stage, strikes), options);
    }

    /**
     * @return what the recorder heard
     */
    public StrikeStream stream() {
        return stream;
    }

    /**
     * @return the rows that were read
     */
    public List<StruckRow> rows() {
        return rows;
    }

    /**
     * @return how many rows were read
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * @return the index of the row a lenient session stopped at, if it stopped
     */
    public OptionalInt stoppedAt() {
        return stoppedAt;
    }

    /**
     * @return why a lenient session stopped, if it stopped
     */
    public Optional<String> stoppedReason() {
        return stoppedReason;
    }

    /**
     * @return the model the blows were judged against
     */
    public TimingModel model() {
        return model;
    }

    /**
     * @return the thresholds faults are named by
     */
    public Thresholds thresholds() {
        return thresholds;
    }

    /**
     * @return the error of every blow, in the order they were struck
     */
    public List<BlowError> errors() {
        return errors;
    }

    /**
     * @return the overall accuracy of the ringing
     */
    public Accuracy accuracy() {
        return StrikingMetrics.accuracy(errors);
    }

    /**
     * @return the accuracy of each bell, the treble first
     */
    public Map<Integer, Summary> byBell() {
        return StrikingMetrics.accuracyByBell(errors);
    }

    /**
     * @return the named faults, in the order they were rung
     */
    public List<Fault> faults() {
        return Faults.findFaults(errors, thresholds);
    }

    /**
     * @return how many faults of each kind
     */
    public Map<Fault.Kind, Integer> faultCounts() {
        return Faults.countFaults(faults());
    }

    /**
     * @return the rows worth going back and listening to, at the usual threshold
     */
    public List<Integer> worstRows() {
        return worstRows(WORST_ROWS_THRESHOLD);
    }

    /**
     * @param threshold in milliseconds
     * @return the rows whose accuracy is worse than the threshold
     */
    public List<Integer> worstRows(double threshold) {
        return StrikingMetrics.badPatches(errors, threshold);
    }

    /**
     * @return whether each row was a handstroke
     */
    public List<Boolean> strokes() {
        return RowSplitter.handstrokes(rows);
    }

    /**
     * @return the accuracy at handstroke and at backstroke
     */
    public Map<Stroke, Summary> byStroke() {
        return StrokeAccuracy.byStroke(errors);
    }

    /**
     * @return the bells struck later at one stroke than at the other
     */
    public List<StrokeAccuracy.OddStruck> oddStruck() {
        return StrokeAccuracy.oddStruck(errors);
    }

    /**
     * @return the rows the band actually rang, which the composition checker can
     *     compare against what they meant to ring
     */
    public List<Row> rungRows() {
        return rows.stream().map(StruckRow::row).toList();
    }
}
