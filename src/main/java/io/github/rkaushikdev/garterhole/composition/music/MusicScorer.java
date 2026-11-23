package io.github.rkaushikdev.garterhole.composition.music;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.music.Runs.RunCount;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.core.NamedRows;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stroke;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scoring the music in a composition.
 *
 * <p>There is no single right scoring. Every band weights the music it likes,
 * and two compositions that score the same under one scheme will not under
 * another. So the scheme is data rather than code: a list of named
 * {@link MusicRule}s, each with a pattern, a run length or a named row and a
 * weight, and the scorer applies whatever it is handed.
 *
 * <p>The default scheme is the one most peal boards would recognise, with four
 * bell runs weighted one and the whole row of tenors weighted more.
 */
public final class MusicScorer {

    private MusicScorer() {}

    /**
     * One line of a score.
     *
     * @param name the rule
     * @param count how many times it fired
     * @param weight what each time is worth
     * @param score the count times the weight
     */
    public record ScoreLine(String name, int count, double weight, double score) {}

    /**
     * A block scored against a scheme.
     *
     * @param total the sum of the lines
     * @param lines one line per rule, in the order of the scheme
     */
    public record Score(double total, List<ScoreLine> lines) {}

    /**
     * The runs of one length, for a summary.
     *
     * @param length the shortest run counted
     * @param front rows with the run at the front
     * @param back rows with the run at the back
     * @param total rows with the run at either end
     */
    public record RunLine(int length, int front, int back, int total) {}

    /**
     * How many times a named row was rung.
     *
     * @param name the name of the row
     * @param count how many times
     */
    public record NamedCount(String name, int count) {}

    /**
     * The music in a block, as a report writer wants it.
     *
     * @param score the total under the default scheme
     * @param runs the run table, shortest runs first
     * @param named the named rows rung, rounds left out, in the order first rung
     */
    public record MusicSummary(double score, List<RunLine> runs, List<NamedCount> named) {

        /**
         * @return the summary as a document, with the field names a report expects
         */
        public ObjectNode toJson() {
            ObjectNode json = Json.object();
            Json.putNumber(json, "score", score);
            ArrayNode runLines = json.putArray("runs");
            for (RunLine run : runs) {
                runLines.addObject()
                        .put("length", run.length())
                        .put("front", run.front())
                        .put("back", run.back())
                        .put("total", run.total());
            }
            ArrayNode namedRows = json.putArray("named");
            for (NamedCount entry : named) {
                namedRows.addObject().put("name", entry.name()).put("count", entry.count());
            }
            return json;
        }
    }

    /**
     * The scheme used when none is given.
     *
     * @param stage a number of bells, four or more
     * @return the rules, leaving out runs longer than the stage
     * @throws ValidationException below four bells
     */
    public static List<MusicRule> defaultScheme(int stage) {
        if (stage < 4) {
            throw new ValidationException("there is no default music scheme on " + stage);
        }
        List<MusicRule> rules = List.of(
                MusicRule.runs("four bell runs", 4, 1),
                MusicRule.runs("five bell runs", 5, 2),
                MusicRule.runs("six bell runs", 6, 3),
                MusicRule.namedRow("queens", "Queens", 5),
                MusicRule.namedRow("backrounds", "Backrounds", 5),
                MusicRule.namedRow("tittums", "Tittums", 5));
        return rules.stream()
                .filter(rule -> rule.runLength().isEmpty() || rule.runLength().getAsInt() <= stage)
                .toList();
    }

    /**
     * Score a block of rows against the default scheme for its stage.
     *
     * @param rows the rows
     * @param stage the stage they are at
     * @return the score
     */
    public static Score scoreMusic(Iterable<Row> rows, int stage) {
        return scoreMusic(rows, stage, defaultScheme(stage));
    }

    /**
     * Score a block of rows against a scheme.
     *
     * @param rows the rows
     * @param stage the stage they are at
     * @param scheme the rules to apply
     * @return the score
     */
    public static Score scoreMusic(Iterable<Row> rows, int stage, List<MusicRule> scheme) {
        List<Row> kept = listOf(rows);
        List<ScoreLine> lines = new ArrayList<>(scheme.size());
        double total = 0;
        for (MusicRule rule : scheme) {
            int count = countRule(kept, rule);
            double weight = rule.weight().orElseThrow();
            double score = count * weight;
            total += score;
            lines.add(new ScoreLine(rule.name(), count, weight, score));
        }
        return new Score(total, List.copyOf(lines));
    }

