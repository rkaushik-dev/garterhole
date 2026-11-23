package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.Ringing.courseStrikes;
import static io.github.rkaushikdev.garterhole.striking.StrikingReport.bellTable;
import static io.github.rkaushikdev.garterhole.striking.StrikingReport.faultLines;
import static io.github.rkaushikdev.garterhole.striking.StrikingReport.headline;
import static io.github.rkaushikdev.garterhole.striking.StrikingReport.reportLines;
import static io.github.rkaushikdev.garterhole.striking.StrikingReport.tendency;
import static io.github.rkaushikdev.garterhole.striking.StrikingReport.verdict;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.StrikingSession.Options;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrikingReportTest {

    private static StrikingSession clean() {
        return StrikingSession.analyse(6, courseStrikes());
    }

    private static StrikingSession ragged() {
        return StrikingSession.analyse(6, courseStrikes((bell, row) -> bell == 4 ? 120 : 0));
    }

    @Nested
    class Verdict {

        @Test
        void gradesTheRinging() {
            assertThat(verdict(5)).isEqualTo("excellent");
            assertThat(verdict(30)).isEqualTo("good");
            assertThat(verdict(55)).isEqualTo("fair");
            assertThat(verdict(120)).isEqualTo("ragged");
        }

        @Test
        void gradesOnTheBoundaryAsTheWorseGrade() {
            assertThat(StrikingReport.Grade.of(19.9)).isEqualTo(StrikingReport.Grade.EXCELLENT);
            assertThat(StrikingReport.Grade.of(20)).isEqualTo(StrikingReport.Grade.GOOD);
            assertThat(StrikingReport.Grade.of(70)).isEqualTo(StrikingReport.Grade.RAGGED);
            assertThat(StrikingReport.Grade.FAIR).hasToString("fair");
        }

        @Test
        void refusesSomethingThatIsNotAnAccuracy() {
            assertThatThrownBy(() -> verdict(-1)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> verdict(-0.5)).hasMessageContaining("is not a striking accuracy");
        }
    }

    @Nested
    class Headline {

        @Test
        void opensWithHowMuchWasRungAndHowWell() {
            assertThat(headline(clean())).isEqualTo("61 rows rung, excellent striking at 0ms");
        }

        @Test
        void gradesPoorRingingLower() {
            assertThat(headline(ragged())).doesNotContain("excellent");
        }

        @Test
        void refusesSomethingThatIsNotASession() {
            assertThatThrownBy(() -> headline(null)).hasMessageContaining("a StrikingSession is needed");
        }
    }

    @Nested
    class Tendency {

        @Test
        void callsASmallMeanErrorEven() {
            assertThat(tendency(0)).isEqualTo("even");
            assertThat(tendency(-8)).isEqualTo("even");
        }

        @Test
        void namesTheDirectionOfALargerOne() {
            assertThat(tendency(-45)).isEqualTo("45ms early");
            assertThat(tendency(45)).isEqualTo("45ms late");
        }
    }

    @Nested
    class BellTable {

        @Test
        void hasAHeadingAndALinePerBell() {
            List<String> table = bellTable(clean());
            assertThat(table).hasSize(7);
            assertThat(table.get(0)).startsWith("bell");
        }

        @Test
        void namesEveryBellOfTheStage() {
            List<String> table = bellTable(clean());
            assertThat(table.get(1)).startsWith("1");
            assertThat(table.get(6)).startsWith("6");
        }
    }

    @Nested
    class FaultLines {

        @Test
        void saysSoWhenThereIsNothingToReport() {
            assertThat(faultLines(clean())).containsExactly("no faults worth reporting");
        }

        @Test
        void describesTheFaultsItFound() {
            assertThat(faultLines(ragged()).get(0)).contains("bell 4");
        }

        @Test
        void stopsAtTheLimitAndSaysHowManyWereLeft() {
            List<String> lines = faultLines(ragged(), 3);
            assertThat(lines).hasSize(4);
            assertThat(lines.get(3)).startsWith("and ");
        }

        @Test
        void refusesSomethingThatIsNotASession() {
            assertThatThrownBy(() -> faultLines(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ReportLines {

        @Test
        void putsTheHeadlineFirstAndTheTableAfter() {
            List<String> lines = reportLines(clean());
            assertThat(lines.get(0)).contains("rows rung");
            assertThat(lines.get(1)).isEmpty();
            assertThat(lines.get(2)).startsWith("bell");
        }

        @Test
        void mentionsARecordingThatStoppedMakingSense() {
            List<Strike> strikes = new ArrayList<>(courseStrikes().subList(0, 12));
            strikes.addAll(List.of(
                    new Strike(1, 90000),
                    new Strike(1, 90250),
                    new Strike(3, 90500),
                    new Strike(4, 90750),
                    new Strike(5, 91000),
                    new Strike(6, 91250)));
            StrikingSession session =
                    StrikingSession.analyse(6, strikes, Options.defaults().withLenient(true));
            assertThat(reportLines(session).get(1)).isEqualTo("the recording stopped making sense at row 3");
        }
    }
}
