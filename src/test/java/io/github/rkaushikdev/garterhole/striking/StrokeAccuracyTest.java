package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.Ringing.courseStrikes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Stroke;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.striking.StrokeAccuracy.OddStruck;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrokeAccuracyTest {

    /** The plain course with the three a steady forty milliseconds late at every handstroke. */
    private static StrikingSession oddThree() {
        return StrikingSession.analyse(6, courseStrikes((bell, row) -> bell == 3 && row % 2 == 0 ? 40 : 0));
    }

    private static List<BlowError> blows(double... errors) {
        List<BlowError> blows = new java.util.ArrayList<>();
        for (int index = 0; index < errors.length; index += 1) {
            blows.add(new BlowError(1, index, 1, 0, 0, errors[index]));
        }
        return blows;
    }

    @Nested
    class ByStroke {

        @Test
        void startsTheRecordingAtAHandstroke() {
            assertThat(StrokeAccuracy.strokeOf(0)).isEqualTo(Stroke.HANDSTROKE);
            assertThat(StrokeAccuracy.strokeOf(1)).isEqualTo(Stroke.BACKSTROKE);
        }

        @Test
        void summarisesEachStrokeOnItsOwn() {
            Map<Stroke, Summary> strokes = StrokeAccuracy.byStroke(blows(30, 0, -30, 0));
            assertThat(strokes.keySet()).containsExactly(Stroke.HANDSTROKE, Stroke.BACKSTROKE);
            assertThat(strokes.get(Stroke.HANDSTROKE).rms()).isEqualTo(30);
            assertThat(strokes.get(Stroke.HANDSTROKE).blows()).isEqualTo(2);
            assertThat(strokes.get(Stroke.BACKSTROKE).rms()).isZero();
        }

        @Test
        void leavesOutAStrokeWithNothingRungAtIt() {
            assertThat(StrokeAccuracy.byStroke(blows(10)).keySet()).containsExactly(Stroke.HANDSTROKE);
            assertThat(StrokeAccuracy.byStroke(List.of())).isEmpty();
        }
    }

    @Nested
    class OddStruckBells {

        @Test
        void findsTheBellThatIsLateAtOneStrokeOnly() {
            List<OddStruck> found = oddThree().oddStruck();
            assertThat(found).extracting(OddStruck::bell).containsExactly(3);
            assertThat(found.get(0).lateStroke()).isEqualTo(Stroke.HANDSTROKE);
            assertThat(found.get(0).difference()).isGreaterThan(30);
        }

        @Test
        void findsNothingInEvenRinging() {
            assertThat(StrikingSession.analyse(6, courseStrikes()).oddStruck()).isEmpty();
        }

        @Test
        void namesTheBackstrokeWhenThatIsTheLateOne() {
            List<OddStruck> found = StrokeAccuracy.oddStruck(blows(0, 50, 0, 50), 20);
            assertThat(found).containsExactly(new OddStruck(1, 0, 50));
            assertThat(found.get(0).lateStroke()).isEqualTo(Stroke.BACKSTROKE);
        }

        @Test
        void ignoresABellRungAtOneStrokeAndADifferenceInsideTheMargin() {
            assertThat(StrokeAccuracy.oddStruck(blows(90), 20)).isEmpty();
            assertThat(StrokeAccuracy.oddStruck(blows(0, 15, 0, 15), 20)).isEmpty();
        }

        @Test
        void refusesANegativeMargin() {
            assertThatThrownBy(() -> StrokeAccuracy.oddStruck(blows(0), -1)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class InTheReport {

        @Test
        void gradesTheTwoStrokesSeparately() {
            String line = StrikingReport.strokeLine(StrikingSession.analyse(6, courseStrikes()));
            assertThat(line).isEqualTo("handstrokes excellent at 0ms, backstrokes excellent at 0ms");
        }

        @Test
        void tellsTheRingerOfAnOddStruckBellWhichStrokeToWatch() {
            List<String> lines = StrikingReport.reportLines(oddThree());
            assertThat(lines).anyMatch(line -> line.matches("bell 3 is odd struck, \\d+ms late at handstroke"));
            assertThat(StrikingReport.describeOddStruck(new OddStruck(10, 0, 35)))
                    .isEqualTo("bell 0 is odd struck, 35ms late at backstroke");
        }

        @Test
        void tellsWhichRowsWereStruckAtWhichStroke() {
            StrikingSession session = StrikingSession.analyse(6, courseStrikes());
            assertThat(RowSplitter.strokes(session.rows()).subList(0, 3))
                    .containsExactly(Stroke.HANDSTROKE, Stroke.BACKSTROKE, Stroke.HANDSTROKE);
        }
    }
}
