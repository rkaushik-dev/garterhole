package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.util.Text.joinWords;
import static io.github.rkaushikdev.garterhole.util.Text.plural;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Statistics.ClassCount;
import io.github.rkaushikdev.garterhole.records.Statistics.MethodCount;
import io.github.rkaushikdev.garterhole.records.Statistics.RingerLine;
import io.github.rkaushikdev.garterhole.records.Statistics.Totals;
import io.github.rkaushikdev.garterhole.records.Statistics.TowerLine;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.util.Text.Align;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Writing a set of performances out in whatever format is wanted.
 *
 * <p>The text report was the only one for a while, and then the association
 * asked for a spreadsheet and the website asked for JSON. Rather than growing a
 * format flag through the report code, each format is a {@link Writer} with the
 * same shape: hand it records and options, get a string back. The writers are
 * the constants of an enum, so registering a new one is one more constant.
 */
public final class Writers {

    /** The columns a spreadsheet gets, in order. */
    private static final List<String> CSV_COLUMNS =
            List.of("date", "tower", "title", "changes", "kind", "conductor", "composer", "band");

    private static final Pattern NEEDS_QUOTES = Pattern.compile("[\",\n]");

    private static final String DEFAULT_HEADING = "Performances";
    private static final int DEFAULT_TOP_RINGERS = 10;

    private Writers() {}

    /**
     * How a report is headed and how long its ringer table runs.
     *
     * @param title the heading
     * @param topRingers how many ringers the table lists
     */
    public record Options(String title, int topRingers) {

        /** Headed {@code Performances}, with ten ringers. */
        public static final Options DEFAULT = new Options(DEFAULT_HEADING, DEFAULT_TOP_RINGERS);

        /**
         * @param heading the heading
         * @return these options with another heading
         */
        public Options withTitle(String heading) {
            return new Options(heading, topRingers);
        }

        /**
         * @param count how many ringers the table lists
         * @return these options with another length of ringer table
         */
        public Options withTopRingers(int count) {
            return new Options(title, count);
        }
    }

    /** A format a set of performances can be written in. */
    public enum Writer {
        /** Plain text, as an association report is printed. */
        TEXT("text", "text/plain") {
            @Override
            public String write(List<Performance> records, Options options) {
                return String.join("\n", reportLines(records, options));
            }
        },

        /** One line per performance, as a list. */
        LIST("list", "text/plain") {
            @Override
            public String write(List<Performance> records, Options options) {
                return records.stream().map(PerformanceReport::summaryLine).collect(Collectors.joining("\n"));
            }
        },

        /** A spreadsheet, with the band in one cell separated by semicolons. */
        CSV("csv", "text/csv") {
            @Override
            public String write(List<Performance> records, Options options) {
                List<String> rows = new ArrayList<>();
                rows.add(String.join(",", CSV_COLUMNS));
                for (Performance rung : records) {
                    rows.add(List.of(
                                    rung.dateText(),
                                    rung.tower(),
                                    rung.title(),
                                    String.valueOf(rung.changes()),
                                    rung.kind().label(),
                                    rung.conductor().orElse(""),
                                    rung.composer().orElse(""),
                                    String.join("; ", rung.band()))
                            .stream()
                            .map(Writers::escapeCsv)
                            .collect(Collectors.joining(",")));
                }
                return String.join("\n", rows);
            }
        },

        /** JSON, indented, each performance written the way the store keeps it, for anything that reads it back. */
        JSON("json", "application/json") {
            @Override
            public String write(List<Performance> records, Options options) {
                ArrayNode written = Json.array();
                records.forEach(rung -> written.add(PerformanceDocument.write(rung)));
                return Json.pretty(written);
            }
        };

        private final String formatName;
        private final String contentType;

        Writer(String formatName, String contentType) {
            this.formatName = formatName;
            this.contentType = contentType;
        }

        /**
         * @return the name the format is asked for by, for example {@code csv}
         */
        public String formatName() {
            return formatName;
        }

