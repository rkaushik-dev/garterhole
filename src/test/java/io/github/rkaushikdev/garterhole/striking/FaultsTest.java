package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.Fault.Kind.CLIP;
import static io.github.rkaushikdev.garterhole.striking.Fault.Kind.DRIFT;
import static io.github.rkaushikdev.garterhole.striking.Fault.Kind.GAP;
import static io.github.rkaushikdev.garterhole.striking.Faults.countFaults;
import static io.github.rkaushikdev.garterhole.striking.Faults.describeFault;
import static io.github.rkaushikdev.garterhole.striking.Faults.faultsByBell;
import static io.github.rkaushikdev.garterhole.striking.Faults.findFaults;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FaultsTest {

    /**
     * A blow struck where the error puts it, with the bells of a row a fifth
     * of a second apart and the rows a second apart, so that only an error
     * this large brings two blows together.
     */
    private static BlowError blow(int bell, int rowIndex, double error) {
        double expected = rowIndex * 1000.0 + bell * 200;
        return new BlowError(bell, rowIndex, 1, expected + error, expected, error);
    }

    private static List<BlowError> run(int bell, int rows, double error) {
        return IntStream.range(0, rows).mapToObj(row -> blow(bell, row, error)).toList();
    }

    private static boolean hasDrift(List<Fault> faults) {
        return faults.stream().anyMatch(fault -> fault.kind() == DRIFT);
    }

    @Nested
    class FindFaults {

        @Test
        void findsNothingInCleanRinging() {
            assertThat(findFaults(List.of(blow(1, 0, 0), blow(2, 0, 10)))).isEmpty();
        }

        @Test
        void namesABlowStruckTooSoonAClip() {
            List<Fault> faults = findFaults(List.of(blow(3, 2, -80)));
            assertThat(faults).hasSize(1);
            assertThat(faults.get(0).kind()).isEqualTo(CLIP);
            assertThat(faults.get(0).bell()).isEqualTo(3);
            assertThat(faults.get(0).size()).isEqualTo(-80);
        }

        @Test
        void namesABlowLeftTooLateAGap() {
            assertThat(findFaults(List.of(blow(3, 2, 80))).get(0).kind()).isEqualTo(GAP);
        }

        @Test
        void leavesABlowInsideTheThresholdAlone() {
            assertThat(findFaults(List.of(blow(3, 2, 40)))).isEmpty();
            assertThat(findFaults(List.of(blow(3, 2, -40)))).isEmpty();
        }

        @Test
        void takesThresholdsOfItsOwn() {
            assertThat(findFaults(List.of(blow(3, 2, 40)), Thresholds.DEFAULT.withGap(30)))
                    .hasSize(1);
        }

        @Test
        void sortsTheFaultsIntoRingingOrder() {
            List<Fault> faults = findFaults(List.of(blow(2, 5, 90), blow(1, 1, 90)));
            assertThat(faults).extracting(Fault::rowIndex).containsExactly(1, 5);
        }

        @Test
        void refusesSomethingThatIsNotAListOfErrors() {
            assertThatThrownBy(() -> findFaults(null)).hasMessageContaining("list of blow errors");
        }

        @Test
        void refusesAThresholdThatIsNotAPositiveNumber() {
            assertThatThrownBy(() -> findFaults(List.of(), Thresholds.DEFAULT.withGap(0)))
                    .hasMessageContaining("must be a positive number");
            assertThatThrownBy(() -> findFaults(List.of(), Thresholds.DEFAULT.withClip(-1)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Clash {

        @Test
        void namesABlowStruckOnTopOfTheOneInFront() {
            List<Fault> faults = findFaults(List.of(blow(1, 0, 0), blow(2, 0, -180)));
            assertThat(faults).containsExactly(new Fault(Fault.Kind.CLASH, 2, 0, 1, 20));
            assertThat(describeFault(faults.get(0)))
                    .isEqualTo("bell 2 clashed with the bell in front, 20ms apart in row 1");
        }

        @Test
        void namesTheClashRatherThanTheClipItAlsoIs() {
            List<Fault> faults = findFaults(List.of(blow(1, 0, 0), blow(2, 0, -195)));
            assertThat(faults).extracting(Fault::kind).containsExactly(Fault.Kind.CLASH);
        }

        @Test
        void leavesTwoBlowsFurtherApartThanTheMarginAlone() {
            assertThat(findFaults(List.of(blow(1, 0, 0), blow(2, 0, -160)), Thresholds.DEFAULT.withClip(500)))
                    .isEmpty();
            assertThat(findFaults(
                            List.of(blow(1, 0, 0), blow(2, 0, -160)),
                            Thresholds.DEFAULT.withClash(50).withClip(500)))
                    .extracting(Fault::kind)
                    .containsExactly(Fault.Kind.CLASH);
        }

        @Test
        void countsAClashAmongTheKinds() {
            assertThat(countFaults(List.of())).containsEntry(Fault.Kind.CLASH, 0);
            assertThat(Fault.Kind.CLASH).hasToString("clash");
        }

        @Test
        void keepsTheUsualMarginWhenTheOtherThresholdsAreGiven() {
            assertThat(new Thresholds(50, 50, 30, 4).clash()).isEqualTo(Thresholds.DEFAULT_CLASH);
            assertThatThrownBy(() -> Thresholds.DEFAULT.withClash(0)).hasMessageContaining("clash threshold");
        }
    }

    @Nested
    class Drift {

        @Test
        void reportsARunOfBlowsOnTheSameSide() {
            assertThat(hasDrift(findFaults(run(4, 4, 40)))).isTrue();
        }

        @Test
        void needsTheRunToBeLongEnough() {
            assertThat(hasDrift(findFaults(run(4, 3, 40)))).isFalse();
        }

        @Test
        void breaksARunWhenTheBellComesBack() {
            List<BlowError> errors =
                    List.of(blow(4, 0, 40), blow(4, 1, 40), blow(4, 2, 0), blow(4, 3, 40), blow(4, 4, 40));
            assertThat(hasDrift(findFaults(errors))).isFalse();
        }

        @Test
        void breaksARunWhenTheBellChangesSide() {
            List<BlowError> errors = List.of(blow(4, 0, 40), blow(4, 1, 40), blow(4, 2, -40), blow(4, 3, -40));
            assertThat(hasDrift(findFaults(errors))).isFalse();
        }

        @Test
        void keepsEachBellSeparate() {
            List<BlowError> errors = IntStream.range(0, 4)
                    .boxed()
                    .flatMap(row -> Stream.of(blow(4, row, 40), blow(5, row, 0)))
                    .toList();
            List<Fault> drifts = findFaults(errors).stream()
                    .filter(fault -> fault.kind() == DRIFT)
                    .toList();
            assertThat(drifts).hasSize(1);
            assertThat(drifts.get(0).bell()).isEqualTo(4);
        }

        @Test
        void measuresADriftInBlowsRatherThanMilliseconds() {
            Fault drift = findFaults(run(4, 5, 40)).stream()
                    .filter(fault -> fault.kind() == DRIFT)
                    .findFirst()
                    .orElseThrow();
            assertThat(drift.size()).isEqualTo(5);
        }
    }

    @Nested
    class CountingAndGrouping {

        private List<Fault> faults() {
            return findFaults(List.of(blow(2, 0, 90), blow(2, 1, -90), blow(3, 2, 90)));
        }

        @Test
        void countsEachKind() {
            assertThat(countFaults(faults())).isEqualTo(Map.of(CLIP, 1, GAP, 2, DRIFT, 0, Fault.Kind.CLASH, 0));
        }

        @Test
        void countsNothingForNoFaults() {
            assertThat(countFaults(List.of())).isEqualTo(Map.of(CLIP, 0, GAP, 0, DRIFT, 0, Fault.Kind.CLASH, 0));
        }

        @Test
        void groupsByBellInOrder() {
            Map<Integer, List<Fault>> byBell = faultsByBell(faults());
            assertThat(byBell.keySet()).containsExactly(2, 3);
            assertThat(byBell.get(2)).hasSize(2);
        }
    }

    @Nested
    class DescribeFault {

        @Test
        void describesAClip() {
            assertThat(describeFault(new Fault(CLIP, 3, 4, 1, -82))).isEqualTo("bell 3 clipped by 82ms in row 5");
        }

        @Test
        void describesAGap() {
            assertThat(describeFault(new Fault(GAP, 3, 4, 1, 82))).isEqualTo("bell 3 left a 82ms gap in row 5");
        }

        @Test
        void describesADriftInBlows() {
            assertThat(describeFault(new Fault(DRIFT, 3, 4, 1, 6))).isEqualTo("bell 3 drifted for 6 blows from row 5");
        }
    }

    @Nested
    class DefaultThresholds {

        @Test
        void isFrozenSoNobodyEditsItByAccident() {
            assertThat(Thresholds.class.isRecord()).isTrue();
            Thresholds.DEFAULT.withGap(10);
            assertThat(Thresholds.DEFAULT).isEqualTo(new Thresholds(50, 50, 30, 4));
        }
    }
}
