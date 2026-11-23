package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.PerformanceValidator.acceptableBandSizes;
import static io.github.rkaushikdev.garterhole.records.PerformanceValidator.describeVerdict;
import static io.github.rkaushikdev.garterhole.records.PerformanceValidator.speedWarnings;
import static io.github.rkaushikdev.garterhole.records.PerformanceValidator.validateAll;
import static io.github.rkaushikdev.garterhole.records.PerformanceValidator.validatePerformance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.records.PerformanceValidator.Options;
import io.github.rkaushikdev.garterhole.records.PerformanceValidator.Verdict;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PerformanceValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 9, 1);
    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");
    private static final Options ON_THE_DAY = Options.DEFAULT.withToday(TODAY);

    private static Performance.Builder fields() {
        return Performance.builder()
                .tower("St Mary")
                .date(LocalDate.of(2025, 5, 18))
                .title("Plain Bob Minor")
                .changes(1260)
                .band(SIX)
                .conductor("Alice");
    }

    private static Verdict check(UnaryOperator<Performance.Builder> overrides) {
        return validatePerformance(overrides.apply(fields()).build(), ON_THE_DAY);
    }

    private static Verdict check(UnaryOperator<Performance.Builder> overrides, Lengths.Kind claim) {
        return validatePerformance(overrides.apply(fields()).build(), ON_THE_DAY.withClaim(claim));
    }

    @Nested
    class AcceptableBandSizes {

        @Test
        void needsExactlyTheStageOnAnEvenStage() {
            assertThat(acceptableBandSizes(8)).containsExactly(8);
        }

        @Test
        void wantsOneRingerForEachPairOnHandbells() {
            assertThat(acceptableBandSizes(8, true)).containsExactly(4);
            assertThat(acceptableBandSizes(7, true)).containsExactly(4);
            assertThat(acceptableBandSizes(5, true)).containsExactly(3);
            assertThat(acceptableBandSizes(12, true)).containsExactly(6);
            assertThat(acceptableBandSizes(6, false)).containsExactly(6);
        }

        @Test
        void allowsACoverOnAnOddStage() {
            assertThat(acceptableBandSizes(5)).containsExactly(5, 6);
        }
    }

    @Nested
    class ValidatePerformance {

        @Test
        void passesASoundRecord() {
            Verdict verdict = check(rung -> rung);
            assertThat(verdict.ok()).isTrue();
            assertThat(verdict.problems()).isEmpty();
            assertThat(verdict.warnings()).isEmpty();
        }

        @Test
        void passesAHandbellQuarterWithThreeRingersForMinor() {
            Verdict verdict = check(fields -> fields.handbells(true).band("Alice", "Bob", "Carol"));
            assertThat(verdict.problems()).isEmpty();
        }

        @Test
        void catchesAHandbellBandRungAsIfInATower() {
            Verdict verdict = check(fields -> fields.handbells(true));
            assertThat(verdict.problems())
                    .containsExactly("Plain Bob Minor on handbells needs a band of 3 and this one has 6");
        }

        @Test
        void catchesABandOfTheWrongSize() {
            Verdict verdict = check(rung -> rung.band(SIX.subList(0, 5)));
            assertThat(verdict.ok()).isFalse();
            assertThat(verdict.problems().get(0)).isEqualTo("Plain Bob Minor needs a band of 6 and this one has 5");
        }

        @Test
        void allowsACoverOnDoubles() {
            assertThat(check(rung -> rung.title("Plain Bob Doubles").band(SIX).changes(1260))
                            .ok())
                    .isTrue();
            assertThat(check(rung -> rung.title("Plain Bob Doubles")
                                    .band(SIX.subList(0, 5))
                                    .conductor("Alice"))
                            .ok())
                    .isTrue();
        }

        @Test
        void catchesSomebodyDownForTwoBells() {
            Verdict verdict = check(rung -> rung.band("Alice", "Alice", "Carol", "Dan", "Erin", "Frank"));
            assertThat(verdict.problems()).contains("somebody is down for more than one bell");
        }

        @Test
        void warnsWhenNoConductorIsRecorded() {
            Verdict verdict = check(rung -> rung.conductor(null));
            assertThat(verdict.ok()).isTrue();
            assertThat(verdict.warnings()).contains("no conductor is recorded");
        }

        @Test
        void catchesADateInTheFuture() {
            Verdict verdict = check(rung -> rung.date(LocalDate.of(2026, 1, 1)));
            assertThat(verdict.problems().get(0)).isEqualTo("the date 2026-01-01 is in the future");
        }

        @Test
        void checksAClaimedLengthWhenOneIsGiven() {
            Verdict verdict = check(rung -> rung.changes(5000), Lengths.Kind.PEAL);
            assertThat(verdict.problems().get(0)).isEqualTo("a peal on 6 needs at least 5040 changes, not 5000");
        }

        @Test
        void catchesAPealLengthThatIsNotWholeExtents() {
            Verdict verdict = check(rung -> rung.changes(5760), Lengths.Kind.PEAL);
            assertThat(verdict.ok()).isTrue();
            assertThat(check(rung -> rung.changes(5100), Lengths.Kind.PEAL)
                            .problems()
                            .get(0))
                    .contains("whole extents of 720");
        }

        @Test
        void passesAPealOfTheRightLength() {
            assertThat(check(rung -> rung.changes(5040), Lengths.Kind.PEAL).ok())
                    .isTrue();
        }

        @Test
        void givesUpEarlyOnATitleItCannotRead() {
            Verdict verdict = check(rung -> rung.title("Nonsense"));
            assertThat(verdict.ok()).isFalse();
            assertThat(verdict.problems()).hasSize(1);
        }

        @Test
        void refusesSomethingThatIsNotAPerformance() {
            assertThatThrownBy(() -> validatePerformance(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class SpeedWarnings {

        @Test
        void saysNothingWhenNoDurationWasRecorded() {
            assertThat(speedWarnings(fields().build())).isEmpty();
        }

        @Test
        void saysNothingAboutASensibleSpeed() {
            assertThat(speedWarnings(fields().duration(45).build())).isEmpty();
        }

        @Test
        void catchesADurationThatIsTooShortToBeTrue() {
            assertThat(speedWarnings(fields().duration(10).build()).get(0)).contains("faster than anybody");
        }

        @Test
        void catchesADurationThatIsFarTooLong() {
            assertThat(speedWarnings(fields().duration(300).build()).get(0)).contains("slower than anybody");
        }
    }

    @Nested
    class FootnoteWarnings {

        @Test
        void warnsAboutAFirstForSomebodyWhoIsNotInTheBand() {
            Verdict verdict = check(fields -> fields.footnotes(List.of("First quarter peal for Gina.")));
            assertThat(verdict.ok()).isTrue();
            assertThat(verdict.warnings())
                    .containsExactly(
                            "the footnote \"First quarter peal for Gina.\" is for Gina, who is not in the band");
        }

        @Test
        void warnsAboutAMilestoneTooButNotAboutAFirstForSomebodyInTheBand() {
            Performance rung = fields().footnotes(List.of(
                            "First quarter peal for Bob.",
                            "100th quarter peal for Bobby.",
                            "Rung for the birthday of Gina."))
                    .build();
            assertThat(PerformanceValidator.footnoteWarnings(rung))
                    .hasSize(1)
                    .first()
                    .asString()
                    .contains("Bobby");
        }

        @Test
        void leavesAFirstWithNobodyNamedAlone() {
            Performance rung = fields().footnotes(List.of("First quarter peal on the new bells."))
                    .build();
            assertThat(PerformanceValidator.footnoteWarnings(rung)).isEmpty();
        }

        @Test
        void putsTheWarningsAfterTheOnesAboutTheTime() {
            Verdict verdict = check(
                    fields -> fields.conductor(null).duration(10).footnotes(List.of("First quarter peal for Gina!")));
            assertThat(verdict.warnings()).hasSize(3);
            assertThat(verdict.warnings().get(0)).isEqualTo("no conductor is recorded");
            assertThat(verdict.warnings().get(2)).contains("is for Gina");
        }
    }

    @Nested
    class ValidateAll {

        @Test
        void reportsOnlyTheRecordsWithSomethingWrong() {
            List<Performance> records = List.of(
                    fields().build(),
                    fields().band(SIX.subList(0, 5)).build(),
                    fields().conductor(null).build());
            assertThat(validateAll(records, ON_THE_DAY)).hasSize(2);
        }

        @Test
        void reportsNothingWhenEverythingIsSound() {
            assertThat(validateAll(List.of(fields().build()), ON_THE_DAY)).isEmpty();
        }
    }

    @Nested
    class DescribeVerdict {

        @Test
        void saysSoWhenThereIsNothingWrong() {
            assertThat(describeVerdict(check(rung -> rung))).isEqualTo("nothing wrong with it");
        }

        @Test
        void mentionsTheWarningsWhenTheRecordStillPasses() {
            assertThat(describeVerdict(check(rung -> rung.conductor(null))))
                    .isEqualTo("nothing wrong with it, but no conductor is recorded");
        }

        @Test
        void listsTheProblemsWhenItDoesNot() {
            assertThat(describeVerdict(check(rung -> rung.band(SIX.subList(0, 5)))))
                    .contains("needs a band of 6");
        }
    }
}
