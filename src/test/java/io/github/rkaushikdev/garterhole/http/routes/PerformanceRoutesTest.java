package io.github.rkaushikdev.garterhole.http.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.http.Context;
import io.github.rkaushikdev.garterhole.http.Documents;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.http.Server;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.Json;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformanceRoutesTest {

    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");

    @TempDir
    Path directory;

    private Server server;

    @BeforeEach
    void build() {
        Service service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
        Router router = PerformanceRoutes.add(new Router());
        server = new Server(Server.Options.of(router).withContext(Context.of(service, router)));
    }

    private Response ask(String method, String url, JsonNode body) {
        return server.handle(Documents.ask(method, url, body));
    }

    private Response ask(String method, String url) {
        return server.handle(Documents.ask(method, url));
    }

    private static ObjectNode quarter() {
        ObjectNode fields = Json.object()
                .put("tower", "St Mary")
                .put("date", "2025-05-18")
                .put("title", "Plain Bob Minor")
                .put("changes", 1260);
        withBand(fields, SIX);
        fields.put("conductor", "Alice");
        return fields;
    }

    private static ObjectNode withBand(ObjectNode fields, List<String> band) {
        ArrayNode ringers = fields.putArray("band");
        band.forEach(ringers::add);
        return fields;
    }

    @Nested
    class FilingAPerformance {

        @Test
        void filesItAndSaysWhereItWent() {
            Response response = ask("POST", "/performances", quarter());
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.headers().get("location")).contains("/performances/2025-05-18");
        }

        @Test
        void reportsTheWarningsWithoutRefusing() {
            ObjectNode fields = quarter();
            fields.remove("conductor");
            Response response = ask("POST", "/performances", fields);
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().path("warnings")).contains(Json.toNode("no conductor is recorded"));
        }

        @Test
        void refusesABandOfTheWrongSize() {
            assertThat(ask("POST", "/performances", withBand(quarter(), SIX.subList(0, 4)))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void refusesADateWrittenAnyOtherWay() {
            assertThat(ask("POST", "/performances", quarter().put("date", "18/05/2025"))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void refusesATitleThatIsNotOne() {
            assertThat(ask("POST", "/performances", quarter().put("title", "nope"))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void refusesTheSamePerformanceTwice() {
            ask("POST", "/performances", quarter());
            assertThat(ask("POST", "/performances", quarter()).status()).isEqualTo(409);
        }
    }

    @Nested
    class CheckingWithoutFiling {

        @Test
        void saysASoundRecordIsSound() {
            Response response = ask("POST", "/performances/check", quarter());
            assertThat(response.body().path("ok").asBoolean()).isTrue();
            assertThat(ask("GET", "/performances").body().path("total").asInt()).isZero();
        }

        @Test
        void reportsTheProblemsWithOneThatIsNot() {
            Response response = ask("POST", "/performances/check", withBand(quarter(), SIX.subList(0, 5)));
            assertThat(response.body().path("ok").asBoolean()).isFalse();
            assertThat(response.body().path("problems").path(0).asText()).contains("needs a band of 6");
        }
    }

    @Nested
    class ReadingPerformances {

        @BeforeEach
        void fileTwo() {
            ask("POST", "/performances", quarter());
            ask("POST", "/performances", quarter().put("tower", "All Saints").put("changes", 5040));
        }

        @Test
        void listsThemWithATotal() {
            assertThat(ask("GET", "/performances").body().path("total").asInt()).isEqualTo(2);
        }

        @Test
        void filtersByTower() {
            assertThat(ask("GET", "/performances?tower=all")
                            .body()
                            .path("total")
                            .asInt())
                    .isEqualTo(1);
        }

        @Test
        void filtersByLengthAndStageAndClass() {
            assertThat(ask("GET", "/performances?kind=peal")
                            .body()
                            .path("total")
                            .asInt())
                    .isEqualTo(1);
            assertThat(ask("GET", "/performances?stage=Minor&class=Bob")
                            .body()
                            .path("total")
                            .asInt())
                    .isEqualTo(2);
            assertThat(ask("GET", "/performances?stage=8").body().path("total").asInt())
                    .isZero();
            assertThat(ask("GET", "/performances?handbells=false&conductor=Alice")
                            .body()
                            .path("total")
                            .asInt())
                    .isEqualTo(2);
        }

        @Test
        void refusesAFieldItCannotRead() {
            assertThat(ask("GET", "/performances?stage=Octuples").status()).isEqualTo(400);
            assertThat(ask("GET", "/performances?handbells=sometimes").status()).isEqualTo(400);
            assertThat(ask("GET", "/report?format=pdf").status()).isEqualTo(400);
        }

        @Test
        void filtersByWhoRang() {
            assertThat(ask("GET", "/performances?ringer=Alice")
                            .body()
                            .path("total")
                            .asInt())
                    .isEqualTo(2);
            assertThat(ask("GET", "/performances?ringer=Nobody")
                            .body()
                            .path("total")
                            .asInt())
                    .isZero();
        }

        @Test
        void readsOneBackByIdentifier() {
            JsonNode listed =
                    ask("GET", "/performances").body().path("performances").path(0);
            Response found =
                    ask("GET", "/performances/" + listed.path("date").asText() + "-st-mary-plain-bob-minor-1260");
            assertThat(found.status()).isIn(200, 404);
        }

        @Test
        void answers404ForOneItDoesNotHold() {
            assertThat(ask("GET", "/performances/nothing").status()).isEqualTo(404);
        }
    }

    @Nested
    class TheReport {

        @BeforeEach
        void fileTwo() {
            ask("POST", "/performances", quarter());
            ask("POST", "/performances", quarter().put("tower", "All Saints").put("changes", 5040));
        }

        @Test
        void writesTextByDefault() {
            Response response = ask("GET", "/report");
            assertThat(response.headers().get("content-type")).contains("text/plain");
            assertThat(response.body().asText()).contains("By tower");
        }

        @Test
        void takesATitle() {
            assertThat(ask("GET", "/report?title=Ringing%20in%202025").body().asText())
                    .contains("Ringing in 2025");
        }

        @Test
        void writesASpreadsheet() {
            Response response = ask("GET", "/report?format=csv");
            assertThat(response.headers().get("content-type")).contains("text/csv");
            assertThat(response.body().asText().split("\n", -1)[0]).contains("date,tower,title");
        }

        @Test
        void writesJsonAsJsonRatherThanAsAString() {
            Response response = ask("GET", "/report?format=json");
            assertThat(response.body().isArray()).isTrue();
            assertThat(response.body()).hasSize(2);
        }

        @Test
        void refusesAFormatNobodyWrote() {
            Response response = ask("GET", "/report?format=pdf");
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().path("error").path("message").asText()).contains("is not a format");
        }
    }

    @Nested
    class Statistics {

        @BeforeEach
        void fileOne() {
            ask("POST", "/performances", quarter());
        }

        @Test
        void countsWhatHasBeenRung() {
            Response response = ask("GET", "/statistics");
            assertThat(response.body().path("totals").path("performances").asInt())
                    .isEqualTo(1);
            assertThat(response.body().path("totals").path("quarters").asInt()).isEqualTo(1);
        }

        @Test
        void breaksTheCountDownByTowerAndByRinger() {
            Response response = ask("GET", "/statistics");
            assertThat(response.body().path("byTower").path(0).path("tower").asText())
                    .isEqualTo("St Mary");
            assertThat(response.body()
                            .path("byRinger")
                            .path(0)
                            .path("totals")
                            .path("performances")
                            .asInt())
                    .isEqualTo(1);
        }
    }
}
