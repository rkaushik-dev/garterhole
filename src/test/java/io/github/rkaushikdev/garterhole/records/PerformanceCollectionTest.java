package io.github.rkaushikdev.garterhole.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.records.PerformanceCollection.Filed;
import io.github.rkaushikdev.garterhole.records.PerformanceCollection.Query;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PerformanceCollectionTest {

    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");

    private PerformanceCollection records;

    private static Performance.Builder fields() {
        return Performance.builder()
                .tower("St Mary")
                .date(LocalDate.of(2025, 5, 18))
                .title("Plain Bob Minor")
                .changes(1260)
                .band(SIX)
                .conductor("Alice");
    }

    private static Performance rung() {
        return fields().build();
    }

    @BeforeEach
    void fileThree() {
        records = new PerformanceCollection();
        records.addAll(List.of(
                rung(),
                fields().date(LocalDate.of(2025, 7, 2))
                        .changes(5040)
                        .conductor("Bob")
                        .build(),
                fields().date(LocalDate.of(2025, 3, 9))
                        .tower("All Saints")
                        .title("Cambridge Surprise Minor")
                        .changes(720)
                        .build()));
    }

    @Nested
    class FilingPerformances {

        @Test
        void filesAPerformanceUnderItsIdentifier() {
            PerformanceCollection collection = new PerformanceCollection();
            String id = collection.add(rung());
            assertThat(collection.size()).isEqualTo(1);
            assertThat(collection.get(id).tower()).isEqualTo("St Mary");
        }

        @Test
        void refusesTheSamePerformanceTwice() {
            PerformanceCollection collection = new PerformanceCollection();
            collection.add(rung());
            assertThatThrownBy(() -> collection.add(rung())).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> collection.add(rung())).hasMessageContaining("already filed");
        }

        @Test
        void acceptsTwoDifferentPerformancesOnTheSameDay() {
            PerformanceCollection collection = new PerformanceCollection();
            collection.add(rung());
            assertThatCode(() -> collection.add(fields().changes(720).build())).doesNotThrowAnyException();
        }

        @Test
        void refusesAnythingThatIsNotAPerformance() {
            assertThatThrownBy(() -> new PerformanceCollection().add(null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void skipsTheRepeatsWhenSeveralAreFiledAtOnce() {
            Filed result = records.addAll(List.of(rung(), fields().changes(1280).build()));
            assertThat(result.added()).hasSize(1);
            assertThat(result.alreadyFiled()).hasSize(1);
        }
    }

    @Nested
    class ReadingTheRecords {

        @Test
        void saysWhetherItHoldsOne() {
            assertThat(records.has(rung().id())).isTrue();
            assertThat(records.has("nothing")).isFalse();
        }

        @Test
        void throwsForOneItDoesNotHold() {
            assertThatThrownBy(() -> records.get("nothing")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void listsEverythingWithTheMostRecentFirst() {
            assertThat(records.all())
                    .extracting(Performance::dateText)
                    .containsExactly("2025-07-02", "2025-05-18", "2025-03-09");
        }

        @Test
        void iteratesInTheSameOrder() {
            List<Performance> seen = new ArrayList<>();
            records.forEach(seen::add);
            assertThat(seen).hasSize(3).isEqualTo(records.all());
        }

        @Test
        void takesAPerformanceOut() {
            Performance removed = records.remove(rung().id());
            assertThat(removed.tower()).isEqualTo("St Mary");
            assertThat(records.size()).isEqualTo(2);
        }

        @Test
        void throwsWhenTakingOutOneItDoesNotHold() {
            assertThatThrownBy(() -> records.remove("nothing")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Searching {

        @Test
        void givesEverythingWhenNothingIsAskedFor() {
            assertThat(records.find()).hasSize(3);
        }

        @Test
        void filtersByTowerHoweverItIsTyped() {
            assertThat(records.find(Query.ALL.tower("st mary"))).hasSize(2);
            assertThat(records.find(Query.ALL.tower("all"))).hasSize(1);
        }

        @Test
        void filtersByMethod() {
            assertThat(records.find(Query.ALL.title("cambridge"))).hasSize(1);
        }

        @Test
        void filtersByWhoRang() {
            assertThat(records.find(Query.ALL.ringer("Alice"))).hasSize(3);
            assertThat(records.find(Query.ALL.ringer("Nobody"))).isEmpty();
        }

        @Test
        void filtersByWhoConducted() {
            assertThat(records.find(Query.ALL.conductor("Bob"))).hasSize(1);
        }

        @Test
        void filtersByWhatTheLengthCountsAs() {
            assertThat(records.find(Query.ALL.kind(Lengths.Kind.PEAL))).hasSize(1);
            assertThat(records.find(Query.ALL.kind(Lengths.Kind.TOUCH))).hasSize(1);
        }

        @Test
        void filtersByDateIncludingTheStartAndExcludingTheEnd() {
            List<Performance> found =
                    records.find(Query.ALL.from(LocalDate.of(2025, 3, 9)).to(LocalDate.of(2025, 7, 2)));
            assertThat(found).hasSize(2);
        }

        @Test
        void filtersByClassAndStageForTheAnnualAnalysis() {
            records.add(fields().date(LocalDate.of(2025, 8, 1))
                    .title("Cambridge Surprise Major")
                    .band("Alice", "Bob", "Carol", "Dan", "Erin", "Frank", "Gina", "Hal")
                    .changes(1280)
                    .build());
            assertThat(records.find(Query.ALL.className("Surprise"))).hasSize(2);
            assertThat(records.find(Query.ALL.className("Surprise").stage(8))).hasSize(1);
            assertThat(records.find(Query.ALL.stage(6))).hasSize(3);
            assertThat(records.find(Query.ALL.className("Delight"))).isEmpty();
        }

        @Test
        void leavesOutARecordWhoseTitleCannotBeReadWhenAskedAboutItsStage() {
            records.add(fields().date(LocalDate.of(2025, 8, 1)).title("Mystery").build());
            assertThat(records.find(Query.ALL.stage(6))).hasSize(3);
            assertThat(records.find(Query.ALL.title("mystery"))).hasSize(1);
        }

        @Test
        void filtersByWhetherItWasRungInHand() {
            records.add(fields().date(LocalDate.of(2025, 8, 1))
                    .handbells(true)
                    .band("Alice", "Bob", "Carol")
                    .build());
            assertThat(records.find(Query.ALL.handbells(true))).hasSize(1);
            assertThat(records.find(Query.ALL.handbells(false))).hasSize(3);
        }

        @Test
        void leavesTheQueryItNarrowedAsItWas() {
            Query stMary = Query.ALL.tower("st mary");
            stMary.kind(Lengths.Kind.PEAL);
            assertThat(records.find(stMary)).hasSize(2);
            assertThat(Query.ALL.matches(rung())).isTrue();
        }

        @Test
        void combinesFilters() {
            assertThat(records.find(Query.ALL.tower("St Mary").kind(Lengths.Kind.PEAL)))
                    .hasSize(1);
        }
    }

    @Nested
    class FirstIn {

        @Test
        void findsTheEarliestPerformanceOfAMethodByARinger() {
            assertThat(records.firstIn("Alice", "Plain Bob Minor"))
                    .get()
                    .extracting(Performance::dateText)
                    .isEqualTo("2025-05-18");
        }

        @Test
        void givesNothingWhenTheyHaveNeverRungIt() {
            assertThat(records.firstIn("Alice", "Bristol Surprise Major")).isEmpty();
            assertThat(records.firstIn("Nobody", "Plain Bob Minor")).isEmpty();
        }
    }
}
