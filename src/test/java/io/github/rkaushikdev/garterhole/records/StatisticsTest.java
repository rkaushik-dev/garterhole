package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.Statistics.between;
import static io.github.rkaushikdev.garterhole.records.Statistics.byMethod;
import static io.github.rkaushikdev.garterhole.records.Statistics.byRinger;
import static io.github.rkaushikdev.garterhole.records.Statistics.byStage;
import static io.github.rkaushikdev.garterhole.records.Statistics.byTower;
import static io.github.rkaushikdev.garterhole.records.Statistics.totals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Statistics.MethodCount;
import io.github.rkaushikdev.garterhole.records.Statistics.RingerLine;
import io.github.rkaushikdev.garterhole.records.Statistics.StageCount;
import io.github.rkaushikdev.garterhole.records.Statistics.Totals;
import io.github.rkaushikdev.garterhole.records.Statistics.TowerLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");
    private static final List<String> EIGHT = eight();

    private static List<String> eight() {
        List<String> band = new ArrayList<>(SIX);
        band.add("Gina");
        band.add("Hal");
        return List.copyOf(band);
    }

    private static List<Performance> records() {
        return List.of(
                Performance.builder()
                        .tower("St Mary")
                        .date(LocalDate.of(2025, 1, 5))
                        .title("Plain Bob Minor")
                        .changes(1260)
                        .band(SIX)
                        .conductor("Alice")
                        .build(),
                Performance.builder()
                        .tower("St Mary")
                        .date(LocalDate.of(2025, 3, 9))
                        .title("Plain Bob Minor")
                        .changes(5040)
                        .band(SIX)
                        .conductor("Bob")
                        .build(),
                Performance.builder()
                        .tower("All Saints")
                        .date(LocalDate.of(2025, 6, 14))
                        .title("Cambridge Surprise Major")
                        .changes(1280)
                        .band(EIGHT)
                        .conductor("Alice")
                        .build(),
                Performance.builder()
                        .tower("All Saints")
                        .date(LocalDate.of(2025, 8, 2))
                        .title("Plain Bob Major")
                        .changes(720)
                        .band(EIGHT)
                        .build());
    }

    private static RingerLine ringer(String name) {
        return byRinger(records()).stream()
                .filter(line -> line.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class TotalsOfRecords {

        @Test
        void countsPerformancesByKind() {
            assertThat(totals(records())).isEqualTo(new Totals(4, 1, 2, 1, 1260 + 5040 + 1280 + 720));
        }

        @Test
        void countsNothingForNothing() {
            assertThat(totals(List.of())).isEqualTo(new Totals(0, 0, 0, 0, 0));
        }

        @Test
        void refusesAnythingThatIsNotAPerformance() {
            assertThatThrownBy(() -> totals(Arrays.asList((Performance) null))).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ByRinger {

        @Test
        void countsAPerformanceOnceForEverybodyInTheBand() {
            RingerLine alice = ringer("Alice");
            assertThat(alice.totals().performances()).isEqualTo(4);
            assertThat(alice.conducted()).isEqualTo(2);
        }

        @Test
        void countsSomebodyWhoRangOnce() {
            RingerLine gina = ringer("Gina");
            assertThat(gina.totals().performances()).isEqualTo(2);
            assertThat(gina.conducted()).isZero();
        }

        @Test
        void countsTheDifferentMethodsEachRang() {
            assertThat(ringer("Alice").methods()).isEqualTo(3);
            assertThat(ringer("Gina").methods()).isEqualTo(2);
            assertThat(new RingerLine("Nobody", new Totals(0, 0, 0, 0, 0), 0).methods())
                    .isZero();
        }

        @Test
        void sortsByHowMuchEachRang() {
            List<RingerLine> lines = byRinger(records());
            assertThat(lines.get(0).totals().performances())
                    .isGreaterThanOrEqualTo(lines.get(lines.size() - 1).totals().performances());
        }

        @Test
        void breaksATieByName() {
            List<String> names = byRinger(records()).stream()
                    .filter(line -> line.totals().performances() == 2)
                    .map(RingerLine::name)
                    .toList();
            assertThat(names).containsExactly("Gina", "Hal");
        }
    }

    @Nested
    class ByTower {

        @Test
        void countsWhatEachTowerHadRungInIt() {
            List<TowerLine> lines = byTower(records());
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).totals().performances()).isEqualTo(2);
        }

        @Test
        void namesBothTowers() {
            assertThat(byTower(records()).stream()
                            .map(TowerLine::tower)
                            .sorted()
                            .toList())
                    .containsExactly("All Saints", "St Mary");
        }
    }

    @Nested
    class ByMethod {

        @Test
        void countsEachMethodMostFirst() {
            assertThat(byMethod(records()).get(0)).isEqualTo(new MethodCount("Plain Bob Minor", 2));
        }

        @Test
        void listsEveryMethodOnce() {
            assertThat(byMethod(records())).hasSize(3);
        }
    }

    @Nested
    class ByStage {

        @Test
        void countsEachStageLargestFirst() {
            assertThat(byStage(records())).containsExactly(new StageCount(8, 2), new StageCount(6, 2));
        }

        @Test
        void skipsATitleItCannotRead() {
            Performance odd = Performance.builder()
                    .tower("Test")
                    .date(LocalDate.of(2025, 1, 1))
                    .title("Nonsense")
                    .changes(720)
                    .band(SIX)
                    .build();
            assertThat(byStage(List.of(odd))).isEmpty();
        }
    }

    @Nested
    class Between {

        @Test
        void keepsThePerformancesInsideTheDates() {
            assertThat(between(records(), LocalDate.of(2025, 2, 1), LocalDate.of(2025, 7, 1)))
                    .hasSize(2);
        }

        @Test
        void includesTheStartAndExcludesTheEnd() {
            assertThat(between(records(), LocalDate.of(2025, 1, 5), LocalDate.of(2025, 3, 9)))
                    .hasSize(1);
        }

        @Test
        void refusesSomethingThatIsNotAPairOfDates() {
            assertThatThrownBy(() -> between(records(), null, LocalDate.now()))
                    .hasMessageContaining("two dates are needed");
        }
    }
}
