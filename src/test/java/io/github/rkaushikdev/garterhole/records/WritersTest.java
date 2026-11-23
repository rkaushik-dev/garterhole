package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.Writers.formats;
import static io.github.rkaushikdev.garterhole.records.Writers.reportLines;
import static io.github.rkaushikdev.garterhole.records.Writers.writerNamed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Writers.Options;
import io.github.rkaushikdev.garterhole.records.Writers.Writer;
import io.github.rkaushikdev.garterhole.util.Json;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WritersTest {

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

    private static List<Performance> records() {
        return List.of(
                quarter().build(),
                quarter().changes(5040).conductor("Bob").build(),
                quarter()
                        .tower("All Saints")
                        .title("Cambridge Surprise Minor")
                        .changes(720)
                        .build());
    }

    @Nested
    class ReportLines {

        @Test
        void opensWithAHeadingItWasGiven() {
            List<String> lines = reportLines(records(), Options.DEFAULT.withTitle("Ringing in 2025"));
            assertThat(lines.get(0)).isEqualTo("Ringing in 2025");
            assertThat(lines.get(1)).isEqualTo("===============");
        }

        @Test
        void hasADefaultHeading() {
            assertThat(reportLines(records()).get(0)).isEqualTo("Performances");
        }

        @Test
        void countsThePerformancesByKind() {
            assertThat(reportLines(records()).get(3)).isEqualTo("3 performances: 1 peal, 1 quarter peal and 1 touch");
        }

        @Test
        void addsTheChangesUp() {
            assertThat(reportLines(records()).get(4)).isEqualTo((1260 + 5040 + 720) + " changes altogether");
        }

        @Test
        void hasATableForEachOfTheTowersAndTheMethods() {
            List<String> lines = reportLines(records());
            assertThat(lines).contains("By tower");
            assertThat(lines).contains("By method");
            assertThat(lines).contains("Most rung by");
        }

        @Test
        void splitsTheRecordsByClassAfterTheMethods() {
            List<String> lines = reportLines(records());
            int byMethod = lines.indexOf("By method");
            int byClass = lines.indexOf("By class");
            assertThat(byClass).isGreaterThan(byMethod).isLessThan(lines.indexOf("Most rung by"));
            assertThat(lines.get(byClass + 1)).startsWith("Bob").endsWith("2");
            assertThat(lines.get(byClass - 1)).isEmpty();
        }

        @Test
        void leavesOffTheBottomOfTheRingerTableWhenAskedForANegativeCount() {
            List<String> all = reportLines(records(), Options.DEFAULT.withTopRingers(100));
            List<String> fewer = reportLines(records(), Options.DEFAULT.withTopRingers(-2));
            assertThat(all.size() - fewer.size()).isEqualTo(2);
        }

        @Test
        void stopsTheRingerTableAtTheNumberAskedFor() {
            List<String> lines = reportLines(records(), Options.DEFAULT.withTopRingers(2));
            int start = lines.indexOf("Most rung by");
            assertThat(lines.subList(start + 1, lines.size())).hasSize(2);
        }

        @Test
        void writesAReportForNothingAtAll() {
            assertThat(reportLines(List.of()).get(3))
                    .isEqualTo("0 performances: 0 peals, 0 quarter peals and 0 touches");
        }
    }

    @Nested
    class TextWriter {

        @Test
        void joinsTheReportIntoOneString() {
            String written = Writer.TEXT.write(records());
            assertThat(written.split("\n")[0]).isEqualTo("Performances");
            assertThat(written).contains("By tower");
        }

        @Test
        void saysWhatItProduces() {
            assertThat(Writer.TEXT.contentType()).isEqualTo("text/plain");
        }
    }

    @Nested
    class ListWriter {

        @Test
        void writesOneLinePerPerformance() {
            assertThat(Writer.LIST.write(records()).split("\n")).hasSize(3);
        }

        @Test
        void writesNothingForNothing() {
            assertThat(Writer.LIST.write(List.of())).isEmpty();
        }
    }

    @Nested
    class CsvWriter {

        @Test
        void opensWithAHeaderRow() {
            assertThat(Writer.CSV.write(records()).split("\n")[0])
                    .isEqualTo("date,tower,title,changes,kind,conductor,composer,band");
        }

        @Test
        void writesOneRowPerPerformance() {
            assertThat(Writer.CSV.write(records()).split("\n")).hasSize(4);
        }

        @Test
        void separatesTheBandWithSemicolonsSoTheCommasStayOutOfTheWay() {
            assertThat(Writer.CSV.write(List.of(quarter().build()))).contains("Alice; Bob; Carol; Dan; Erin; Frank");
        }

        @Test
        void quotesACellWithACommaInIt() {
            String written = Writer.CSV.write(
                    List.of(quarter().tower("St Mary, Bridgend").build()));
            assertThat(written).contains("\"St Mary, Bridgend\"");
        }

        @Test
        void doublesAQuoteInsideACell() {
            String written = Writer.CSV.write(
                    List.of(quarter().tower("The \"Old\" Church").build()));
            assertThat(written).contains("\"The \"\"Old\"\" Church\"");
        }

        @Test
        void leavesAMissingConductorEmpty() {
            String written = Writer.CSV.write(List.of(quarter().conductor(null).build()));
            assertThat(written.split("\n")[1]).contains(",,");
        }
    }

    @Nested
    class JsonWriter {

        @Test
        void writesSomethingThatReadsBack() {
            JsonNode parsed = Json.parse(Writer.JSON.write(records()));
            assertThat(parsed).hasSize(3);
            assertThat(parsed.get(0).get("title").asText()).isEqualTo("Plain Bob Minor");
            assertThat(parsed.get(0).get("band")).isEqualTo(Json.toNode(SIX));
        }

        @Test
        void writesAMissingConductorAsNull() {
            JsonNode parsed = Json.parse(
                    Writer.JSON.write(List.of(quarter().conductor(null).build())));
            assertThat(parsed.get(0).get("conductor").isNull()).isTrue();
        }

        @Test
        void writesAnEmptyList() {
            assertThat(Json.parse(Writer.JSON.write(List.of()))).isEqualTo(Json.array());
        }
    }

    @Nested
    class ChoosingAWriter {

        @Test
        void findsOneByName() {
            assertThat(writerNamed("csv")).isSameAs(Writer.CSV);
            assertThat(writerNamed("json")).isSameAs(Writer.JSON);
        }

        @Test
        void refusesAFormatNobodyWrote() {
            assertThatThrownBy(() -> writerNamed("pdf")).hasMessageContaining("is not a format");
            assertThatThrownBy(() -> writerNamed("pdf")).isInstanceOf(ValidationException.class);
        }

        @Test
        void listsTheFormatsItKnows() {
            assertThat(formats()).containsExactly("text", "list", "csv", "json");
        }
    }
}
