package io.github.rkaushikdev.garterhole.records;

import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A tower's performance records, held together.
 *
 * <p>Duplicates are the thing this exists to stop. The same quarter peal gets
 * filed twice more often than anything else goes wrong with these records: once
 * by the conductor on the night and once by the secretary a week later. They are
 * caught on the identifier, which is built from the date, the tower, the method
 * and the length, so two genuinely different performances on the same night are
 * still both accepted.
 */
public final class PerformanceCollection implements Iterable<Performance> {

    /** Most recent first, and by tower within a day. */
    private static final Comparator<Performance> NEWEST_FIRST =
            Comparator.comparing(Performance::date).reversed().thenComparing(Performance::tower);

    private final Map<String, Performance> byId = new LinkedHashMap<>();

    /**
     * What filing several at once did.
     *
     * @param added the identifiers of the ones filed
     * @param alreadyFiled the identifiers of the ones that were there already
     */
    public record Filed(List<String> added, List<String> alreadyFiled) {}

    /**
     * @return how many performances are filed
     */
    public int size() {
        return byId.size();
    }

    /**
     * File a performance.
     *
     * @param rung the performance
     * @return the identifier it was filed under
     * @throws ValidationException when there is no performance
     * @throws ConflictException when it is already filed
     */
    public String add(Performance rung) {
        if (rung == null) {
            throw new ValidationException("only a Performance can be filed");
        }
        String id = rung.id();
        if (byId.containsKey(id)) {
            throw new ConflictException("this performance is already filed as " + Json.quote(id), Map.of("id", id));
        }
        byId.put(id, rung);
        return id;
    }

    /**
     * File several, skipping the ones already there rather than stopping. A
     * secretary importing a year of records wants the ones that are new, not an
     * error at the first repeat.
     *
     * @param records the performances
     * @return which were filed and which were there already
     */
    public Filed addAll(Iterable<Performance> records) {
        List<String> added = new ArrayList<>();
        List<String> alreadyFiled = new ArrayList<>();
        for (Performance rung : records) {
            if (byId.containsKey(rung.id())) {
                alreadyFiled.add(rung.id());
                continue;
            }
            added.add(add(rung));
        }
        return new Filed(List.copyOf(added), List.copyOf(alreadyFiled));
    }

    /**
     * @param id an identifier
     * @return the performance filed under it
     * @throws NotFoundException when nothing is filed under it
     */
    public Performance get(String id) {
        Performance found = byId.get(id);
        if (found == null) {
            throw new NotFoundException("performance", id);
        }
        return found;
    }

    /**
     * @param id an identifier
     * @return true when a performance is filed under it
     */
    public boolean has(String id) {
        return byId.containsKey(id);
    }

    /**
     * @param id an identifier
     * @return the performance that was filed under it
     * @throws NotFoundException when nothing is filed under it
     */
    public Performance remove(String id) {
        Performance found = get(id);
        byId.remove(id);
        return found;
    }

    /**
     * @return everything filed, most recent first
     */
    public List<Performance> all() {
        return CollectionHelpers.sortBy(byId.values(), NEWEST_FIRST);
    }

    /**
     * @return everything filed
     */
    public List<Performance> find() {
        return find(Query.ALL);
    }

    /**
     * Search the records. A tower or a title matches when it contains the words
     * asked for, whatever case they are typed in; a ringer or a conductor has to
     * be named exactly.
     *
     * @param query what to look for
     * @return the matching performances, most recent first
     */
    public List<Performance> find(Query query) {
        return all().stream().filter(query::matches).toList();
    }

    /**
     * The performance a ringer would call their first in a method.
     *
     * @param name the ringer
     * @param title the method title, or part of it
     * @return the earliest performance of it they rang in, when there is one
     */
    public Optional<Performance> firstIn(String name, String title) {
        List<Performance> found = find(Query.ALL.ringer(name).title(title));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
    }

    /**
     * @return everything filed, most recent first
     */
    @Override
    public Iterator<Performance> iterator() {
        return all().iterator();
    }

    /**
     * What to look for: a list of narrowings, each of which a performance has
     * to pass. A query with none matches everything. Each narrowing gives a new
     * query and leaves the one it narrowed as it was.
     */
    public static final class Query {

        /** A query that matches everything. */
        public static final Query ALL = new Query(List.of());

        private final List<Predicate<Performance>> narrowings;

        private Query(List<Predicate<Performance>> narrowings) {
            this.narrowings = List.copyOf(narrowings);
        }

        /**
         * @param name part of the tower name
         * @return this query, also matching on the tower
         */
        public Query tower(String name) {
            String wanted = lower(name);
            return and(rung -> rung.tower().toLowerCase(Locale.ROOT).contains(wanted));
        }

        /**
         * @param method part of the method title
         * @return this query, also matching on the method
         */
        public Query title(String method) {
            String wanted = lower(method);
            return and(rung -> rung.title().toLowerCase(Locale.ROOT).contains(wanted));
        }

        /**
         * @param name somebody who rang
         * @return this query, also matching on the band
         */
        public Query ringer(String name) {
            String wanted = Text.squash(name);
            return and(rung -> rung.bellOf(wanted).isPresent());
        }

        /**
         * @param name who conducted
         * @return this query, also matching on the conductor
         */
        public Query conductor(String name) {
            Optional<String> wanted = Optional.of(Text.squash(name));
            return and(rung -> rung.conductor().equals(wanted));
        }

        /**
         * @param length what the length counts as
         * @return this query, also matching on the kind of performance
         */
        public Query kind(Lengths.Kind length) {
            return and(rung -> rung.kind() == length);
        }

        /**
         * @param day the first day to include
         * @return this query, also matching on the date
         */
        public Query from(LocalDate day) {
            return and(rung -> !rung.date().isBefore(day));
        }

        /**
         * @param day the first day to leave out
         * @return this query, also matching on the date
         */
        public Query to(LocalDate day) {
            return and(rung -> rung.date().isBefore(day));
        }

        /**
         * @param bells a stage, for example eight for Major
         * @return this query, also matching on the stage the title gives
         */
        public Query stage(int bells) {
            return and(
                    rung -> parts(rung).filter(parts -> parts.stage() == bells).isPresent());
        }

        /**
         * The question an annual peal analysis asks, such as how many Surprise
         * quarters a tower had.
         *
         * @param wanted a class as a title gives it, for example {@code Surprise}
         * @return this query, also matching on the class of method
         */
        public Query className(String wanted) {
            return and(rung -> parts(rung)
                    .filter(parts -> parts.className().equals(wanted))
                    .isPresent());
        }

        /**
         * @param inHand true for handbell performances only, false for tower ones only
         * @return this query, also matching on where it was rung
         */
        public Query handbells(boolean inHand) {
            return and(rung -> rung.handbells() == inHand);
        }

        /**
         * @param rung a performance
         * @return true when it passes every narrowing
         */
        public boolean matches(Performance rung) {
            return narrowings.stream().allMatch(narrowing -> narrowing.test(rung));
        }

        private Query and(Predicate<Performance> narrowing) {
            List<Predicate<Performance>> more = new ArrayList<>(narrowings);
            more.add(narrowing);
            return new Query(more);
        }

        private static String lower(String text) {
            return Text.squash(text).toLowerCase(Locale.ROOT);
        }

        private static Optional<Title.Parts> parts(Performance rung) {
            return Title.isWellFormed(rung.title()) ? Optional.of(Title.parse(rung.title())) : Optional.empty();
        }
    }
}
