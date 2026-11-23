package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.TowerSerialiser.FORMAT_VERSION;
import static io.github.rkaushikdev.garterhole.tower.TowerSerialiser.bandFromJson;
import static io.github.rkaushikdev.garterhole.tower.TowerSerialiser.bandToJson;
import static io.github.rkaushikdev.garterhole.tower.TowerSerialiser.towerFromJson;
import static io.github.rkaushikdev.garterhole.tower.TowerSerialiser.towerToJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import io.github.rkaushikdev.garterhole.tower.Ringer.Details;
import io.github.rkaushikdev.garterhole.tower.Ringer.Role;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TowerSerialiserTest {

    private static Tower stMary() {
        return new Tower(
                "St Mary the Virgin",
                "Bridgend",
                List.of(
                        Bell.weighing(1, "4-0-0", Bell.Details.NONE.withNote("D")),
                        Bell.weighing(2, "4-2-7"),
                        Bell.weighing(3, "5-1-14"),
                        Bell.weighing(4, "6-0-0"),
                        Bell.weighing(5, "8-2-21"),
                        Bell.weighing(
                                6,
                                "12-1-4",
                                Bell.Details.NONE.withFounder("Taylor").withCast(1901))));
    }

    private static List<Ringer> band() {
        return List.of(
                Ringer.ringer("Alice Vane", Details.NONE.withId("alice").withRoles(Role.CONDUCTOR)),
                Ringer.ringer("Bob Reed", Details.NONE.withId("bob").withHeaviestBellPounds(900.0)));
    }

    private static CompetencyRegister competency() {
        return new CompetencyRegister()
                .record("alice", "Plain Bob Minor", Level.CONDUCTS)
                .record("bob", "Plain Bob Minor", Level.INSIDE);
    }

    /** A document written with single quotes, so that it reads like the literal it stands for. */
    private static JsonNode doc(String json) {
        return Json.parse(json.replace('\'', '"'));
    }

    @Nested
    class TowerToJson {

        @Test
        void stampsTheFormatAndTheVersion() {
            ObjectNode document = towerToJson(stMary());
            assertThat(document.get("format").asText()).isEqualTo("garterhole.tower");
            assertThat(document.get("version").asInt()).isEqualTo(FORMAT_VERSION);
        }

        @Test
        void writesTheWeightsTheWayABoardWritesThem() {
            assertThat(towerToJson(stMary()).get("bells").get(5).get("weight").asText())
                    .isEqualTo("12-1-4");
        }

        @Test
        void keepsTheDetailsABellCarries() {
            JsonNode bells = towerToJson(stMary()).get("bells");
            assertThat(bells.get(0).get("note").asText()).isEqualTo("D");
            assertThat(bells.get(5).get("founder").asText()).isEqualTo("Taylor");
            assertThat(bells.get(5).get("cast").asInt()).isEqualTo(1901);
        }

        @Test
        void refusesAnythingThatIsNotATower() {
            assertThatThrownBy(() -> towerToJson(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TowerFromJson {

        @Test
        void roundTrips() {
            Tower rebuilt = towerFromJson(Json.parse(Json.stringify(towerToJson(stMary()))));
            assertThat(rebuilt.name()).isEqualTo("St Mary the Virgin");
            assertThat(rebuilt.size()).isEqualTo(6);
            assertThat(rebuilt.tenor().weightText()).isEqualTo("12-1-4");
            assertThat(rebuilt.id()).isEqualTo(stMary().id());
        }

        @Test
        void takesAWeightInPoundsAsWell() {
            Tower rebuilt = towerFromJson(
                    doc(
                            "{'format':'garterhole.tower','version':1,'name':'Test','place':null,'bells':[{'number':1,'weightPounds':448}]}"));
            assertThat(rebuilt.bells().get(0).weightText()).isEqualTo("4-0-0");
        }

        @Test
        void refusesADocumentOfAnotherKind() {
            assertThatThrownBy(() -> towerFromJson(doc("{'format':'garterhole.band','version':1}")))
                    .hasMessageContaining("this reads garterhole.tower documents");
        }

        @Test
        void refusesAVersionItDoesNotKnow() {
            assertThatThrownBy(() -> towerFromJson(doc("{'format':'garterhole.tower','version':9}")))
                    .hasMessageContaining("that document is version 9");
        }

        @Test
        void refusesADocumentWithNoBells() {
            assertThatThrownBy(() -> towerFromJson(doc("{'format':'garterhole.tower','version':1,'name':'Test'}")))
                    .hasMessageContaining("needs its bells");
        }

        @Test
        void refusesABellThatDoesNotSayWhatItWeighs() {
            assertThatThrownBy(() -> towerFromJson(
                            doc("{'format':'garterhole.tower','version':1,'name':'Test','bells':[{'number':1}]}")))
                    .hasMessageContaining("bell 1 does not say what it weighs");
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> towerFromJson(null)).hasMessageContaining("must be an object");
        }
    }

    @Nested
    class Bands {

        @Test
        void writesTheBandAndWhatItCanRing() {
            ObjectNode document = bandToJson(band(), competency());
            assertThat(document.get("format").asText()).isEqualTo("garterhole.band");
            assertThat(document.get("ringers")).hasSize(2);
            assertThat(document.get("competency")
                            .get(0)
                            .get("methods")
                            .get(0)
                            .get("title")
                            .asText())
                    .isEqualTo("Plain Bob Minor");
        }

        @Test
        void roundTrips() {
            JsonNode document = Json.parse(Json.stringify(bandToJson(band(), competency())));
            TowerSerialiser.Band rebuilt = bandFromJson(document);
            assertThat(rebuilt.band()).extracting(Ringer::name).containsExactly("Alice Vane", "Bob Reed");
            assertThat(rebuilt.band().get(0).conducts()).isTrue();
            assertThat(rebuilt.band().get(1).heaviestBellPounds()).hasValue(900);
            assertThat(rebuilt.competency().levelOf("bob", "Plain Bob Minor")).hasValue(Level.INSIDE);
        }

        @Test
        void refusesADocumentItCannotWrite() {
            assertThatThrownBy(() -> bandToJson(null, competency())).hasMessageContaining("needs a band");
            assertThatThrownBy(() -> bandToJson(band(), null)).hasMessageContaining("needs a competency register");
        }

        @Test
        void refusesADocumentItCannotRead() {
            assertThatThrownBy(() -> bandFromJson(doc("{'format':'garterhole.band','version':1}")))
                    .hasMessageContaining("needs its ringers");
            assertThatThrownBy(() -> bandFromJson(null)).isInstanceOf(ValidationException.class);
        }
    }
}
