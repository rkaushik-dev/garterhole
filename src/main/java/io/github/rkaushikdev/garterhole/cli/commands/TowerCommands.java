package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.Args.phraseFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireFlag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.FlagSpec;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.http.routes.TowerRoutes;
import io.github.rkaushikdev.garterhole.tower.Bell;
import io.github.rkaushikdev.garterhole.tower.Tower;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The tower commands.
 *
 * <p>Adding a tower is typing its name and the weights off the board, which is
 * what a bell weight looks like written down: {@code 4-0-0,4-2-7,5-1-14}. The
 * numbering is filled in from the order, because the treble is always first and
 * nobody wants to type that.
 */
public final class TowerCommands {

    /** The flags these commands take. */
    public static final Map<String, FlagSpec> TOWER_FLAGS = FlagSpec.flags(
            "name",
            FlagSpec.value("n"),
            "place",
            FlagSpec.value("p"),
            "weights",
            FlagSpec.value("w"),
            "id",
            FlagSpec.VALUE,
            "json",
            FlagSpec.SWITCH,
            "quiet",
            FlagSpec.SWITCH);

    private TowerCommands() {}

    /**
     * {@code tower add --name "St Mary" --place Bridgend --weights 4-0-0,4-2-7,...}
     *
     * @param parsed the command line
     * @param context the service and the output
     * @throws ValidationException when there are no weights or a weight cannot be read
     */
    public static void addTower(ParsedArgs parsed, CommandContext context) {
        List<String> weights = Coerce.toList(requireFlag(parsed, "weights"), "weights");
        if (weights.isEmpty()) {
            throw new ValidationException("a tower needs at least one bell");
        }
        ObjectNode document = Json.object();
        document.put("name", phraseFlag(parsed, "name"));
        parsed.asText("place").ifPresent(place -> document.put("place", place));
        ArrayNode bells = document.putArray("bells");
        for (int index = 0; index < weights.size(); index += 1) {
            bells.addObject().put("number", index + 1).put("weight", weights.get(index));
        }
        Tower tower = TowerRoutes.towerFrom(document);
        ObjectNode stored = TowerRoutes.filingDocument(document, tower);
        context.service().putTower(tower.id(), stored);
        context.output().note("filed as " + tower.id());
        context.output().answer(stored, value -> describeTower(tower));
    }

    /**
     * {@code tower list}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void listTowers(ParsedArgs parsed, CommandContext context) {
        List<JsonNode> towers = context.service().listTowers();
        if (context.output().json()) {
            context.output().answer(towers);
            return;
        }
        if (towers.isEmpty()) {
            context.output().note("no towers yet");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("id", "name", "bells", "rings up to"));
        for (JsonNode entry : towers) {
            JsonNode stages = entry.path("stages");
            rows.add(List.of(
                    entry.path("id").asText(),
                    entry.path("name").asText(),
                    Json.templateText(entry.path("size")),
                    stages.isEmpty() ? "nothing" : Stage.name(stages.get(0).asInt())));
        }
        context.output().table(rows, List.of(Text.Align.LEFT, Text.Align.LEFT, Text.Align.RIGHT, Text.Align.LEFT));
    }

    /**
     * {@code tower show --id bridgend-st-mary}
     *
     * @param parsed the command line
     * @param context the service and the output
     * @throws ValidationException when no tower is named
     */
    public static void showTower(ParsedArgs parsed, CommandContext context) {
        String id = requireFlag(parsed, "id");
        JsonNode document = context.service().getTower(id);
        context.output().answer(document, value -> describeTower(TowerRoutes.towerFrom(value)));
    }

    private static List<String> describeTower(Tower tower) {
        List<String> lines = new ArrayList<>();
        lines.add(tower.describe());
        lines.add("");
        for (Bell bell : tower.bells()) {
            lines.add("  " + bell.number() + "  " + bell.weightText()
                    + bell.note().map(note -> "  " + note).orElse(""));
        }
        return lines;
    }
}
