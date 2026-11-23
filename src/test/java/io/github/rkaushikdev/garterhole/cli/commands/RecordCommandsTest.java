package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.commands.RecordCommands.RECORD_FLAGS;
import static io.github.rkaushikdev.garterhole.cli.commands.RecordCommands.fileRecord;
import static io.github.rkaushikdev.garterhole.cli.commands.RecordCommands.listRecords;
import static io.github.rkaushikdev.garterhole.cli.commands.RecordCommands.printBoard;
import static io.github.rkaushikdev.garterhole.cli.commands.RecordCommands.reportRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.Args;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.Output;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.FixedClock;
import io.github.rkaushikdev.garterhole.util.Json;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordCommandsTest {

    private static final String QUARTER_LINE =
            "--tower StMary --title Plain Bob Minor --changes 1260 --band Alice,Bob,Carol,Dan,Erin,Frank";

    private static final Clock TODAY = new FixedClock(Instant.parse("2025-05-18T00:00:00Z"));

    @TempDir
    Path directory;

    private Service service;

    @BeforeEach
    void openTheStore() {
        service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
    }

    private static ParsedArgs args(String line) {
        return Args.parseArgs(List.of(line.split(" ")), RECORD_FLAGS);
    }

    /** A context whose clock says it is the eighteenth of May 2025. */
    private CommandContext onTheDay(Output.Collected collected) {
        return new CommandContext(service, collected.output(), Map.of(), TODAY);
    }

    /** A context on the real clock. */
    private CommandContext context(Output.Collected collected) {
        return new CommandContext(service, collected.output());
    }

    private Service emptyService() {
        return new Service(new Repository(new Journal(directory.resolve("other.jsonl"))));
    }

    @Nested
    class RecordsFile {

        @Test
        void filesAQuarterAndSaysWhereItWent() {
            Output.Collected collected = Output.collecting();
            fileRecord(args(QUARTER_LINE), onTheDay(collected));
            assertThat(collected.err().get(collected.err().size() - 1)).contains("filed as");
            assertThat(service.listPerformances()).hasSize(1);
        }

        @Test
        void mentionsTheFootnotesTheQuarterHasEarned() {
            Output.Collected collected = Output.collecting();
            fileRecord(args(QUARTER_LINE), onTheDay(collected));
            assertThat(collected.err()).contains("footnote it has earned: First quarter peal for Bob.");
        }

        @Test
        void usesTodayWhenNoDateIsGiven() {
            fileRecord(args(QUARTER_LINE), onTheDay(Output.collecting()));
            assertThat(service.listPerformances().get(0).get("date").asText()).isEqualTo("2025-05-18");
        }

        @Test
        void takesADateWhenOneIsGiven() {
            fileRecord(args(QUARTER_LINE + " --date 2025-03-09"), onTheDay(Output.collecting()));
            assertThat(service.listPerformances().get(0).get("date").asText()).isEqualTo("2025-03-09");
        }

        @Test
        void makesTheFirstOfTheBandTheConductorByDefault() {
            fileRecord(args(QUARTER_LINE), onTheDay(Output.collecting()));
            assertThat(service.listPerformances().get(0).get("conductor").asText())
                    .isEqualTo("Alice");
        }

        @Test
        void takesAConductorWhenOneIsNamed() {
            fileRecord(args(QUARTER_LINE + " --conductor Carol"), onTheDay(Output.collecting()));
            assertThat(service.listPerformances().get(0).get("conductor").asText())
                    .isEqualTo("Carol");
        }

        @Test
        void refusesABandThatIsTheWrongSizeForTheMethod() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> fileRecord(
                            args("--tower StMary --title Plain Bob Minor --changes 1260 --band Alice,Bob"),
                            onTheDay(collected)))
                    .hasMessageContaining("needs a band of 6");
        }

        @Test
        void refusesADateWrittenAnyOtherWay() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> fileRecord(args(QUARTER_LINE + " --date 18/05/2025"), context(collected)))
                    .hasMessageContaining("must be a date like");
        }

        @Test
        void refusesACommandWithNoBand() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> fileRecord(
                            args("--tower StMary --title Plain Bob Minor --changes 1260"), onTheDay(collected)))
                    .hasMessageContaining("needs --band");
        }
    }

    @Nested
    class RecordsList {

        @BeforeEach
        void fileTwo() {
            fileRecord(args(QUARTER_LINE), onTheDay(Output.collecting()));
            fileRecord(args(QUARTER_LINE + " --changes 5040 --date 2025-06-01"), onTheDay(Output.collecting()));
        }

        @Test
        void printsATableWithAHeading() {
            Output.Collected collected = Output.collecting();
            listRecords(args("list"), context(collected));
            assertThat(collected.out().get(0)).contains("date");
            assertThat(collected.out()).hasSize(3);
        }

        @Test
        void filtersByWhoRang() {
            Output.Collected collected = Output.collecting();
            listRecords(args("list --ringer Nobody"), context(collected));
            assertThat(collected.out()).isEmpty();
        }

        @Test
        void filtersByLengthAndByDate() {
            Output.Collected peals = Output.collecting();
            listRecords(args("list --kind peal"), context(peals));
            assertThat(peals.out()).hasSize(2);
            Output.Collected june = Output.collecting();
            listRecords(args("list --from 2025-05-20 --to 2025-07-01"), context(june));
            assertThat(june.out()).hasSize(2).last().asString().contains("5040");
        }

        @Test
        void refusesALengthItDoesNotKnow() {
            assertThatThrownBy(() -> listRecords(args("list --kind extent"), context(Output.collecting())))
                    .hasMessageContaining("is not a kind of performance");
        }

        @Test
        void saysSoWhenNothingIsFiled() {
            Output.Collected collected = Output.collecting();
            listRecords(args("list"), new CommandContext(emptyService(), collected.output()));
            assertThat(collected.err().get(0)).isEqualTo("nothing filed yet");
        }

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            listRecords(args("list"), context(collected));
            assertThat(Json.parse(String.join("\n", collected.out()))).hasSize(2);
        }
    }

    @Nested
    class RecordsReport {

        @BeforeEach
        void fileOne() {
            fileRecord(args(QUARTER_LINE), onTheDay(Output.collecting()));
        }

        @Test
        void writesATextReportByDefault() {
            Output.Collected collected = Output.collecting();
            reportRecords(args("report"), context(collected));
            assertThat(String.join("\n", collected.out())).contains("By tower");
        }

        @Test
        void writesASpreadsheetWhenAsked() {
            Output.Collected collected = Output.collecting();
            reportRecords(args("report --format csv"), context(collected));
            assertThat(String.join("\n", collected.out())).contains("date,tower,title");
        }

        @Test
        void refusesAFormatNobodyWrote() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> reportRecords(args("report --format pdf"), context(collected)))
                    .hasMessageContaining("is not a format");
        }
    }

    @Nested
    class RecordsBoard {

        @BeforeEach
        void fileOne() {
            fileRecord(args(QUARTER_LINE), onTheDay(Output.collecting()));
        }

        @Test
        void printsOneTheWayItGoesOnABoard() {
            Output.Collected collected = Output.collecting();
            printBoard(args("board"), context(collected));
            assertThat(collected.out().get(0)).isEqualTo("StMary");
            assertThat(collected.out()).contains("1  Alice (c)");
        }

        @Test
        void complainsWhenThereIsNothingToPrint() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> printBoard(args("board"), new CommandContext(emptyService(), collected.output())))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
