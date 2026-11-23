package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.Args.phraseFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.FlagSpec;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.records.Performance;
import io.github.rkaushikdev.garterhole.records.PerformanceReport;
import io.github.rkaushikdev.garterhole.records.Writers;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * The performance commands.
 *
 * <p>Filing a quarter peal on the night is the thing a tower actually does with
 * this, and it has to be one line typed with cold hands. So the band is a comma
 * separated list, the date defaults to today, and the conductor defaults to the
 * first name in the band, which is what a tower writes on the board anyway when
 * the conductor rang the treble.
 */
public final class RecordCommands {

    /** The flags these commands take. */
    public static final Map<String, FlagSpec> RECORD_FLAGS = FlagSpec.flags(
            "tower", FlagSpec.VALUE,
            "date", FlagSpec.value("d"),
            "title", FlagSpec.value("t"),
            "changes", FlagSpec.value("c"),
            "band", FlagSpec.value("b"),
            "conductor", FlagSpec.VALUE,
            "composer", FlagSpec.VALUE,
            "duration", FlagSpec.VALUE,
            "format", FlagSpec.value("f"),
            "ringer", FlagSpec.VALUE,
            "json", FlagSpec.SWITCH,
            "quiet", FlagSpec.SWITCH);

    private RecordCommands() {}

    /**
     * {@code records file --tower "St Mary" --title "Plain Bob Minor" --changes 1260 --band Alice,Bob,...}
     *
     * @param parsed the command line
     * @param context the service, the output and the clock that says what today is
     * @throws ValidationException when the band is empty or a field cannot be read
     */
    public static void fileRecord(ParsedArgs parsed, CommandContext context) {
        List<String> band = Coerce.toList(requireFlag(parsed, "band"), "band");
        if (band.isEmpty()) {
            throw new ValidationException("a performance needs a band");
        }
        LocalDate date =
                parsed.asText("date").map(text -> Coerce.toDate(text, "date")).orElseGet(context::today);
        ObjectNode fields = Json.object();
        fields.put("tower", requireFlag(parsed, "tower"));
        fields.put("date", date.toString());
        fields.put("title", phraseFlag(parsed, "title"));
        fields.put("changes", requireNumber(parsed, "changes"));
        band.forEach(fields.putArray("band")::add);
        fields.put("conductor", requireFlag(parsed, "conductor", band.get(0)));
        parsed.asText("composer").ifPresent(composer -> fields.put("composer", composer));
        if (parsed.has("duration")) {
            fields.put("duration", requireNumber(parsed, "duration"));
        }
        Service.Filed filed = context.service().filePerformance(fields);
        filed.warnings().forEach(context.output()::note);
        filed.suggestedFootnotes().forEach(line -> context.output().note("footnote it has earned: " + line));
        context.output().note("filed as " + filed.id());
        context.output().answer(filed);
    }

    /**
     * {@code records list [--ringer Alice]}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void listRecords(ParsedArgs parsed, CommandContext context) {
        Optional<String> ringer = parsed.asText("ringer");
        List<JsonNode> records = context.service().listPerformances().stream()
                .filter(entry -> ringer.isEmpty() || rangIn(entry, ringer.get()))
                .toList();
        if (context.output().json()) {
            context.output().answer(records);
            return;
        }
        if (records.isEmpty()) {
            context.output().note("nothing filed yet");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("date", "tower", "changes", "method"));
        for (JsonNode entry : records) {
            rows.add(List.of(
                    entry.path("date").asText(),
                    entry.path("tower").asText(),
                    Json.templateText(entry.path("changes")),
                    entry.path("title").asText()));
        }
        context.output().table(rows, List.of(Text.Align.LEFT, Text.Align.LEFT, Text.Align.RIGHT, Text.Align.LEFT));
    }

    /**
     * {@code records report [--format csv]}
     *
     * @param parsed the command line
     * @param context the service and the output
     * @throws ValidationException when the format is not one of the writers
     */
    public static void reportRecords(ParsedArgs parsed, CommandContext context) {
        String format = requireFlag(parsed, "format", "text");
        if (!Writers.formats().contains(format)) {
            throw new ValidationException(
                    Json.quote(format) + " is not a format, so try one of " + String.join(", ", Writers.formats()));
        }
        List<Performance> records = context.service().listPerformances().stream()
                .map(Performance::fromDocument)
                .toList();
        context.output().answer(Writers.writerNamed(format).write(records));
    }

    /**
     * {@code records board [--title ...]} prints one performance the way it goes
     * on a peal board: the one rung of that method, or the first filed.
     *
     * @param parsed the command line
     * @param context the service and the output
     * @throws ValidationException when there is no such performance
     */
    public static void printBoard(ParsedArgs parsed, CommandContext context) {
        List<JsonNode> records = context.service().listPerformances();
        Optional<String> title = parsed.asText("title");
        Optional<JsonNode> wanted = title.isEmpty()
                ? records.stream().findFirst()
                : records.stream()
                        .filter(entry -> entry.path("title").isTextual()
                                && entry.path("title").textValue().equals(title.get()))
                        .findFirst();
        JsonNode found = wanted.orElseThrow(() -> new ValidationException("there is no performance to print"));
        context.output().answer(found, document -> PerformanceReport.boardLines(Performance.fromDocument(document)));
    }

    private static boolean rangIn(JsonNode entry, String ringer) {
        return StreamSupport.stream(entry.path("band").spliterator(), false)
                .anyMatch(name -> name.isTextual() && name.textValue().equals(ringer));
    }
}
