package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
     * What to look for. Every field is optional, and a query with nothing in it
     * matches every method. Build one from {@link #all()} and narrow it.
     */
    public static final class Query {
        private static final Query ALL = new Query(null, null, null, null, null, null);

        private final Integer stage;
        private final String className;
        private final String name;
        private final Method sharesLeadHeadWith;
        private final Boolean little;
        private final Boolean differential;

        private Query(
                Integer stage,
                String className,
                String name,
                Method sharesLeadHeadWith,
                Boolean little,
                Boolean differential) {
            this.stage = stage;
            this.className = className;
            this.name = name;
            this.sharesLeadHeadWith = sharesLeadHeadWith;
            this.little = little;
            this.differential = differential;
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
            return new Query(wanted, className, name, sharesLeadHeadWith, little, differential);
        }

        /**
         * @param wanted the full class name, for example {@code Little Bob}
         * @return this query, matching only methods of that class
         */
        public Query className(String wanted) {
            return new Query(stage, wanted, name, sharesLeadHeadWith, little, differential);
        }

        /**
         * @param piece a piece of the title, matched ignoring case
         * @return this query, matching only titles that contain it
         */
        public Query name(String piece) {
            return new Query(stage, className, piece, sharesLeadHeadWith, little, differential);
        }

        /**
         * @param method a method
         * @return this query, matching only methods that finish where that one finishes
         */
        public Query sharesLeadHeadWith(Method method) {
            return new Query(stage, className, name, method, little, differential);
        }

        /**
         * @param wanted whether the method should be little
         * @return this query, matching only methods that are, or are not, little
         */
        public Query little(boolean wanted) {
            return new Query(stage, className, name, sharesLeadHeadWith, wanted, differential);
        }

        /**
         * @param wanted whether the method should be differential
         * @return this query, matching only methods that are, or are not, differential
         */
        public Query differential(boolean wanted) {
            return new Query(stage, className, name, sharesLeadHeadWith, little, wanted);
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
        Optional<String> wantedName =
                Optional.ofNullable(query.name).map(piece -> Text.squash(piece).toLowerCase(Locale.ROOT));
        Optional<String> wantedLeadHead =
                Optional.ofNullable(query.sharesLeadHeadWith).map(LeadHeads::leadHeadSignature);
        Map<Method, String> matches = new LinkedHashMap<>();
        for (Method method : library) {
            if (query.stage != null && method.stage() != query.stage) {
                continue;
            }
            Classification facts = Classifier.classify(method);
            if (query.className != null && !facts.className().equals(query.className)) {
                continue;
            }
            if (query.little != null && facts.little() != query.little) {
                continue;
            }
            if (query.differential != null && facts.differential() != query.differential) {
                continue;
            }
            String title = Title.of(method);
            if (wantedName.isPresent() && !title.toLowerCase(Locale.ROOT).contains(wantedName.get())) {
                continue;
            }
            if (wantedLeadHead.isPresent()
                    && !LeadHeads.leadHeadSignature(method).equals(wantedLeadHead.get())) {
                continue;
            }
            matches.put(method, title);
        }
        return matches.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                .toList();
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
