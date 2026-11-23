package io.github.rkaushikdev.garterhole.records;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A footnote, read.
 *
 * <p>A performance record carries a line or two under the band: a first quarter
 * for somebody, a birthday, a death. Most of them are free text and always will
 * be, but the common ones follow a shape, and recognising that shape is what
 * lets the service say "this is Alice's first quarter of Minor" without anybody
 * typing it.
 *
 * <p>Nothing here rewrites what somebody wrote. A footnote that is recognised
 * gets a structure attached to it and keeps its own words.
 *
 * @param text the footnote, with its whitespace squashed
 * @param kind what kind of footnote it is
 * @param names the names it seems to mention
 */
public record Footnote(String text, Kind kind, List<String> names) {

    /**
     * The kinds of footnote that are recognised, in the order they are tried,
     * each with the shape that gives it away.
     */
    public enum Kind {
        /** A first, of a method, a stage or a length. */
        FIRST("^(?:first|1st)\\b"),
        /** A milestone, such as a hundredth peal. */
        MILESTONE("^\\d+(?:st|nd|rd|th)\\s+(?:peal|quarter)"),
        /** Rung for a birthday. */
        BIRTHDAY("\\bbirthday\\b"),
        /** Rung in memory of somebody. */
        MEMORIAL("\\b(?:memory|memoriam|rip)\\b"),
        /** Rung for a wedding. */
        WEDDING("\\b(?:wedding|marriage)\\b"),
        /** Anything else. */
        OTHER(null);

        private final Pattern shape;

        Kind(String shape) {
            this.shape = shape == null ? null : Pattern.compile(shape, Pattern.CASE_INSENSITIVE);
        }

        private boolean fits(String line) {
            return shape != null && shape.matcher(line).find();
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Every kind of footnote, as a list that cannot be changed. */
    public static final List<Kind> KINDS = List.of(Kind.values());

    /**
     * A word that could be part of a name: a capital, then lower case, allowing
     * for the capital after an apostrophe that a name like O'Brien has.
     */
    private static final Pattern NAME_WORD = Pattern.compile("^[A-Z][a-z]*(?:'[A-Z]?[a-z]+)?$");

    private static final Pattern PUNCTUATION = Pattern.compile("[.,;!?]");

    /**
     * @param text the footnote
     * @param kind what kind of footnote it is
     * @param names the names it seems to mention
     */
    public Footnote {
        names = List.copyOf(names);
    }

    /**
     * @param text a footnote, or {@code null}
     * @return what kind of footnote it is
     */
    public static Kind kindOf(String text) {
        String line = squashed(text);
        return KINDS.stream().filter(kind -> kind.fits(line)).findFirst().orElse(Kind.OTHER);
    }

    /**
     * Read a footnote.
     *
     * @param text the footnote
     * @return its words, its kind and the names it mentions
     * @throws ValidationException when it is blank
     */
    public static Footnote readFootnote(String text) {
        String line = squashed(text);
        if (line.isEmpty()) {
            throw new ValidationException("a footnote cannot be blank");
        }
        return new Footnote(line, kindOf(line), namesIn(line));
    }

    /**
     * The names a footnote mentions, taken as the runs of capitalised words that
     * are not at the start of a sentence.
     *
     * <p>This is a guess and it is documented as one. It is right often enough to
     * be worth offering as a suggestion in a form, and it is never used to change
     * what was written.
     *
     * @param text a footnote, or {@code null}
     * @return the names, in the order they appear
     */
    public static List<String> namesIn(String text) {
        String line = squashed(text);
        List<String> names = new ArrayList<>();
        List<String> current = new ArrayList<>();
        String[] words = PUNCTUATION.matcher(line).replaceAll("").split(" ", -1);
        for (int index = 0; index < words.length; index += 1) {
            if (index > 0 && NAME_WORD.matcher(words[index]).matches()) {
                current.add(words[index]);
                continue;
            }
            if (!current.isEmpty()) {
                names.add(String.join(" ", current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            names.add(String.join(" ", current));
        }
        return List.copyOf(names);
    }

    /**
     * The footnotes of a performance, read.
     *
     * @param footnotes the lines
     * @return each one read, in order
     * @throws ValidationException when one is blank
     */
    public static List<Footnote> readAll(Iterable<String> footnotes) {
        List<Footnote> read = new ArrayList<>();
        for (String line : footnotes) {
            read.add(readFootnote(line));
        }
        return List.copyOf(read);
    }

    /**
     * Write the footnote a first would get, so that a form can offer it.
     *
     * @param name who it was a first for
     * @param what for example {@code quarter peal of Minor}
     * @return the sentence
     * @throws ValidationException when either is blank
     */
    public static String firstFootnote(String name, String what) {
        String who = squashed(name);
        String thing = squashed(what);
        if (who.isEmpty() || thing.isEmpty()) {
            throw new ValidationException("a first needs somebody and something");
        }
        return "First " + thing + " for " + who + ".";
    }

    /**
     * Write the footnote a milestone would get.
     *
     * @param name who reached it
     * @param count which one it is, for example a hundred
     * @param what for example {@code peal}
     * @return the sentence, for example {@code 100th peal for Bob Hart.}
     * @throws ValidationException when the name or the thing is blank, or the count is not one
     */
    public static String milestoneFootnote(String name, int count, String what) {
        String who = squashed(name);
        String thing = squashed(what);
        if (who.isEmpty() || thing.isEmpty() || count < 1) {
            throw new ValidationException("a milestone needs somebody, a count and something");
        }
        return ordinal(count) + " " + thing + " for " + who + ".";
    }

    /**
     * @param count a number, one or more
     * @return the number written as a board writes a place, for example {@code 21st} or {@code 112th}
     */
    public static String ordinal(int count) {
        int lastTwo = count % 100;
        if (lastTwo >= 11 && lastTwo <= 13) {
            return count + "th";
        }
        return switch (count % 10) {
            case 1 -> count + "st";
            case 2 -> count + "nd";
            case 3 -> count + "rd";
            default -> count + "th";
        };
    }

    /**
     * Group footnotes by kind, so that a report can put the firsts together.
     *
     * @param footnotes the lines
     * @return the squashed lines of each kind, kinds in the order first seen
     */
    public static Map<Kind, List<String>> groupByKind(Iterable<String> footnotes) {
        Map<Kind, List<String>> groups = new LinkedHashMap<>();
        for (Footnote entry : readAll(footnotes)) {
            groups.computeIfAbsent(entry.kind(), kind -> new ArrayList<>()).add(entry.text());
        }
        Map<Kind, List<String>> frozen = new LinkedHashMap<>();
        groups.forEach((kind, lines) -> frozen.put(kind, List.copyOf(lines)));
        return Collections.unmodifiableMap(frozen);
    }

    private static String squashed(String text) {
        return Text.squash(text == null ? "" : text);
    }
}
