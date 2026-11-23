package io.github.rkaushikdev.garterhole.http.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Pagination;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.tower.Bell;
import io.github.rkaushikdev.garterhole.tower.Note;
import io.github.rkaushikdev.garterhole.tower.Tower;
import io.github.rkaushikdev.garterhole.tower.Tuning;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.validation.Schema;
import io.github.rkaushikdev.garterhole.validation.Schemas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Towers over HTTP.
 *
 * <p>A tower is stored as the document that came in rather than as the class,
 * which keeps the stored shape stable while the class changes. The class is
 * built on the way in to check the document and on the way out to answer the
 * questions that need it, such as what stages the tower can ring.
 */
public final class TowerRoutes {

    private static final Pattern BOARD_WEIGHT = Pattern.compile("^\\d+-[0-3]-([0-9]|1[0-9]|2[0-7])$");

    /** What a tower has to say about itself. */
    private static final Schema NEW_TOWER = Schemas.object(Fields.of(
            Map.entry("name", Schemas.string().min(1).max(120)),
            Map.entry("place", Schemas.optional(Schemas.string().max(120))),
            Map.entry(
                    "bells",
                    Schemas.arrayOf(Schemas.object(Fields.of(
                                    Map.entry(
                                            "number",
                                            Schemas.number().integer().min(1).max(16)),
                                    Map.entry(
                                            "weight",
                                            Schemas.optional(Schemas.string().pattern(BOARD_WEIGHT))),
                                    Map.entry(
                                            "weightPounds",
                                            Schemas.optional(Schemas.number().min(1))),
                                    Map.entry(
                                            "note",
                                            Schemas.optional(
                                                    Schemas.string().min(1).max(2))),
                                    Map.entry(
                                            "founder",
                                            Schemas.optional(Schemas.string().max(120))),
                                    Map.entry(
                                            "cast",
                                            Schemas.optional(
                                                    Schemas.number()
                                                            .integer()
                                                            .min(1200)
                                                            .max(2200))))))
                            .min(1)
                            .max(16))));

    private TowerRoutes() {}

    /**
     * Add the routes to a router.
     *
     * @param router the router
     * @return the router
     */
    public static Router add(Router router) {
        router.get(
                "/towers",
                (request, context) -> Response.json(
                        Pagination.paginate(request, context.service().listTowers(), "towers")));

        router.post("/towers", (request, context) -> {
            JsonNode body = Schemas.check(NEW_TOWER, request.body(), "the tower");
            Tower tower = towerFrom(body);
            ObjectNode document = filingDocument(body, tower);
            context.service().putTower(tower.id(), document);
            return Response.created("/towers/" + tower.id(), document);
        });

        router.get(
                "/towers/:id",
                (request, context) -> Response.json(context.service().getTower(request.param("id"))));

        router.get("/towers/:id/bells", (request, context) -> {
            Tower tower = towerFrom(context.service().getTower(request.param("id")));
            ArrayNode bells = Json.array();
            for (Bell bell : tower.bells()) {
                ObjectNode entry = bells.addObject();
                entry.put("number", bell.number());
                entry.put("weight", bell.weightText());
                Json.putNumber(entry, "weightPounds", bell.weightPounds());
                entry.put("note", bell.note().orElse(null));
            }
            ObjectNode answer = Json.object();
            answer.set("bells", bells);
            answer.put("tenor", tower.tenor().weightText());
            Json.putNumber(answer, "totalWeightPounds", tower.totalWeightPounds());
            answer.put("key", Tuning.keyOf(tower).map(Note::sharpName).orElse(null));
            ArrayNode discords = answer.putArray("offTheScale");
            for (Tuning.Discord discord : Tuning.discords(tower)) {
                discords.addObject()
                        .put("bell", discord.bell())
                        .put("note", discord.found().sharpName())
                        .put("expected", discord.expected().sharpName());
            }
            return Response.json(answer);
        });

        router.delete("/towers/:id", (request, context) -> {
            boolean removed = context.service().repository().delete(Service.TOWERS, request.param("id"));
            return removed ? Response.noContent() : Response.json(Json.object().put("removed", false), 404);
        });

        return router;
    }

    /**
     * Build the class from a document, so that a tower with a gap in its ring
     * is refused rather than stored.
     *
     * @param document the tower as filed or as sent
     * @return the tower
     * @throws ValidationException when the document does not describe a tower, or
     *     its identifier disagrees with its name
     */
    public static Tower towerFrom(JsonNode document) {
        List<Bell> bells = new ArrayList<>();
        for (JsonNode entry : document.path("bells")) {
            bells.add(bellFrom(entry));
        }
        JsonNode place = document.path("place");
        Tower tower =
                new Tower(document.path("name").textValue(), Json.isUndefined(place) ? null : place.textValue(), bells);
        JsonNode id = document.path("id");
        if (!Json.isUndefined(id) && !id.equals(Json.toNode(tower.id()))) {
            throw new ValidationException(
                    "the tower says its id is " + Json.stringify(id) + " but its name gives " + Json.quote(tower.id()));
        }
        return tower;
    }

    /**
     * The document a tower is filed as: what was sent, with what the class
     * works out from it laid over the top, so that a list of towers can be
     * printed without building every one of them again.
     *
     * @param sent the tower as it was sent
     * @param tower the tower built from it
     * @return a copy of what was sent, with {@code id}, {@code size}, {@code stages}
     *     and {@code key} added, the key {@code null} when the tenor has no note
     */
    public static ObjectNode filingDocument(JsonNode sent, Tower tower) {
        ObjectNode document = ((ObjectNode) sent).deepCopy();
        document.put("id", tower.id());
        document.put("size", tower.size());
        tower.stages().forEach(document.putArray("stages")::add);
        document.put("key", Tuning.keyOf(tower).map(Note::sharpName).orElse(null));
        return document;
    }

    /**
     * The identifier a tower would be filed under: its place and its name,
     * made safe for a URL.
     *
     * @param document the tower
     * @return the identifier
     */
    public static String towerId(JsonNode document) {
        JsonNode place = document.path("place");
        String name = Json.templateText(document.path("name"));
        return Text.slugify(Json.isUndefined(place) ? name : Json.templateText(place) + " " + name);
    }

    private static Bell bellFrom(JsonNode entry) {
        int number = entry.path("number").intValue();
        Bell.Details details = new Bell.Details(
                textOrNull(entry.path("note")),
                textOrNull(entry.path("founder")),
                entry.path("cast").isNumber()
                        ? Integer.valueOf(entry.path("cast").intValue())
                        : null);
        JsonNode weight = entry.path("weight");
        if (!Json.isUndefined(weight)) {
            return Bell.weighing(number, weight.textValue(), details);
        }
        JsonNode weightPounds = entry.path("weightPounds");
        if (Json.isUndefined(weightPounds)) {
            throw new ValidationException(
                    "bell " + Json.templateText(entry.path("number")) + " does not say what it weighs");
        }
        return new Bell(number, weightPounds.doubleValue(), details);
    }

    private static String textOrNull(JsonNode value) {
        return value.isTextual() ? value.textValue() : null;
    }
}
