package io.github.rkaushikdev.garterhole.http.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.http.Context;
import io.github.rkaushikdev.garterhole.http.Pagination;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.records.Performance;
import io.github.rkaushikdev.garterhole.records.PerformanceCollection;
import io.github.rkaushikdev.garterhole.records.PerformanceValidator;
import io.github.rkaushikdev.garterhole.records.Statistics;
import io.github.rkaushikdev.garterhole.records.Writers;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Schema;
import io.github.rkaushikdev.garterhole.validation.Schemas;
import io.github.rkaushikdev.garterhole.validation.Types;
import java.util.List;
import java.util.Map;

/**
 * Performance records over HTTP.
 *
 * <p>The report route is the one a secretary uses, and it can answer in any of
 * the formats the writers know. The format comes from a query parameter rather
 * than content negotiation, because a secretary is going to paste the URL into
 * a browser and wants a spreadsheet without setting a header.
 */
public final class PerformanceRoutes {

    private static final int MOST_RINGERS_LISTED = 20;

    /** What a performance has to say about itself. */
    private static final Schema NEW_PERFORMANCE = Schemas.object(Fields.of(
            Map.entry("tower", Schemas.string().min(1).max(120)),
            Map.entry("date", Types.isoDate()),
            Map.entry("title", Types.methodTitle()),
            Map.entry("changes", Types.changeCount()),
            Map.entry(
                    "band",
                    Schemas.arrayOf(Schemas.string().min(1).max(80)).min(3).max(17)),
            Map.entry("conductor", Schemas.optional(Schemas.string().min(1).max(80))),
            Map.entry("composer", Schemas.optional(Schemas.string().min(1).max(80))),
            Map.entry(
                    "duration",
                    Schemas.optional(Schemas.number().integer().min(1).max(1000))),
            Map.entry(
                    "footnotes",
                    Schemas.optional(Schemas.arrayOf(Schemas.string().max(500)).max(20)))));

    private PerformanceRoutes() {}

    /**
     * Add the routes to a router.
     *
     * @param router the router
     * @return the router
     */
    public static Router add(Router router) {
        router.get("/performances", (request, context) -> {
            List<JsonNode> filtered =
                    context.service().findPerformances(PerformanceCollection.Query.fromFields(request::queryValue));
            return Response.json(Pagination.paginate(request, filtered, "performances"));
        });

        router.post("/performances", (request, context) -> {
            JsonNode body = Schemas.check(NEW_PERFORMANCE, request.body(), "the performance");
            Service.Filed filed = context.service().filePerformance(body);
            return Response.created("/performances/" + filed.id(), filed);
        });

        router.get("/performances/:id", (request, context) -> context.service()
                .repository()
                .find(Service.PERFORMANCES, request.param("id"))
                .map(Response::json)
                .orElseGet(PerformanceRoutes::noSuchPerformance));

        router.post("/performances/check", (request, context) -> {
            JsonNode body = Schemas.check(NEW_PERFORMANCE, request.body(), "the performance");
            return Response.json(PerformanceValidator.validatePerformance(Performance.fromDocument(body)));
        });

        router.get("/report", (request, context) -> {
            Writers.Writer writer = Writers.writerNamed(request.queryValue("format", "text"));
            Writers.Options options = request.queryValue("title")
                    .map(Writers.Options.DEFAULT::withTitle)
                    .orElse(Writers.Options.DEFAULT);
            String written = writer.write(performancesOf(context), options);
            if (writer.contentType().equals("application/json")) {
                return Response.json(Json.parse(written));
            }
            return Response.text(written, Map.of("content-type", writer.contentType() + "; charset=utf-8"));
        });

        router.get("/statistics", (request, context) -> {
            List<Performance> records = performancesOf(context);
            List<Statistics.RingerLine> ringers = Statistics.byRinger(records);
            ObjectNode answer = Json.object();
            answer.set("totals", Json.toNode(Statistics.totals(records)));
            answer.set("byTower", Json.toNode(Statistics.byTower(records)));
            answer.set("byRinger", Json.toNode(ringers.subList(0, Math.min(MOST_RINGERS_LISTED, ringers.size()))));
            return Response.json(answer);
        });

        return router;
    }

    private static List<Performance> performancesOf(Context context) {
        return context.service().listPerformances().stream()
                .map(Performance::fromDocument)
                .toList();
    }

    private static Response noSuchPerformance() {
        ObjectNode error = Json.object().put("code", "not_found").put("message", "no such performance");
        ObjectNode body = Json.object();
        body.set("error", error);
        return Response.json(body, 404);
    }
}
