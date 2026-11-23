package io.github.rkaushikdev.garterhole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceTest {

    private static final Method.Definition PLAIN_BOB_MAJOR = new Method.Definition("Plain", 8, "x18x18x18x18,12");
    private static final Method.Definition CAMBRIDGE =
            new Method.Definition("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");

    @TempDir
    Path directory;

    private Repository repository;
    private Service service;

    @BeforeEach
    void openTheStore() {
        repository = new Repository(new Journal(directory.resolve("journal.jsonl")));
        service = new Service(repository);
    }

    private static ObjectNode quarter() {
        ObjectNode fields = Json.object()
                .put("tower", "St Mary")
                .put("date", "2025-05-18")
                .put("title", "Plain Bob Minor")
                .put("changes", 1260);
        SIX.forEach(fields.putArray("band")::add);
        fields.put("conductor", "Alice");
        return fields;
    }

    @Nested
    class Methods {

        @Test
        void filesAMethodUnderTheTitleItsNotationGivesIt() {
            ObjectNode document = service.addMethod(PLAIN_BOB_MAJOR);
            assertThat(document.get("title").asText()).isEqualTo("Plain Bob Major");
            assertThat(repository.has(Service.METHODS, "Plain Bob Major")).isTrue();
        }

        @Test
        void worksTheClassOutRatherThanTrustingTheCaller() {
            service.addMethod(CAMBRIDGE);
            assertThat(service.getMethod("Cambridge Surprise Major")
                            .get("class")
                            .asText())
                    .isEqualTo("Surprise");
        }

        @Test
        void refusesAMethodThatIsAlreadyThere() {
            service.addMethod(PLAIN_BOB_MAJOR);
            assertThatThrownBy(() -> service.addMethod(PLAIN_BOB_MAJOR)).isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesNotationItCannotRead() {
            assertThatThrownBy(() -> service.addMethod(new Method.Definition("Bad", 8, "x1?x")))
                    .hasMessageContaining("not part of place notation");
        }

        @Test
        void listsWhatItHoldsInTitleOrder() {
            service.addMethod(PLAIN_BOB_MAJOR);
            service.addMethod(CAMBRIDGE);
            assertThat(service.listMethods())
                    .extracting(entry -> entry.get("title").asText())
                    .containsExactly("Cambridge Surprise Major", "Plain Bob Major");
        }

        @Test
        void complainsAboutOneItDoesNotHold() {
            assertThatThrownBy(() -> service.getMethod("Nothing Major")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void removesOne() {
            service.addMethod(PLAIN_BOB_MAJOR);
            assertThat(service.removeMethod("Plain Bob Major")).isTrue();
            assertThat(service.removeMethod("Plain Bob Major")).isFalse();
        }

        @Test
        void describesOneWithTheFactsDerivedFromItsChanges() {
            service.addMethod(PLAIN_BOB_MAJOR);
            ObjectNode described = service.describeMethod("Plain Bob Major");
            assertThat(described.get("className").asText()).isEqualTo("Bob");
            assertThat(described.get("huntBells")).isEqualTo(Json.parse("[1]"));
            assertThat(described.get("leadHeadCode").asText()).isEqualTo("a");
        }
    }

    @Nested
    class AnalysingATouch {

        @BeforeEach
        void fileTheMethod() {
            service.addMethod(PLAIN_BOB_MAJOR);
        }

        @Test
        void ringsTheTouchAndReports() {
            ObjectNode analysis = service.analyse("Plain Bob Major", "3(H)");
            assertThat(analysis.get("length").asInt()).isEqualTo(336);
            assertThat(analysis.get("true").asBoolean()).isTrue();
            assertThat(analysis.get("comesRound").asBoolean()).isTrue();
            assertThat(analysis.has("proof")).isFalse();
            assertThat(analysis.get("tenors").get("together").asBoolean()).isTrue();
        }

        @Test
        void reportsThePartsAndTheCalls() {
            ObjectNode analysis = service.analyse("Plain Bob Major", "3(H)");
            assertThat(analysis.get("parts").get("parts").asInt()).isEqualTo(3);
            assertThat(analysis.get("calls")).isEqualTo(Json.parse("[{\"symbol\":\"-\",\"count\":3}]"));
        }

        @Test
        void complainsAboutAMethodItDoesNotHold() {
            assertThatThrownBy(() -> service.analyse("Nothing Major", "3(H)")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void complainsAboutATouchItCannotRead() {
            assertThatThrownBy(() -> service.analyse("Plain Bob Major", "W)")).hasMessageContaining("without opening");
        }
    }

    @Nested
    class Towers {

        @Test
        void filesAndReadsOneBack() {
            service.putTower("st-mary", Json.parse("{\"name\":\"St Mary\",\"bells\":8}"));
            assertThat(service.getTower("st-mary")).isEqualTo(Json.parse("{\"name\":\"St Mary\",\"bells\":8}"));
        }

        @Test
        void replacesOneThatIsAlreadyThere() {
            service.putTower("st-mary", Json.parse("{\"bells\":6}"));
            service.putTower("st-mary", Json.parse("{\"bells\":8}"));
            assertThat(service.listTowers()).hasSize(1);
        }

        @Test
        void complainsAboutOneItDoesNotHold() {
            assertThatThrownBy(() -> service.getTower("nothing")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesSomethingThatIsNotATower() {
            assertThatThrownBy(() -> service.putTower("st-mary", Json.toNode("St Mary")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Performances {

        @Test
        void filesOneAndGivesBackItsIdentifier() {
            Service.Filed filed = service.filePerformance(quarter());
            assertThat(filed.id()).contains("2025-05-18");
            assertThat(repository.has(Service.PERFORMANCES, filed.id())).isTrue();
        }

        @Test
        void reportsTheWarningsWithoutRefusing() {
            ObjectNode fields = quarter();
            fields.remove("conductor");
            assertThat(service.filePerformance(fields).warnings()).contains("no conductor is recorded");
        }

        @Test
        void refusesOneThatDoesNotAddUp() {
            ObjectNode fields = quarter();
            ((com.fasterxml.jackson.databind.node.ArrayNode) fields.get("band")).remove(5);
            assertThatThrownBy(() -> service.filePerformance(fields)).hasMessageContaining("needs a band of 6");
        }

        @Test
        void refusesTheSamePerformanceTwice() {
            service.filePerformance(quarter());
            assertThatThrownBy(() -> service.filePerformance(quarter())).isInstanceOf(ConflictException.class);
        }

        @Test
        void writesNothingWhenItRefuses() {
            int before = repository.journal().count();
            ObjectNode fields = quarter();
            fields.putArray("band");
            assertThatThrownBy(() -> service.filePerformance(fields)).isInstanceOf(GarterholeException.class);
            assertThat(repository.journal().count()).isEqualTo(before);
        }

        @Test
        void listsWhatItHolds() {
            service.filePerformance(quarter());
            List<JsonNode> held = service.listPerformances();
            assertThat(held).hasSize(1);
        }
    }

    @Nested
    class Counts {

        @Test
        void saysWhatTheServiceHolds() {
            service.addMethod(PLAIN_BOB_MAJOR);
            service.putTower("st-mary", Json.parse("{\"bells\":8}"));
            assertThat(service.counts()).isEqualTo(new Service.Counts(1, 1, 0));
            assertThat(Json.stringify(service.counts())).isEqualTo("{\"methods\":1,\"towers\":1,\"performances\":0}");
        }

        @Test
        void refusesOptionsItCannotUse() {
            assertThatThrownBy(() -> new Service(null)).hasMessageContaining("needs a Repository");
            assertThatThrownBy(() -> new Service(null, Logger.silent())).isInstanceOf(ValidationException.class);
        }
    }
}
