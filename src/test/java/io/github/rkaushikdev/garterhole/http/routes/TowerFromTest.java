package io.github.rkaushikdev.garterhole.http.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.Tower;
import io.github.rkaushikdev.garterhole.tower.TowerSerialiser;
import io.github.rkaushikdev.garterhole.util.Json;
import org.junit.jupiter.api.Test;

/**
 * The tower documents the commands file and read back. The command tests only
 * ever write board weights, so the other ways a document can describe a bell,
 * and the identifier check, are covered here.
 */
class TowerFromTest {

    @Test
    void takesAWeightInPoundsWhenThereIsNoBoardWeight() {
        Tower tower = TowerRoutes.towerFrom(Json.parse(
                "{\"name\":\"St Mary\",\"bells\":[{\"number\":1,\"weightPounds\":448,\"note\":\"F\",\"cast\":1890}]}"));
        assertThat(tower.bells().get(0).weightPounds()).isEqualTo(448);
        assertThat(tower.bells().get(0).cast()).hasValue(1890);
    }

    @Test
    void takesAWeightInKilogramsFromAFoundersCertificate() {
        Tower tower =
                TowerRoutes.towerFrom(Json.parse("{\"name\":\"St Mary\",\"bells\":[{\"number\":1,\"weightKg\":254}]}"));
        assertThat(tower.bells().get(0).weightKilograms()).isEqualTo(254);
        assertThat(tower.bells().get(0).weightText()).isEqualTo("5-0-0");
    }

    @Test
    void readsTheSameWayAsATowerDocumentDoes() {
        String fields = "\"name\":\"St Mary\",\"place\":\"Bridgend\",\"bells\":[{\"number\":1,\"weight\":\"4-0-0\"}]";
        Tower sent = TowerRoutes.towerFrom(Json.parse("{" + fields + "}"));
        Tower filed = TowerSerialiser.towerFromJson(
                Json.parse("{\"format\":\"garterhole.tower\",\"version\":1," + fields + "}"));
        assertThat(sent.id()).isEqualTo(filed.id());
        assertThat(sent.bells().get(0).weightPounds())
                .isEqualTo(filed.bells().get(0).weightPounds());
    }

    @Test
    void namesAnUnnumberedBellByItsPlaceInTheList() {
        assertThatThrownBy(() -> TowerRoutes.towerFrom(Json.parse("{\"name\":\"St Mary\",\"bells\":[{}]}")))
                .hasMessage("bell 0 does not say what it weighs");
    }

    @Test
    void refusesABellThatDoesNotSayWhatItWeighs() {
        assertThatThrownBy(() -> TowerRoutes.towerFrom(Json.parse("{\"name\":\"St Mary\",\"bells\":[{\"number\":1}]}")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("bell 1 does not say what it weighs");
    }

    @Test
    void refusesADocumentWhoseIdDisagreesWithItsName() {
        assertThatThrownBy(
                        () -> TowerRoutes.towerFrom(
                                Json.parse(
                                        "{\"name\":\"St Mary\",\"id\":\"all-saints\",\"bells\":[{\"number\":1,\"weight\":\"4-0-0\"}]}")))
                .hasMessage("the tower says its id is \"all-saints\" but its name gives \"st-mary\"");
    }
}
