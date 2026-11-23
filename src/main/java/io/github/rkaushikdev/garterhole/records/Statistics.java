package io.github.rkaushikdev.garterhole.records;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Counting up what a band has rung.
 *
 * <p>Every association report opens with the same handful of numbers: how many
 * peals and quarters, at how many towers, in how many methods, and who rang the
 * most of them. None of it is hard, and all of it is fiddly enough to be worth
 * writing once.
 *
 * <p>A performance counts once for each person in the band, which is what
 * "peals rung" means to a ringer. It counts once for the tower and once for the
 * method, which is what it means to a reporter.
 */
public final class Statistics {

    private Statistics() {}

    /**
     * A set of performances added up.
     *
     * @param performances how many there were
     * @param peals how many were peals
     * @param quarters how many were quarter peals
     * @param touches how many were touches
     * @param changes how many changes they came to altogether
     */
    public record Totals(int performances, int peals, int quarters, int touches, long changes) {}

    /**
     * What one ringer rang.
     *
     * @param name the ringer
     * @param totals what they rang, added up
     * @param conducted how many of those they conducted
     * @param methods how many different method titles they rang
     */
    public record RingerLine(String name, Totals totals, int conducted, int methods) {

        /**
         * A line with no count of methods, for a ringer whose methods were not counted.
         *
         * @param name the ringer
         * @param totals what they rang
         * @param conducted how many they conducted
         */
        public RingerLine(String name, Totals totals, int conducted) {
            this(name, totals, conducted, 0);
        }
    }

    /**
     * What one tower had rung in it.
     *
     * @param tower the tower
     * @param totals what was rung there, added up
     */
    public record TowerLine(String tower, Totals totals) {}

    /**
     * How often one method was rung.
     *
     * @param title the method title
     * @param count how many performances of it there were
     */
    public record MethodCount(String title, int count) {}

    /**
     * How often one class of method was rung.
     *
     * @param className the class as a title gives it, for example {@code Surprise}
     *     or {@code Principle}, with any {@code Little} or {@code Differential}
     * @param count how many performances in it there were
     */
    public record ClassCount(String className, int count) {}

    /**
     * How often one stage was rung.
     *
     * @param stage the stage
     * @param count how many performances at it there were
     */
    public record StageCount(int stage, int count) {}

    /**
     * Add a set of performances up.
     *
     * @param records the performances
     * @return the totals
     * @throws ValidationException when an entry is not a performance
     */
    public static Totals totals(Iterable<Performance> records) {
        List<Performance> kept = collect(records);
        int peals = 0;
        int quarters = 0;
        int touches = 0;
        long changes = 0;
        for (Performance rung : kept) {
            Lengths.Kind kind = rung.kind();
            peals += kind == Lengths.Kind.PEAL ? 1 : 0;
            quarters += kind == Lengths.Kind.QUARTER_PEAL ? 1 : 0;
            touches += kind == Lengths.Kind.TOUCH ? 1 : 0;
            changes += rung.changes();
        }
        return new Totals(kept.size(), peals, quarters, touches, changes);
    }

    /**
     * What each ringer has rung, most first, and by name on a tie.
     *
     * @param records the performances
     * @return one line per ringer
     */
    public static List<RingerLine> byRinger(Iterable<Performance> records) {
        Map<String, List<Performance>> byName = new LinkedHashMap<>();
        for (Performance rung : collect(records)) {
            for (String name : rung.band()) {
                byName.computeIfAbsent(name, key -> new ArrayList<>()).add(rung);
            }
        }
        List<RingerLine> lines = new ArrayList<>();
        byName.forEach((name, theirs) -> {
            int conducted = (int) theirs.stream()
                    .filter(rung -> rung.conductor().equals(Optional.of(name)))
                    .count();
            int methods =
                    (int) theirs.stream().map(Performance::title).distinct().count();
            lines.add(new RingerLine(name, totals(theirs), conducted, methods));
        });
        return CollectionHelpers.sortBy(
                lines,
                Comparator.comparingInt((RingerLine line) -> -line.totals().performances())
                        .thenComparing(RingerLine::name));
    }

