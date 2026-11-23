package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Searching a method library. A tower librarian asks three kinds of question:
 * what have we got on eight, what Surprise do we ring, and what else finishes
 * where Cambridge finishes. All three are the same query with different fields
 * filled in.
 *
 * <p>The class and the lead head are worked out from the changes each time
 * rather than cached, which is slow enough to matter only on libraries far
 * larger than a tower keeps.
 */
public final class LibrarySearch {

    private LibrarySearch() {}

    /**
     * A method being looked at, with the facts the narrowing asks about worked
     * out once rather than once for every part of the query.
     */
    private record Candidate(Method method, Classification facts, String title) {

        static Candidate of(Method method) {
            return new Candidate(method, Classifier.classify(method), Title.of(method));
        }
    }

    /**
     * What to look for. A query is a list of narrowings, and a query with none
     * in it matches every method. Build one from {@link #all()} and narrow it;
     * each narrowing gives a new query and leaves the old one as it was.
     */
    public static final class Query {
        private static final Query ALL = new Query(List.of());

        private final List<Predicate<Candidate>> narrowings;

        private Query(List<Predicate<Candidate>> narrowings) {
            this.narrowings = List.copyOf(narrowings);
        }

        /**
         * @return the query that matches everything
         */
        public static Query all() {
            return ALL;
        }

        /**
         * @param wanted the stage
         * @return this query, matching only methods at that stage
         */
        public Query stage(int wanted) {
            return and(candidate -> candidate.method().stage() == wanted);
        }

        /**
         * @param wanted the full class name, for example {@code Little Bob}
         * @return this query, matching only methods of that class
         */
        public Query className(String wanted) {
            return and(candidate -> candidate.facts().className().equals(wanted));
        }

        /**
         * @param piece a piece of the title, matched ignoring case
         * @return this query, matching only titles that contain it
         */
        public Query name(String piece) {
            String wanted = Text.squash(piece).toLowerCase(Locale.ROOT);
            return and(candidate -> candidate.title().toLowerCase(Locale.ROOT).contains(wanted));
        }

        /**
         * @param method a method
         * @return this query, matching only methods that finish where that one finishes
         */
        public Query sharesLeadHeadWith(Method method) {
            String wanted = LeadHeads.leadHeadSignature(method);
            return and(
                    candidate -> LeadHeads.leadHeadSignature(candidate.method()).equals(wanted));
        }

        /**
         * The question a band choosing methods to splice asks first: which of
         * these are {@code b} methods?
         *
         * @param code a lead head code, for example {@code b} or {@code j1}
         * @return this query, matching only methods with that lead head code
         */
        public Query leadHeadCode(String code) {
            return and(candidate ->
                    LeadHeadCode.codeOf(candidate.method()).filter(code::equals).isPresent());
        }

        /**
         * @param wanted whether the method should be little
         * @return this query, matching only methods that are, or are not, little
         */
        public Query little(boolean wanted) {
            return and(candidate -> candidate.facts().little() == wanted);
        }

        /**
         * @param wanted whether the method should be differential
         * @return this query, matching only methods that are, or are not, differential
         */
        public Query differential(boolean wanted) {
            return and(candidate -> candidate.facts().differential() == wanted);
        }

        private Query and(Predicate<Candidate> narrowing) {
            List<Predicate<Candidate>> more = new ArrayList<>(narrowings);
            more.add(narrowing);
            return new Query(more);
        }

        private boolean matches(Candidate candidate) {
            return narrowings.stream().allMatch(narrowing -> narrowing.test(candidate));
        }
    }

    /**
     * @param library a library
     * @return every method it holds, in title order
     */
    public static List<Method> search(MethodLibrary library) {
        return search(library, Query.all());
    }

    /**
     * Search a library.
     *
     * @param library a library
     * @param query what to look for
     * @return the matches, in title order
     * @throws ValidationException when there is no library
     */
    public static List<Method> search(MethodLibrary library, Query query) {
        if (library == null) {
            throw new ValidationException("a MethodLibrary is needed to search");
        }
        List<Candidate> matches = new ArrayList<>();
        for (Method method : library) {
            Candidate candidate = Candidate.of(method);
            if (query.matches(candidate)) {
                matches.add(candidate);
            }
        }
        return matches.stream()
                .sorted(Comparator.comparing(Candidate::title))
                .map(Candidate::method)
                .toList();
    }

    /**
     * @param library a library
     * @return how many methods it holds with each lead head code, keyed by
     *     stage and code as {@code 8:b}, leaving out the methods with none
     */
    public static Map<String, Integer> countByCode(MethodLibrary library) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        LeadHeads.groupByCode(library.methods()).forEach((key, members) -> counts.put(key, members.size()));
        return Collections.unmodifiableMap(counts);
    }

    /**
     * @param library a library
     * @return how many methods it holds at each stage, ascending
     */
    public static Map<Integer, Integer> countByStage(MethodLibrary library) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (Method method : library) {
            counts.merge(method.stage(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /**
     * @param library a library
     * @return how many methods it holds in each class, in name order
     */
    public static Map<String, Integer> countByClass(MethodLibrary library) {
        Map<String, Integer> counts = new TreeMap<>(LocaleOrder.ORDER);
        for (Method method : library) {
            counts.merge(Classifier.classify(method).className(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /**
     * The methods that can be spliced with each other, grouped by lead head.
     * Groups with only one method in them are left out, since a group of one
     * splices with nothing.
     *
     * @param library a library
     * @return the groups, keyed by lead head signature
     */
    public static Map<String, List<Method>> spliceGroups(MethodLibrary library) {
        Map<String, List<Method>> groups = new LinkedHashMap<>(LeadHeads.groupByLeadHead(library.methods()));
        groups.values().removeIf(bucket -> bucket.size() < 2);
        return groups;
    }

    /**
     * @param library a library
     * @return a one line summary for the command line banner
     */
    public static String summarise(MethodLibrary library) {
        String stages = countByStage(library).entrySet().stream()
                .map(entry -> entry.getValue() + " on " + entry.getKey())
                .collect(Collectors.joining(", "));
        return stages.isEmpty() ? "no methods" : library.size() + " methods: " + stages;
    }
}
