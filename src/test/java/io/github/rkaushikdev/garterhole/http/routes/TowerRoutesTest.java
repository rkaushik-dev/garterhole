package io.github.rkaushikdev.garterhole.http.routes;

import static io.github.rkaushikdev.garterhole.http.Documents.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Context;
import io.github.rkaushikdev.garterhole.http.Documents;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.http.Server;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.Json;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TowerRoutesTest {

    private static final String ST_MARY = "{'name':'St Mary the Virgin','place':'Bridgend','bells':["
            + "{'number':1,'weight':'4-0-0'},"
            + "{'number':2,'weight':'4-2-7'},"
            + "{'number':3,'weight':'5-1-14'},"
            + "{'number':4,'weight':'6-0-0'},"
            + "{'number':5,'weight':'8-2-21'},"
            + "{'number':6,'weight':'12-1-4','note':'F'}]}";

    @TempDir
    Path directory;

    private Server server;

    @BeforeEach
    void build() {
        Service service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
        Router router = TowerRoutes.add(new Router());
        server = new Server(Server.Options.of(router).withContext(Context.of(service, router)));
    }

    private Response ask(String method, String url, JsonNode body) {
        return server.handle(Documents.ask(method, url, body));
    }

    private Response ask(String method, String url) {
        return server.handle(Documents.ask(method, url));
    }

    @Nested
    class AddingATower {

        @Test
        void filesItUnderAnIdentifierMadeFromItsName() {
            Response response = ask("POST", "/towers", object(ST_MARY));
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().path("id").asText()).isEqualTo("bridgend-st-mary-the-virgin");
            assertThat(response.headers().get("location")).isEqualTo("/towers/bridgend-st-mary-the-virgin");
        }

        @Test
        void worksOutWhatItCanRing() {
            Response response = ask("POST", "/towers", object(ST_MARY));
            assertThat(response.body().path("size").asInt()).isEqualTo(6);
            assertThat(response.body().path("stages")).isEqualTo(Documents.doc("[6,5,4,3]"));
            assertThat(response.body().path("key").asText()).isEqualTo("F");
        }

        @Test
        void takesAWeightInPoundsInstead() {
            Response response =
                    ask("POST", "/towers", object("{'name':'Test','bells':[{'number':1,'weightPounds':400}]}"));
            assertThat(response.status()).isEqualTo(201);
        }

        @Test
        void refusesABellThatDoesNotSayWhatItWeighs() {
            Response response = ask("POST", "/towers", object("{'name':'Test','bells':[{'number':1}]}"));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().path("error").path("message").asText()).contains("does not say what it weighs");
        }

        @Test
        void refusesARingWithAGapInIt() {
            Response response = ask(
                    "POST",
                    "/towers",
                    object("{'name':'Test','bells':[{'number':1,'weight':'4-0-0'},{'number':3,'weight':'6-0-0'}]}"));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().path("error").path("message").asText()).contains("gap");
        }

        @Test
        void refusesAWeightWrittenWrongly() {
            Response response =
                    ask("POST", "/towers", object("{'name':'Test','bells':[{'number':1,'weight':'4-9-0'}]}"));
            assertThat(response.status()).isEqualTo(400);
        }

        @Test
        void refusesATowerWithNoBells() {
            assertThat(ask("POST", "/towers", object("{'name':'Test','bells':[]}"))
                            .status())
                    .isEqualTo(400);
        }
    }

    @Nested
    class ReadingTowers {

        @BeforeEach
        void fileStMary() {
            ask("POST", "/towers", object(ST_MARY));
        }

        @Test
        void listsThemWithATotal() {
            assertThat(ask("GET", "/towers").body().path("total").asInt()).isEqualTo(1);
        }

        @Test
        void readsOneBack() {
            assertThat(ask("GET", "/towers/bridgend-st-mary-the-virgin")
                            .body()
                            .path("name")
                            .asText())
                    .isEqualTo("St Mary the Virgin");
        }

        @Test
        void answers404ForOneItDoesNotHold() {
            assertThat(ask("GET", "/towers/nowhere").status()).isEqualTo(404);
        }

        @Test
        void listsTheBellsWithTheirWeights() {
            Response response = ask("GET", "/towers/bridgend-st-mary-the-virgin/bells");
            assertThat(response.body().path("bells")).hasSize(6);
            assertThat(response.body().path("tenor").asText()).isEqualTo("12-1-4");
            assertThat(response.body().path("bells").path(0).path("weight").asText())
                    .isEqualTo("4-0-0");
        }

        @Test
        void givesTheKeyFromTheTenor() {
            Response response = ask("GET", "/towers/bridgend-st-mary-the-virgin/bells");
            assertThat(response.body().path("key").asText()).isEqualTo("F");
            assertThat(response.body().path("offTheScale")).isEmpty();
        }

        @Test
        void namesTheBellsOffTheScale() {
            ask(
                    "POST",
                    "/towers",
                    object("{'name':'Out','bells':[{'number':1,'weight':'4-0-0','note':'D'},"
                            + "{'number':2,'weight':'5-0-0','note':'B'},{'number':3,'weight':'6-0-0','note':'Bb'},"
                            + "{'number':4,'weight':'7-0-0','note':'A'},{'number':5,'weight':'8-0-0','note':'G'},"
                            + "{'number':6,'weight':'9-0-0','note':'F'}]}"));
            Response response = ask("GET", "/towers/out/bells");
            assertThat(response.body().path("offTheScale"))
                    .isEqualTo(Json.parse("[{\"bell\": 2, \"note\": \"B\", \"expected\": \"C\"}]"));
        }

        @Test
        void addsTheRingUp() {
            Response response = ask("GET", "/towers/bridgend-st-mary-the-virgin/bells");
            assertThat(response.body().path("totalWeightPounds").asDouble()).isGreaterThan(0);
        }

        @Test
        void removesOne() {
            assertThat(ask("DELETE", "/towers/bridgend-st-mary-the-virgin").status())
                    .isEqualTo(204);
            assertThat(ask("GET", "/towers/bridgend-st-mary-the-virgin").status())
                    .isEqualTo(404);
        }

        @Test
        void answers404WhenRemovingOneThatIsNotThere() {
            assertThat(ask("DELETE", "/towers/nowhere").status()).isEqualTo(404);
        }
    }

    @Nested
    class TowerFrom {

        @Test
        void buildsTheClassFromADocument() {
            assertThat(TowerRoutes.towerFrom(object(ST_MARY)).size()).isEqualTo(6);
        }

        @Test
        void refusesADocumentWhoseIdDisagreesWithItsName() {
            assertThatThrownBy(() -> TowerRoutes.towerFrom(object(ST_MARY).put("id", "somewhere-else")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TowerId {

        @Test
        void joinsThePlaceAndTheName() {
            assertThat(TowerRoutes.towerId(object(ST_MARY))).isEqualTo("bridgend-st-mary-the-virgin");
        }

        @Test
        void usesTheNameAloneWhenThereIsNoPlace() {
            assertThat(TowerRoutes.towerId(object("{'name':'Test Tower'}"))).isEqualTo("test-tower");
        }
    }
}
