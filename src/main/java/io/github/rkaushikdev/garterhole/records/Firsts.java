package io.github.rkaushikdev.garterhole.records;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The footnotes a performance has earned, worked out from what the band had
 * rung before it.
 *
 * <p>A reporter who files a quarter does not always know that it was the
 * treble ringer's first, or the conductor's hundredth, and the ringers are
 * the last people to mention it. Every one of those facts is in the records
 * already. So when a peal or a quarter is filed, the earlier records are read
 * and the footnotes it has earned are offered, worded the way a board words
 * them. They are suggestions: nothing here adds a line to a record.
 *
 * <p>Only peals and quarter peals earn footnotes; a touch is not counted.
 * Milestones are the twenty fifth, the fiftieth and every hundredth.
 */
public final class Firsts {

    private Firsts() {}

    /**
     * The footnotes a performance has earned and does not already carry, in
     * band order, the conductor's last.
     *
     * @param rung the performance being filed
     * @param earlier the records already filed; any on or after its date are not counted
     * @return the suggested lines
     * @throws ValidationException when there is no performance
     */
    public static List<String> suggestFootnotes(Performance rung, Iterable<Performance> earlier) {
        if (rung == null) {
            throw new ValidationException("a Performance is needed to suggest its footnotes");
        }
        Lengths.Kind kind = rung.kind();
        if (kind == Lengths.Kind.TOUCH) {
            return List.of();
        }
        List<Performance> before = new ArrayList<>();
        for (Performance other : earlier) {
            if (other.date().isBefore(rung.date()) && other.kind() == kind) {
                before.add(other);
            }
        }
        String what = kind.label();
        List<String> suggested = new ArrayList<>();
        for (String name : rung.band()) {
            forRinger(rung, before, name, what).ifPresent(suggested::add);
        }
        rung.conductor()
                .filter(name ->
                        before.stream().noneMatch(other -> other.conductor().equals(Optional.of(name))))
                .ifPresent(name -> suggested.add(Footnote.firstFootnote(name, what + " as conductor")));
        suggested.removeAll(rung.footnotes());
        return List.copyOf(suggested);
    }

    /**
     * @param count how many peals, or quarters, somebody has rung
     * @return true when the count is one a board marks
     */
    public static boolean isMilestone(int count) {
        return count == 25 || count == 50 || (count > 0 && count % 100 == 0);
    }

    /**
     * The one footnote a ringer has earned, the biggest first: a first of the
     * length, then a milestone, then a first of the method, then a first on
     * handbells.
     */
    private static Optional<String> forRinger(Performance rung, List<Performance> before, String name, String what) {
        List<Performance> theirs =
                before.stream().filter(other -> other.band().contains(name)).toList();
        int count = theirs.size() + 1;
        if (count == 1) {
            return Optional.of(Footnote.firstFootnote(name, what));
        }
        if (isMilestone(count)) {
            return Optional.of(Footnote.milestoneFootnote(name, count, what));
        }
        if (theirs.stream().noneMatch(other -> other.title().equals(rung.title()))) {
            return Optional.of(Footnote.firstFootnote(name, what + " of " + rung.title()));
        }
        if (rung.handbells() && theirs.stream().noneMatch(Performance::handbells)) {
            return Optional.of(Footnote.firstFootnote(name, what + " on handbells"));
        }
        return Optional.empty();
    }
}
