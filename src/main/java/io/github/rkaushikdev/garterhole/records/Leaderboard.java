package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.util.Text.plural;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Statistics.RingerLine;
import io.github.rkaushikdev.garterhole.records.Statistics.Totals;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.util.Text.Align;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;

/**
 * The table an association prints at the end of the year.
 *
 * <p>Counting performances is not the only way to order it, and which way is
 * chosen changes who is at the top. Somebody who rings fifty quarters is not
 * obviously ahead of somebody who rings four peals, and neither is obviously
 * ahead of somebody who rang twenty different methods, so the ordering is a
 * parameter with honest choices rather than a decision buried in a sort.
 */
public final class Leaderboard {

    /** The ways a leaderboard may be ordered, each with what it counts for a ringer. */
    public enum Ordering {
        /** Everything rung, whatever its length. */
        PERFORMANCES(line -> line.totals().performances()),
        /** Peals only. */
        PEALS(line -> line.totals().peals()),
        /** Quarter peals only. */
        QUARTERS(line -> line.totals().quarters()),
        /** Every change rung, which is the one a ringer of long touches likes. */
        CHANGES(line -> line.totals().changes()),
        /** Performances conducted. */
        CONDUCTED(RingerLine::conducted),
        /** Different methods rung, which rewards the band that learns rather than repeats. */
        METHODS(RingerLine::methods);

        private final ToLongFunction<RingerLine> score;

        Ordering(ToLongFunction<RingerLine> score) {
            this.score = score;
        }

        /**
         * @return the ordering as it is written in a request, for example {@code peals}
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * @param line what a ringer rang
         * @return what the ringer scores under this ordering
         */
        public long score(RingerLine line) {
            return score.applyAsLong(line);
        }

        /**
         * @param label an ordering as it is written in a request
         * @return the ordering
         * @throws ValidationException naming the choices when nothing has that label
         */
        public static Ordering of(String label) {
            for (Ordering ordering : values()) {
                if (ordering.label().equals(label)) {
                    return ordering;
                }
            }
            throw new ValidationException(
                    Json.quote(label) + " is not an ordering, so try one of " + String.join(", ", ORDERINGS));
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** How a leaderboard may be ordered, as the labels a request uses. */
    public static final List<String> ORDERINGS =
            Arrays.stream(Ordering.values()).map(Ordering::label).toList();

    private static final int DEFAULT_TOP = 20;

    /** Highest score first, and by name on a tie. */
    private static final Comparator<Place> BEST_FIRST =
            Comparator.comparingLong(Place::score).reversed().thenComparing(Place::name, LocaleOrder.ORDER);

    private Leaderboard() {}

    /**
     * How a leaderboard is drawn up.
     *
     * @param ordering one of {@link #ORDERINGS}
     * @param top how many places to give
     */
    public record Options(String ordering, int top) {

        /** Ordered by performances, twenty places. */
        public static final Options DEFAULT = new Options(Ordering.PERFORMANCES.label(), DEFAULT_TOP);

        /**
         * @param how one of {@link #ORDERINGS}
         * @return these options with another ordering
         */
        public Options withOrdering(String how) {
            return new Options(how, top);
        }

        /**
         * @param how an ordering
         * @return these options with that ordering
         */
        public Options withOrdering(Ordering how) {
            return new Options(how.label(), top);
        }

        /**
         * @param places how many places to give
         * @return these options with another number of places
         */
        public Options withTop(int places) {
            return new Options(ordering, places);
        }
    }

    /**
     * One place on the board.
     *
     * @param place the place, counting from one
     * @param name the ringer
     * @param score what they scored under the ordering
     * @param totals what they rang, added up
     * @param conducted how many they conducted
     */
    public record Place(int place, String name, long score, Totals totals, int conducted) {}

    /**
     * The score a ringer gets under an ordering.
     *
     * @param line what the ringer rang
     * @param ordering one of {@link #ORDERINGS}
     * @return the score
     * @throws ValidationException when the ordering is not one of them
     */
    public static long scoreOf(RingerLine line, String ordering) {
        return Ordering.of(ordering).score(line);
    }

    /**
     * The leaderboard by performances, twenty places.
     *
     * @param records the performances
     * @return the places
     */
    public static List<Place> leaderboard(Iterable<Performance> records) {
        return leaderboard(records, Options.DEFAULT);
    }

    /**
     * The leaderboard. Anybody who scored nothing is left off.
     *
     * @param records the performances
     * @param options the ordering and how many places
     * @return the places
     * @throws ValidationException when the board would have no places or the ordering is unknown
     */
    public static List<Place> leaderboard(Iterable<Performance> records, Options options) {
        if (options.top() < 1) {
            throw new ValidationException("a leaderboard of " + options.top() + " places is not one");
        }
        Ordering ordering = Ordering.of(options.ordering());
        List<Place> scored = Statistics.byRinger(records).stream()
                .map(line -> new Place(0, line.name(), ordering.score(line), line.totals(), line.conducted()))
                .filter(line -> line.score() > 0)
                .sorted(BEST_FIRST)
                .limit(options.top())
                .toList();
        List<Place> board = new ArrayList<>(scored.size());
        for (Place line : scored) {
            board.add(new Place(board.size() + 1, line.name(), line.score(), line.totals(), line.conducted()));
        }
        return List.copyOf(board);
    }

    /**
     * The leaderboard by performances, as lines of text.
     *
     * @param records the performances
     * @return the heading and the table
     */
    public static List<String> leaderboardLines(Iterable<Performance> records) {
        return leaderboardLines(records, Options.DEFAULT);
    }

    /**
     * The leaderboard as lines of text.
     *
     * @param records the performances
     * @param options the ordering and how many places
     * @return the heading and the table, or a line saying there is nothing to show
     */
    public static List<String> leaderboardLines(Iterable<Performance> records, Options options) {
        List<Place> board = leaderboard(records, options);
        if (board.isEmpty()) {
            return List.of("nobody has rung anything");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Most " + options.ordering());
        lines.addAll(Text.tabulate(
                board.stream()
                        .map(line -> List.of(String.valueOf(line.place()), line.name(), String.valueOf(line.score())))
                        .toList(),
                List.of(Align.RIGHT, Align.LEFT, Align.RIGHT)));
        return lines;
    }

    /**
     * A sentence summing the year up, which is what goes at the top of the report.
     *
     * @param records the performances
     * @return the sentence
     */
    public static String summary(Iterable<Performance> records) {
        List<Performance> kept = new ArrayList<>();
        records.forEach(kept::add);
        Totals counted = Statistics.totals(kept);
        List<Place> board = leaderboard(kept, Options.DEFAULT.withTop(1));
        if (board.isEmpty()) {
            return "nothing was rung";
        }
        return plural(counted.performances(), "performance") + " in " + plural(counted.changes(), "change")
                + ", most of them by " + board.get(0).name() + " with "
                + board.get(0).score();
    }
}
