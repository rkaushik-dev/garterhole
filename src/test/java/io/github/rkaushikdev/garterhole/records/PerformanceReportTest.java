package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.PerformanceReport.boardLines;
import static io.github.rkaushikdev.garterhole.records.PerformanceReport.summaryLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PerformanceReportTest {

    private static final List<String> SIX = List.of("Alice", "Bob", "Carol", "Dan", "Erin", "Frank");

    private static Performance.Builder quarter() {
        return Performance.builder()
                .tower("St Mary")
                .date(LocalDate.of(2025, 5, 18))
                .title("Plain Bob Minor")
                .changes(1260)
                .band(SIX)
                .conductor("Alice");
    }

    @Nested
    class BoardLines {

        @Test
        void opensWithTheTowerTheDateAndTheLength() {
            List<String> lines = boardLines(quarter().build());
            assertThat(lines.get(0)).isEqualTo("St Mary");
            assertThat(lines.get(1)).isEqualTo("2025-05-18");
            assertThat(lines.get(2)).isEqualTo("1260 Plain Bob Minor");
        }

        @Test
        void listsTheBandOneBellToALine() {
            List<String> lines = boardLines(quarter().build());
            assertThat(lines).contains("1  Alice (c)");
            assertThat(lines).contains("6  Frank");
        }

        @Test
        void listsAHandbellBandOnePairToALine() {
            List<String> lines = boardLines(quarter()
                    .title("Plain Bob Royal")
                    .changes(1260)
                    .handbells(true)
                    .band("Alice", "Bob", "Carol", "Dan", "Erin")
                    .build());
            assertThat(lines.get(3)).isEqualTo("on handbells");
            assertThat(lines).contains("1-2  Alice (c)", "3-4  Bob", "9-0  Erin");
        }

        @Test
        void marksTheConductor() {
            assertThat(boardLines(quarter().build()))
                    .filteredOn(line -> line.contains("(c)"))
                    .hasSize(1);
        }

        @Test
        void marksNobodyWhenThereIsNoConductor() {
            assertThat(boardLines(quarter().conductor(null).build())).noneMatch(line -> line.contains("(c)"));
        }

        @Test
        void namesTheComposerWhenThereIsOne() {
            assertThat(boardLines(quarter().composer("A Composer").build())).contains("composed by A Composer");
        }

        @Test
        void givesTheTimeWhenItWasRecorded() {
            assertThat(boardLines(quarter().duration(45).build())).contains("45 minutes");
        }

        @Test
        void printsTheFootnotesAtTheEnd() {
            List<String> lines = boardLines(
                    quarter().footnotes(List.of("First quarter on six.")).build());
            assertThat(lines.get(lines.size() - 1)).isEqualTo("First quarter on six.");
        }

        @Test
        void refusesSomethingThatIsNotAPerformance() {
            assertThatThrownBy(() -> boardLines(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class SummaryLine {

        @Test
        void writesOneLineForAReport() {
            assertThat(summaryLine(quarter().build()))
                    .isEqualTo("2025-05-18  St Mary  1260 Plain Bob Minor (c: Alice)");
        }

        @Test
        void leavesTheConductorOutWhenThereIsNone() {
            assertThat(summaryLine(quarter().conductor(null).build()))
                    .isEqualTo("2025-05-18  St Mary  1260 Plain Bob Minor");
        }
    }
}
