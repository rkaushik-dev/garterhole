package io.github.rkaushikdev.garterhole.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.util.Json;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PerformanceTest {

    private static final List<String> BAND =
            List.of("Alice Vane", "Bob Reed", "Carol Sims", "Dan Ellis", "Erin Fox", "Frank Gale");

    /** The fields of the quarter below. */
    private static Performance.Builder fields() {
        return Performance.builder()
                .tower("St Mary, Bridgend")
                .date(LocalDate.of(2025, 5, 18))
                .title("Plain Bob Minor")
                .changes(1260)
                .band(BAND)
                .conductor("Alice Vane");
    }

    private static Performance quarter() {
        return fields().build();
    }

    /** The same fields as a document, the way the store and a request body carry them. */
    private static ObjectNode document() {
        ObjectNode document = Json.object();
        document.put("tower", "St Mary, Bridgend");
        document.put("date", "2025-05-18");
        document.put("title", "Plain Bob Minor");
        document.put("changes", 1260);
        BAND.forEach(document.putArray("band")::add);
        document.put("conductor", "Alice Vane");
        return document;
    }

    @Nested
    class PerformanceItself {

        @Test
        void keepsWhatItWasGiven() {
            Performance rung = quarter();
            assertThat(rung.tower()).isEqualTo("St Mary, Bridgend");
            assertThat(rung.title()).isEqualTo("Plain Bob Minor");
            assertThat(rung.changes()).isEqualTo(1260);
            assertThat(rung.band()).isEqualTo(BAND);
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(Performance.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> quarter().band().add("Gina")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void copiesTheDateItWasGiven() {
            LocalDate date = LocalDate.of(2025, 5, 18);
            Performance rung = Performance.builder()
                    .tower("Test")
                    .date(date)
                    .title("Plain Bob Minor")
                    .changes(1260)
                    .band(BAND)
                    .build();
            date.withYear(1999);
            assertThat(rung.dateText()).isEqualTo("2025-05-18");
        }

        @Test
        void readsTheStageOutOfTheTitle() {
            assertThat(quarter().stage()).isEqualTo(6);
        }

        @Test
        void saysWhatTheLengthCountsAs() {
            assertThat(quarter().kind()).isEqualTo(Lengths.Kind.QUARTER_PEAL);
            assertThat(fields().changes(720).build().kind()).isEqualTo(Lengths.Kind.TOUCH);
            assertThat(fields().changes(5040).build().kind()).isEqualTo(Lengths.Kind.PEAL);
        }

        @Test
        void countsTheBand() {
            assertThat(quarter().bandSize()).isEqualTo(6);
        }

        @Test
        void makesAnIdentifierThatCatchesTheSamePerformanceTwice() {
            assertThat(quarter().id()).isEqualTo(fields().build().id());
            assertThat(quarter().id())
                    .isNotEqualTo(fields().changes(1280).build().id());
        }

        @Test
        void printsItself() {
            assertThat(quarter()).hasToString("St Mary, Bridgend, 2025-05-18: 1260 Plain Bob Minor");
        }
    }

    @Nested
    class BadRecords {

        @Test
        void refusesADefinitionThatIsNotAnObject() {
            assertThatThrownBy(() -> Performance.fromDocument(TextNode.valueOf("a peal")))
                    .hasMessageContaining("needs a definition object");
        }

        @Test
        void refusesAMissingTowerOrTitle() {
            assertThatThrownBy(() -> fields().tower("").build()).hasMessageContaining("a tower name");
            assertThatThrownBy(() -> fields().title("").build()).hasMessageContaining("a method title");
        }

        @Test
        void refusesADateThatIsNotADate() {
            assertThatThrownBy(() -> fields().date(null).build()).hasMessageContaining("needs the date");
            assertThatThrownBy(() -> Performance.fromDocument(document().put("date", "nonsense")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesALengthThatIsNotALength() {
            assertThatThrownBy(() -> fields().changes(0).build()).hasMessageContaining("is not a number of changes");
        }

        @Test
        void refusesAnEmptyBandOrANamelessRinger() {
            assertThatThrownBy(() -> fields().band(List.of()).build()).hasMessageContaining("needs a band");
            assertThatThrownBy(() -> fields().band("Alice", "").conductor(null).build())
                    .hasMessageContaining("the ringer of bell 2");
        }

        @Test
        void refusesAConductorWhoDidNotRing() {
            assertThatThrownBy(() -> fields().conductor("Nobody").build())
                    .hasMessage("the conductor \"Nobody\" is not in the band");
        }

        @Test
        void refusesADurationThatIsNotMinutes() {
            assertThatThrownBy(() -> fields().duration(0).build()).hasMessageContaining("not a length of time");
        }
    }

    @Nested
    class LookingUpTheBand {

        @Test
        void namesTheRingerOfABell() {
            assertThat(quarter().ringerOf(1)).isEqualTo("Alice Vane");
            assertThat(quarter().ringerOf(6)).isEqualTo("Frank Gale");
        }

        @Test
        void refusesABellNobodyRang() {
            assertThatThrownBy(() -> quarter().ringerOf(7)).hasMessageContaining("was not rung in this performance");
            assertThatThrownBy(() -> quarter().ringerOf(0)).isInstanceOf(ValidationException.class);
        }

        @Test
        void findsTheBellSomebodyRang() {
            assertThat(quarter().bellOf("Carol Sims")).hasValue(3);
            assertThat(quarter().bellsOf("Carol Sims")).containsExactly(3);
            assertThat(quarter().bellsOf("Nobody")).isEmpty();
        }

        @Test
        void givesEachHandbellRingerAPair() {
            Performance inHand = fields().handbells(true)
                    .band("Alice Vane", "Bob Hart", "Carol Sims")
                    .build();
            assertThat(inHand.handbells()).isTrue();
            assertThat(inHand.ringerOf(1)).isEqualTo("Alice Vane");
            assertThat(inHand.ringerOf(2)).isEqualTo("Alice Vane");
            assertThat(inHand.ringerOf(6)).isEqualTo("Carol Sims");
            assertThat(inHand.bellsOf("Bob Hart")).containsExactly(3, 4);
            assertThat(inHand.bellOf("Carol Sims")).hasValue(5);
            assertThatThrownBy(() -> inHand.ringerOf(7)).hasMessageContaining("was not rung");
            assertThat(quarter().handbells()).isFalse();
        }

        @Test
        void readsAHandbellPerformanceFromADocument() {
            ObjectNode stored = document();
            stored.put("handbells", true);
            assertThat(Performance.fromDocument(stored).handbells()).isTrue();
            assertThat(Performance.fromDocument(document()).handbells()).isFalse();
        }

        @Test
        void givesNothingForSomebodyWhoWasNotThere() {
            assertThat(quarter().bellOf("Nobody")).isEmpty();
        }

        @Test
        void recordsTheOptionalDetailsAsMissingWhenTheyAreMissing() {
            Performance rung = fields().conductor(null).composer(null).build();
            assertThat(rung.conductor()).isEmpty();
            assertThat(rung.composer()).isEmpty();
            assertThat(rung.duration()).isEmpty();
            assertThat(rung.footnotes()).isEmpty();
            ObjectNode stored = document();
            stored.putNull("conductor");
            stored.putNull("composer");
            assertThat(Performance.fromDocument(stored).conductor()).isEmpty();
        }
    }
}
