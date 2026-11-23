package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.Leaderboard.ORDERINGS;
import static io.github.rkaushikdev.garterhole.records.Leaderboard.leaderboard;
import static io.github.rkaushikdev.garterhole.records.Leaderboard.leaderboardLines;
import static io.github.rkaushikdev.garterhole.records.Leaderboard.scoreOf;
import static io.github.rkaushikdev.garterhole.records.Leaderboard.summary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Leaderboard.Options;
import io.github.rkaushikdev.garterhole.records.Leaderboard.Ordering;
import io.github.rkaushikdev.garterhole.records.Leaderboard.Place;
import io.github.rkaushikdev.garterhole.records.Statistics.RingerLine;
import io.github.rkaushikdev.garterhole.records.Statistics.Totals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LeaderboardTest {

    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");

    private static Performance.Builder fields() {
        return Performance.builder()
                .tower("St Mary")
                .date(LocalDate.of(2025, 5, 18))
                .title("Plain Bob Minor")
                .changes(1260)
                .band(SIX)
                .conductor("Alice");
    }

    private static List<Performance> records() {
        List<String> withGina = new ArrayList<>(SIX.subList(0, 5));
        withGina.add("Gina");
        return List.of(
                fields().build(),
                fields().date(LocalDate.of(2025, 6, 1))
                        .changes(5040)
                        .conductor("Bob")
                        .build(),
                fields().date(LocalDate.of(2025, 7, 1)).band(withGina).build());
    }

    @Nested
    class ScoreOf {

        private final RingerLine line = new RingerLine("Somebody", new Totals(3, 1, 0, 0, 7560), 2);

        @Test
        void scoresEachOrderingFromTheRightField() {
            assertThat(scoreOf(line, "performances")).isEqualTo(3);
            assertThat(scoreOf(line, "peals")).isEqualTo(1);
            assertThat(scoreOf(line, "changes")).isEqualTo(7560);
            assertThat(scoreOf(line, "conducted")).isEqualTo(2);
        }

        @Test
        void refusesAnOrderingNobodyUses() {
            assertThatThrownBy(() -> scoreOf(line, "height")).hasMessageContaining("is not an ordering");
        }

        @Test
        void scoresQuartersAndMethodsToo() {
            RingerLine counted = new RingerLine("Somebody", new Totals(9, 1, 6, 2, 12000), 2, 4);
            assertThat(scoreOf(counted, "quarters")).isEqualTo(6);
            assertThat(scoreOf(counted, "methods")).isEqualTo(4);
            assertThat(Ordering.METHODS.score(line)).isZero();
        }

        @Test
        void readsAnOrderingByItsLabel() {
            assertThat(Ordering.of("conducted")).isEqualTo(Ordering.CONDUCTED);
            assertThat(Ordering.QUARTERS).hasToString("quarters");
            assertThatThrownBy(() -> Ordering.of("Peals"))
                    .hasMessage("\"Peals\" is not an ordering, so try one of"
                            + " performances, peals, quarters, changes, conducted, methods");
        }

        @Test
        void listsTheOrderingsItKnows() {
            assertThat(ORDERINGS).contains("peals");
            assertThatThrownBy(() -> ORDERINGS.add("height")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class LeaderboardItself {

        @Test
        void putsTheBusiestRingerFirst() {
            List<Place> board = leaderboard(records());
            assertThat(board.get(0).place()).isEqualTo(1);
            assertThat(board.get(0).name()).isEqualTo("Alice");
            assertThat(board.get(0).score()).isEqualTo(3);
        }

        @Test
        void numbersThePlaces() {
            assertThat(leaderboard(records())).extracting(Place::place).containsExactly(1, 2, 3, 4, 5, 6, 7);
        }

        @Test
        void ordersByPealsWhenAsked() {
            List<Place> board = leaderboard(records(), Options.DEFAULT.withOrdering("peals"));
            assertThat(board).allMatch(line -> line.score() == 1);
        }

        @Test
        void ordersByConductingWhenAsked() {
            List<Place> board = leaderboard(records(), Options.DEFAULT.withOrdering("conducted"));
            assertThat(board.get(0).name()).isEqualTo("Alice");
            assertThat(board.get(0).score()).isEqualTo(2);
        }

        @Test
        void ordersByDifferentMethodsRung() {
            List<Performance> rung = new ArrayList<>(records());
            rung.add(fields().date(LocalDate.of(2025, 8, 1))
                    .title("Cambridge Surprise Minor")
                    .band("Gina", "Bob", "Carol", "Dan", "Erin", "Frank")
                    .conductor("Bob")
                    .build());
            List<Place> board = leaderboard(rung, Options.DEFAULT.withOrdering(Ordering.METHODS));
            assertThat(board).extracting(Place::name).startsWith("Bob", "Carol", "Dan", "Erin", "Frank", "Gina");
            assertThat(board.get(0).score()).isEqualTo(2);
            assertThat(board)
                    .filteredOn(line -> line.name().equals("Alice"))
                    .extracting(Place::score)
                    .containsExactly(1L);
        }

        @Test
        void ordersByQuartersLeavingThePealOut() {
            List<Place> board = leaderboard(records(), Options.DEFAULT.withOrdering("quarters"));
            assertThat(board.get(0).score()).isEqualTo(2);
            assertThat(board)
                    .filteredOn(line -> line.name().equals("Gina"))
                    .extracting(Place::score)
                    .containsExactly(1L);
        }

        @Test
        void leavesOutAnybodyWhoScoredNothing() {
            List<Place> board = leaderboard(records(), Options.DEFAULT.withOrdering("conducted"));
            assertThat(board).extracting(Place::name).containsExactly("Alice", "Bob");
        }

        @Test
        void stopsAtTheNumberOfPlacesAskedFor() {
            assertThat(leaderboard(records(), Options.DEFAULT.withTop(2))).hasSize(2);
        }

        @Test
        void breaksATieByName() {
            List<String> tied = leaderboard(records()).stream()
                    .filter(line -> line.score() == 3)
                    .map(Place::name)
                    .toList();
            assertThat(tied).isSorted();
        }

        @Test
        void refusesABoardWithNoPlaces() {
            assertThatThrownBy(() -> leaderboard(records(), Options.DEFAULT.withTop(0)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void givesNothingForNoRecords() {
            assertThat(leaderboard(List.of())).isEmpty();
        }
    }

    @Nested
    class LeaderboardLines {

        @Test
        void writesAHeadingAndATable() {
            List<String> lines = leaderboardLines(records());
            assertThat(lines.get(0)).isEqualTo("Most performances");
            assertThat(lines.get(1)).contains("Alice");
        }

        @Test
        void namesTheOrderingInTheHeading() {
            assertThat(leaderboardLines(records(), Options.DEFAULT.withOrdering("peals"))
                            .get(0))
                    .isEqualTo("Most peals");
        }

        @Test
        void saysSoWhenNobodyHasRungAnything() {
            assertThat(leaderboardLines(List.of())).containsExactly("nobody has rung anything");
        }
    }

    @Nested
    class Summary {

        @Test
        void sumsTheYearUpInASentence() {
            String line = summary(records());
            assertThat(line).contains("3 performances");
            assertThat(line).contains("most of them by Alice with 3");
        }

        @Test
        void saysSoWhenNothingWasRung() {
            assertThat(summary(List.of())).isEqualTo("nothing was rung");
        }
    }
}