    /**
     * What each tower has had rung in it, most first, and by name on a tie.
     *
     * @param records the performances
     * @return one line per tower
     */
    public static List<TowerLine> byTower(Iterable<Performance> records) {
        Map<String, List<Performance>> byName = CollectionHelpers.groupBy(collect(records), Performance::tower);
        List<TowerLine> lines = new ArrayList<>();
        byName.forEach((tower, theirs) -> lines.add(new TowerLine(tower, totals(theirs))));
        return CollectionHelpers.sortBy(
                lines,
                Comparator.comparingInt((TowerLine line) -> -line.totals().performances())
                        .thenComparing(TowerLine::tower));
    }

    /**
     * How often each method has been rung, most first, and by title on a tie.
     *
     * @param records the performances
     * @return one line per method
     */
    public static List<MethodCount> byMethod(Iterable<Performance> records) {
        List<MethodCount> lines = new ArrayList<>();
        CollectionHelpers.countBy(collect(records), Performance::title)
                .forEach((title, count) -> lines.add(new MethodCount(title, count)));
        return CollectionHelpers.sortBy(
                lines,
                Comparator.comparingInt((MethodCount line) -> -line.count()).thenComparing(MethodCount::title));
    }

    /**
     * How often each stage has been rung, largest stage first. A performance
     * whose title cannot be read is left out rather than stopping the count.
     *
     * @param records the performances
     * @return one line per stage
     */
    public static List<StageCount> byStage(Iterable<Performance> records) {
        List<StageCount> lines = new ArrayList<>();
        CollectionHelpers.countBy(readableTitles(records), Title.Parts::stage)
                .forEach((stage, count) -> lines.add(new StageCount(stage, count)));
        return CollectionHelpers.sortBy(
                lines, Comparator.comparingInt(StageCount::stage).reversed());
    }

    /**
     * How often each class of method has been rung, most first, and by name
     * on a tie. This is the split an association's annual peal analysis is
     * printed in: so many Surprise, so many Delight, so many Plain. A
     * performance whose title cannot be read is left out.
     *
     * @param records the performances
     * @return one line per class
     */
    public static List<ClassCount> byClass(Iterable<Performance> records) {
        List<ClassCount> lines = new ArrayList<>();
        CollectionHelpers.countBy(readableTitles(records), Title.Parts::className)
                .forEach((className, count) -> lines.add(new ClassCount(className, count)));
        return CollectionHelpers.sortBy(
                lines,
                Comparator.comparingInt((ClassCount line) -> -line.count()).thenComparing(ClassCount::className));
    }

    /** The titles of the performances read apart, leaving out any that cannot be read. */
    private static List<Title.Parts> readableTitles(Iterable<Performance> records) {
        List<Title.Parts> parts = new ArrayList<>();
        for (Performance rung : collect(records)) {
            readTitle(rung.title()).ifPresent(parts::add);
        }
        return parts;
    }

    private static Optional<Title.Parts> readTitle(String title) {
        try {
            return Optional.of(Title.parse(title));
        } catch (GarterholeException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * The performances in a stretch of time.
     *
     * @param records the performances
     * @param from the first day to include
     * @param to the first day to leave out
     * @return the performances rung on or after the first day and before the second
     * @throws ValidationException when either day is missing
     */
    public static List<Performance> between(Iterable<Performance> records, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("two dates are needed to look between them");
        }
        return collect(records).stream()
                .filter(rung -> !rung.date().isBefore(from) && rung.date().isBefore(to))
                .toList();
    }

    private static List<Performance> collect(Iterable<Performance> records) {
        List<Performance> kept = new ArrayList<>();
        for (Performance rung : records) {
            if (rung == null) {
                throw new ValidationException("only performances can be counted up");
            }
            kept.add(rung);
        }
        return kept;
    }
}