        /**
         * @return the media type of what it writes
         */
        public String contentType() {
            return contentType;
        }

        /**
         * @param records the performances, in the order they are to be written
         * @param options the heading and the length of the ringer table, which
         *     only the text report uses
         * @return the written records
         */
        public abstract String write(List<Performance> records, Options options);

        /**
         * @param records the performances, in the order they are to be written
         * @return the written records, with the default heading
         */
        public String write(List<Performance> records) {
            return write(records, Options.DEFAULT);
        }
    }

    /**
     * The report as lines of text, with the default heading.
     *
     * @param records the performances
     * @return the lines
     */
    public static List<String> reportLines(List<Performance> records) {
        return reportLines(records, Options.DEFAULT);
    }

    /**
     * The report as lines of text: a heading, the totals, then a table each for
     * the towers, the methods, the classes and the ringers who rang most.
     *
     * @param records the performances
     * @param options the heading and how many ringers the last table lists
     * @return the lines
     */
    public static List<String> reportLines(List<Performance> records, Options options) {
        List<Performance> kept = List.copyOf(records);
        String heading = options.title();
        Totals counted = Statistics.totals(kept);
        List<String> lines = new ArrayList<>(List.of(heading, "=".repeat(heading.length()), ""));
        lines.add(plural(counted.performances(), "performance") + ": "
                + joinWords(List.of(
                        plural(counted.peals(), "peal"),
                        plural(counted.quarters(), "quarter peal"),
                        plural(counted.touches(), "touch", "touches"))));
        lines.add(counted.changes() + " changes altogether");
        section(lines, "By tower", countTable(Statistics.byTower(kept), TowerLine::tower, line -> line.totals()
                .performances()));
        section(lines, "By method", countTable(Statistics.byMethod(kept), MethodCount::title, MethodCount::count));
        section(lines, "By class", countTable(Statistics.byClass(kept), ClassCount::className, ClassCount::count));
        section(lines, "Most rung by", ringerTable(Statistics.byRinger(kept), options.topRingers()));
        return lines;
    }

    /** A blank line, a heading, and the table under it. */
    private static void section(List<String> lines, String heading, List<String> table) {
        lines.add("");
        lines.add(heading);
        lines.addAll(table);
    }

    /**
     * The ringers who rang most, as many as asked for; a negative count leaves
     * that many off the bottom instead.
     */
    private static List<String> ringerTable(List<RingerLine> ringers, int wanted) {
        int end = wanted < 0 ? ringers.size() + wanted : wanted;
        List<RingerLine> top = ringers.subList(0, Math.max(0, Math.min(ringers.size(), end)));
        return Text.tabulate(
                top.stream()
                        .map(line -> List.of(
                                line.name(),
                                String.valueOf(line.totals().performances()),
                                line.conducted() == 0 ? "" : line.conducted() + " conducted"))
                        .toList(),
                List.of(Align.LEFT, Align.RIGHT, Align.LEFT));
    }

    /**
     * A writer by name.
     *
     * @param name the name of a format
     * @return the writer for it
     * @throws ValidationException when there is no such format
     */
    public static Writer writerNamed(String name) {
        return Arrays.stream(Writer.values())
                .filter(writer -> writer.formatName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        Json.quote(name) + " is not a format, so try one of " + String.join(", ", formats())));
    }

    /**
     * @return every format that can be written, in the order they are offered
     */
    public static List<String> formats() {
        return Arrays.stream(Writer.values()).map(Writer::formatName).toList();
    }

    private static <T> List<String> countTable(
            List<T> lines, Function<T, String> nameOf, Function<T, Integer> countOf) {
        return Text.tabulate(
                lines.stream()
                        .map(line -> List.of(nameOf.apply(line), String.valueOf(countOf.apply(line))))
                        .toList(),
                List.of(Align.LEFT, Align.RIGHT));
    }

    private static String escapeCsv(String cell) {
        if (!NEEDS_QUOTES.matcher(cell).find()) {
            return cell;
        }
        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }
}