    /**
     * How many times a single rule fires over a block. The rows are taken to
     * start with the rounds a touch is rung from, which is a backstroke, so the
     * first change is a handstroke; a rule tied to a stroke counts only the
     * rows at that stroke.
     *
     * @param rows the rows
     * @param rule the rule
     * @return how many rows it counts
     * @throws ValidationException when the rule is missing, has no finite weight or counts nothing
     */
    public static int countRule(List<Row> rows, MusicRule rule) {
        if (rule == null) {
            throw new ValidationException("a music rule must be an object");
        }
        if (rule.weight().isEmpty() || !Double.isFinite(rule.weight().getAsDouble())) {
            throw new ValidationException("the rule " + Json.quote(rule.name()) + " has no weight");
        }
        List<Row> counted = rule.stroke().map(only -> atStroke(rows, only)).orElse(rows);
        if (rule.pattern().isPresent()) {
            return Patterns.countPattern(counted, rule.pattern().get());
        }
        if (rule.runLength().isPresent()) {
            return Runs.countRuns(counted, rule.runLength().getAsInt()).total();
        }
        if (rule.namedRow().isPresent()) {
            String wanted = rule.namedRow().get();
            RowNames names = new RowNames();
            return (int) counted.stream()
                    .filter(row -> names.nameOf(row).filter(wanted::equals).isPresent())
                    .count();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", rule.name());
        throw new ValidationException("the rule " + Json.quote(rule.name()) + " has nothing to count", details);
    }

    /**
     * The rows rung at one stroke, the first row being the backstroke rounds a
     * touch starts from.
     *
     * @param rows the rows
     * @param stroke the stroke wanted
     * @return the rows at that stroke, in order
     */
    public static List<Row> atStroke(List<Row> rows, Stroke stroke) {
        List<Row> kept = new ArrayList<>();
        for (int index = 0; index < rows.size(); index += 1) {
            if (Stroke.at(index, Stroke.BACKSTROKE) == stroke) {
                kept.add(rows.get(index));
            }
        }
        return kept;
    }

    /**
     * The scheme a composer scores a peal composition against: the default
     * scheme, the combination roll ups on Major, and a point off for every row
     * with the tenors reversed at the back at backstroke, which is the one
     * blemish everybody outside hears.
     *
     * <p>The roll ups are the rows ending in two of the four, five and six
     * followed by the seven and the eight, {@code 4578}, {@code 5478},
     * {@code 4678} and {@code 6478}. The {@code 5678} and {@code 6578} endings
     * are left to the runs and the default scheme.
     *
     * @param stage a number of bells, six or more
     * @return the rules
     * @throws ValidationException below six bells
     */
    public static List<MusicRule> compositionScheme(int stage) {
        if (stage < 6) {
            throw new ValidationException("there is no composition scheme on " + stage);
        }
        List<MusicRule> rules = new ArrayList<>(defaultScheme(stage));
        if (stage == 8) {
            for (String ending : List.of("4578", "5478", "4678", "6478")) {
                rules.add(MusicRule.pattern("roll up " + ending, "*" + ending, 1));
            }
        }
        String reversed = "" + Bells.symbol(stage) + Bells.symbol(stage - 1);
        rules.add(MusicRule.pattern(reversed + " at backstroke", "*" + reversed, -1)
                .atStroke(Stroke.BACKSTROKE));
        return List.copyOf(rules);
    }

    /**
     * A summary of the music in a block, as a report writer wants it.
     *
     * @param rows the rows
     * @param stage the stage they are at
     * @return the score, the run table and the named rows rung
     */
    public static MusicSummary musicSummary(Iterable<Row> rows, int stage) {
        List<Row> kept = listOf(rows);
        double total = scoreMusic(kept, stage).total();
        List<RunLine> runs = new ArrayList<>();
        for (Map.Entry<Integer, RunCount> entry : Runs.runTable(kept, stage).entrySet()) {
            RunCount counts = entry.getValue();
            runs.add(new RunLine(entry.getKey(), counts.front(), counts.back(), counts.total()));
        }
        RowNames names = new RowNames();
        Map<String, Integer> named = new LinkedHashMap<>();
        for (Row row : kept) {
            names.nameOf(row)
                    .filter(name -> !name.equals("Rounds"))
                    .ifPresent(name -> named.merge(name, 1, Integer::sum));
        }
        List<NamedCount> namedCounts = named.entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .toList();
        return new MusicSummary(total, List.copyOf(runs), namedCounts);
    }

    /**
     * Compare two blocks on the default scheme for their stage.
     *
     * @param left one block
     * @param right another
     * @param stage the stage they are at
     * @return positive when the left one is more musical
     */
    public static double compareMusic(Iterable<Row> left, Iterable<Row> right, int stage) {
        return compareMusic(left, right, stage, defaultScheme(stage));
    }

    /**
     * Compare two blocks on the same scheme.
     *
     * @param left one block
     * @param right another
     * @param stage the stage they are at
     * @param scheme the rules to apply
     * @return positive when the left one is more musical
     */
    public static double compareMusic(Iterable<Row> left, Iterable<Row> right, int stage, List<MusicRule> scheme) {
        return scoreMusic(left, stage, scheme).total()
                - scoreMusic(right, stage, scheme).total();
    }

    private static List<Row> listOf(Iterable<Row> rows) {
        List<Row> kept = new ArrayList<>();
        rows.forEach(kept::add);
        return kept;
    }

    /**
     * The name of each named row, worked out once per stage rather than once per
     * row. Where two names give the same row the one listed first wins, as it
     * does in {@link NamedRows#nameOfRow}.
     */
    private static final class RowNames {
        private final Map<Integer, Map<Row, String>> byStage = new HashMap<>();

        Optional<String> nameOf(Row row) {
            Map<Row, String> names = byStage.computeIfAbsent(row.stage(), stage -> {
                Map<Row, String> inverted = new HashMap<>();
                NamedRows.namedRows(stage).forEach((name, named) -> inverted.putIfAbsent(named, name));
                return inverted;
            });
            return Optional.ofNullable(names.get(row));
        }
    }
}
