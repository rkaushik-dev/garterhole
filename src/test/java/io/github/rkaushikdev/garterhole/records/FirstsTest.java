package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.Firsts.isMilestone;
import static io.github.rkaushikdev.garterhole.records.Firsts.suggestFootnotes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FirstsTest {

    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");

    private static Performance.Builder quarter(int day) {
        return Performance.builder()
                .tower("St Mary")
                .date(LocalDate.of(2025, 1, 1).plusDays(day))
                .title("Plain Bob Minor")
                .changes(1260)
                .band(SIX)
                .conductor("Alice");
    }

    @Nested
    class Suggestions {

        @Test
        void givesEverybodyTheirFirstWhenNothingCameBefore() {
            assertThat(suggestFootnotes(quarter(0).build(), List.of()))
                    .startsWith("First quarter peal for Alice.", "First quarter peal for Bob.")
                    .endsWith("First quarter peal for Frank.", "First quarter peal as conductor for Alice.")
                    .hasSize(7);
        }

        @Test
        void givesOnlyTheNewcomerAFirstOfTheLength() {
            List<String> withGina = new ArrayList<>(SIX.subList(0, 5));
            withGina.add("Gina");
            Performance second = quarter(10).band(withGina).build();
            assertThat(suggestFootnotes(second, List.of(quarter(0).build())))
                    .containsExactly("First quarter peal for Gina.");
        }

        @Test
        void marksAFirstOfTheMethodForTheRingersWhoHaveRungOthers() {
            Performance cambridge = quarter(10)
                    .title("Cambridge Surprise Minor")
                    .conductor("Bob")
                    .build();
            assertThat(suggestFootnotes(cambridge, List.of(quarter(0).build())))
                    .contains(
                            "First quarter peal of Cambridge Surprise Minor for Alice.",
                            "First quarter peal as conductor for Bob.")
                    .hasSize(7);
        }

        @Test
        void marksTheTwentyFifth() {
            List<Performance> earlier = new ArrayList<>();
            for (int day = 0; day < 24; day += 1) {
                earlier.add(quarter(day).build());
            }
            assertThat(suggestFootnotes(quarter(30).build(), earlier))
                    .containsExactly(
                            "25th quarter peal for Alice.",
                            "25th quarter peal for Bob.",
                            "25th quarter peal for Carol.",
                            "25th quarter peal for Dan.",
                            "25th quarter peal for Erin.",
                            "25th quarter peal for Frank.");
        }

        @Test
        void marksAFirstOnHandbells() {
            Performance inHand =
                    quarter(10).handbells(true).band("Alice", "Bob", "Carol").build();
            assertThat(suggestFootnotes(inHand, List.of(quarter(0).build())))
                    .containsExactly(
                            "First quarter peal on handbells for Alice.",
                            "First quarter peal on handbells for Bob.",
                            "First quarter peal on handbells for Carol.");
        }

        @Test
        void countsOnlyWhatCameBeforeAndOfTheSameLength() {
            Performance peal = quarter(5).changes(5040).build();
            Performance sameDay = quarter(10).build();
            assertThat(suggestFootnotes(quarter(10).build(), List.of(peal, sameDay)))
                    .hasSize(7);
        }

        @Test
        void leavesOutAFootnoteTheRecordAlreadyCarries() {
            Performance noted =
                    quarter(0).footnotes(List.of("First quarter peal for Bob.")).build();
            assertThat(suggestFootnotes(noted, List.of()))
                    .doesNotContain("First quarter peal for Bob.")
                    .hasSize(6);
        }

        @Test
        void suggestsNothingForATouch() {
            assertThat(suggestFootnotes(quarter(0).changes(720).build(), List.of()))
                    .isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAPerformance() {
            assertThatThrownBy(() -> suggestFootnotes(null, List.of())).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Milestones {

        @Test
        void areTheTwentyFifthTheFiftiethAndEveryHundredth() {
            assertThat(isMilestone(25)).isTrue();
            assertThat(isMilestone(50)).isTrue();
            assertThat(isMilestone(100)).isTrue();
            assertThat(isMilestone(700)).isTrue();
            assertThat(isMilestone(75)).isFalse();
            assertThat(isMilestone(150)).isFalse();
            assertThat(isMilestone(0)).isFalse();
        }
    }
}
