package io.github.rkaushikdev.garterhole.records;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.util.Json;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Checking a performance against the rules before it is published.
 *
 * <p>An association reporter reads several hundred of these a year and cannot
 * check them all by hand. Most of what goes wrong is mechanical: a band of seven
 * for a method on eight, a conductor who is not in the band, a peal of Minor that
 * is not a whole number of extents, a date in the future because somebody typed
 * the year wrong.
 *
 * <p>Each check gives a sentence rather than a code, because the sentence is what
 * gets sent back to the person who filed it.
 */
public final class PerformanceValidator {

    private static final double FASTEST_SECONDS_PER_CHANGE = 1;
    private static final double SLOWEST_SECONDS_PER_CHANGE = 5;

    private PerformanceValidator() {}

    /**
     * What a check found.
     *
     * @param ok true when nothing is wrong enough to refuse the record
     * @param problems what is wrong, each as a sentence
     * @param warnings what is odd but allowed, each as a sentence
     */
    public record Verdict(boolean ok, List<String> problems, List<String> warnings) {}

    /**
     * A record with something wrong with it.
     *
     * @param id the identifier of the performance
     * @param verdict what the check found
     */
    public record Finding(String id, Verdict verdict) {}

    /**
     * How a check is made.
     *
     * @param today the day the check is made on, for spotting a date in the
     *     future; the current day in UTC when not given
     * @param claim the length the record claims to be, when it claims one
     */
    public record Options(Optional<LocalDate> today, Optional<Lengths.Kind> claim) {

        /** Today's date and no claim. */
        public static final Options DEFAULT = new Options(Optional.empty(), Optional.empty());

        /**
         * @param day the day the check is made on
         * @return these options checked on that day
         */
        public Options withToday(LocalDate day) {
            return new Options(Optional.of(day), claim);
        }

        /**
         * @param kind a peal or a quarter peal
         * @return these options checking that claim
         */
        public Options withClaim(Lengths.Kind kind) {
            return new Options(today, Optional.of(kind));
        }
    }

    /**
     * How many ringers a stage needs in a tower, allowing for the cover on an odd stage.
     *
     * @param stage a number of bells
     * @return the band sizes that are acceptable
     */
    public static List<Integer> acceptableBandSizes(int stage) {
        return acceptableBandSizes(stage, false);
    }

    /**
     * How many ringers a stage needs. On handbells each ringer has a pair, so
     * Major needs four; an odd stage is rung with the cover in hand too, so
     * Triples needs four as well, and Doubles three.
     *
     * @param stage a number of bells
     * @param handbells true for a handbell band
     * @return the band sizes that are acceptable
     */
    public static List<Integer> acceptableBandSizes(int stage, boolean handbells) {
        if (handbells) {
            return List.of((Stage.bellsInTower(stage) + 1) / 2);
        }
        return Stage.hasCover(stage) ? List.of(stage, stage + 1) : List.of(stage);
    }

    /**
     * Check a performance as of today, with no claim about its length.
     *
     * @param rung the performance
     * @return what the check found
     */
    public static Verdict validatePerformance(Performance rung) {
        return validatePerformance(rung, Options.DEFAULT);
    }

    /**
     * Check a performance.
     *
     * @param rung the performance
     * @param options the day to check on and any claim about the length
     * @return what the check found
     * @throws ValidationException when there is no performance
     */
    public static Verdict validatePerformance(Performance rung, Options options) {
        if (rung == null) {
            throw new ValidationException("a Performance is needed to check it");
        }
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!Title.isWellFormed(rung.title())) {
            problems.add(Json.quote(rung.title()) + " is not a method title");
            return new Verdict(false, List.copyOf(problems), List.copyOf(warnings));
        }
        int stage = rung.stage();
        List<Integer> sizes = acceptableBandSizes(stage, rung.handbells());
        if (!sizes.contains(rung.bandSize())) {
            String allowed = sizes.stream().map(String::valueOf).collect(Collectors.joining(" or "));
            String where = rung.handbells() ? " on handbells" : "";
            problems.add(rung.title() + where + " needs a band of " + allowed + " and this one has " + rung.bandSize());
        }
        if (new HashSet<>(rung.band()).size() != rung.bandSize()) {
            problems.add("somebody is down for more than one bell");
        }
        if (rung.conductor().isEmpty()) {
            warnings.add("no conductor is recorded");
        }
        LocalDate today = options.today().orElseGet(() -> LocalDate.now(ZoneOffset.UTC));
        if (rung.date().isAfter(today)) {
            problems.add("the date " + rung.dateText() + " is in the future");
        }
        options.claim().ifPresent(claim -> Lengths.checkLength(rung.changes(), stage, claim)
                .reason()
                .ifPresent(problems::add));
        warnings.addAll(speedWarnings(rung));
        return new Verdict(problems.isEmpty(), List.copyOf(problems), List.copyOf(warnings));
    }

    /**
     * Warnings about the time a performance took. A quarter peal of Minor in
     * fifteen minutes did not happen, and a peal that took nine hours is either
     * a record or a typing mistake.
     *
     * @param rung the performance
     * @return the warnings, none when no time was recorded
     */
    public static List<String> speedWarnings(Performance rung) {
        if (rung.duration().isEmpty()) {
            return List.of();
        }
        int minutes = rung.duration().getAsInt();
        double secondsPerChange = minutes * 60.0 / rung.changes();
        List<String> warnings = new ArrayList<>();
        if (secondsPerChange < FASTEST_SECONDS_PER_CHANGE) {
            warnings.add(minutes + " minutes for " + rung.changes() + " changes is faster than anybody rings");
        }
        if (secondsPerChange > SLOWEST_SECONDS_PER_CHANGE) {
            warnings.add(minutes + " minutes for " + rung.changes() + " changes is slower than anybody rings");
        }
        return List.copyOf(warnings);
    }

    /**
     * Check a list of performances and gather everything wrong with them.
     *
     * @param performances the performances
     * @param options the day to check on
     * @return only the ones with a problem or a warning
     */
    public static List<Finding> validateAll(Iterable<Performance> performances, Options options) {
        List<Finding> found = new ArrayList<>();
        for (Performance rung : performances) {
            Verdict verdict = validatePerformance(rung, options);
            if (!verdict.ok() || !verdict.warnings().isEmpty()) {
                found.add(new Finding(rung.id(), verdict));
            }
        }
        return List.copyOf(found);
    }

    /**
     * @param verdict what a check found
     * @return a single sentence saying whether a performance passed
     */
    public static String describeVerdict(Verdict verdict) {
        if (verdict.ok() && verdict.warnings().isEmpty()) {
            return "nothing wrong with it";
        }
        if (verdict.ok()) {
            return "nothing wrong with it, but " + String.join("; ", verdict.warnings());
        }
        return String.join("; ", verdict.problems());
    }
}
