package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.commands.TowerCommands.TOWER_FLAGS;
import static io.github.rkaushikdev.garterhole.cli.commands.TowerCommands.addTower;
import static io.github.rkaushikdev.garterhole.cli.commands.TowerCommands.listTowers;
import static io.github.rkaushikdev.garterhole.cli.commands.TowerCommands.showTower;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.Args;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.Output;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.http.routes.TowerRoutes;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.Json;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TowerCommandsTest {

    private static final String SIX_BELLS = "--weights 4-0-0,4-2-7,5-1-14,6-0-0,8-2-21,12-1-4";

    @TempDir
    Path directory;

    private Service service;

    @BeforeEach
    void openTheStore() {
        service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
    }

    private static ParsedArgs args(String line) {
        return Args.parseArgs(List.of(line.split(" ")), TOWER_FLAGS);
    }

    private CommandContext context(Output.Collected collected) {
        return new CommandContext(service, collected.output());
    }

    @Nested
    class TowerAdd {

        @Test
        void filesATowerAndNumbersTheBellsFromTheOrder() {
            Output.Collected collected = Output.collecting();
            addTower(args("--name StMary --place Bridgend " + SIX_BELLS), context(collected));
            assertThat(collected.err().get(0)).isEqualTo("filed as bridgend-stmary");
            assertThat(service.getTower("bridgend-stmary").get("bells").get(0))
                    .isEqualTo(Json.parse("{\"number\":1,\"weight\":\"4-0-0\"}"));
        }

        @Test
        void worksOutWhatTheTowerCanRing() {
            addTower(args("--name StMary " + SIX_BELLS), context(Output.collecting()));
            assertThat(service.getTower("stmary").get("stages")).isEqualTo(Json.parse("[6,5,4,3]"));
        }

        @Test
        void describesItAfterFiling() {
            Output.Collected collected = Output.collecting();
            addTower(args("--name StMary " + SIX_BELLS), context(collected));
            assertThat(collected.out().get(0)).contains("6 bells, tenor 12-1-4");
            assertThat(collected.out()).contains("  6  12-1-4");
        }

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            addTower(args("--name StMary " + SIX_BELLS), context(collected));
            assertThat(Json.parse(String.join("\n", collected.out()))
                            .get("size")
                            .asInt())
                    .isEqualTo(6);
        }

        @Test
        void refusesAWeightWrittenWrongly() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> addTower(args("--name StMary --weights 4-9-0"), context(collected)))
                    .hasMessageContaining("is more than a hundredweight");
        }

        @Test
        void refusesACommandWithNoWeights() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> addTower(args("--name StMary"), context(collected)))
                    .hasMessageContaining("needs --weights");
        }

        @Test
        void refusesACommandWithNoName() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> addTower(args(SIX_BELLS), context(collected)))
                    .hasMessageContaining("needs --name");
        }
    }

    @Nested
    class TowerList {

        @BeforeEach
        void fileTwo() {
            addTower(args("--name StMary --place Bridgend " + SIX_BELLS), context(Output.collecting()));
            addTower(args("--name AllSaints --weights 4-0-0,4-2-7,5-1-14,6-0-0"), context(Output.collecting()));
        }

        @Test
        void printsATableWithAHeading() {
            Output.Collected collected = Output.collecting();
            listTowers(args("list"), context(collected));
            assertThat(collected.out().get(0)).contains("id");
            assertThat(collected.out()).hasSize(3);
        }

        @Test
        void saysWhatEachRingsUpTo() {
            Output.Collected collected = Output.collecting();
            listTowers(args("list"), context(collected));
            assertThat(String.join("\n", collected.out())).contains("Minor");
            assertThat(String.join("\n", collected.out())).contains("Minimus");
        }

        @Test
        void saysSoWhenThereAreNone() {
            Service empty = new Service(new Repository(new Journal(directory.resolve("other.jsonl"))));
            Output.Collected collected = Output.collecting();
            listTowers(args("list"), new CommandContext(empty, collected.output()));
            assertThat(collected.err().get(0)).isEqualTo("no towers yet");
        }

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            listTowers(args("list"), context(collected));
            assertThat(Json.parse(String.join("\n", collected.out()))).hasSize(2);
        }
    }

    @Nested
    class TowerShow {

        @BeforeEach
        void fileOne() {
            addTower(args("--name StMary --place Bridgend " + SIX_BELLS), context(Output.collecting()));
        }

        @Test
        void describesOne() {
            Output.Collected collected = Output.collecting();
            showTower(args("show --id bridgend-stmary"), context(collected));
            assertThat(collected.out().get(0)).contains("StMary, Bridgend");
        }

        @Test
        void printsTheNoteBesideEachBellThatHasOne() {
            JsonNode sent = Json.parse("{\"name\":\"Tuned\",\"bells\":[{\"number\":1,\"weight\":\"4-0-0\"},"
                    + "{\"number\":2,\"weight\":\"5-0-0\",\"note\":\"Eb\"}]}");
            service.putTower("tuned", TowerRoutes.filingDocument(sent, TowerRoutes.towerFrom(sent)));
            Output.Collected collected = Output.collecting();
            showTower(args("show --id tuned"), context(collected));
            assertThat(collected.out().get(0)).contains("tenor 5-0-0 in Eb");
            assertThat(collected.out()).contains("  1  4-0-0", "  2  5-0-0  Eb");
        }

        @Test
        void filesNoKeyForATowerWhoseTenorHasNoNote() {
            assertThat(service.getTower("bridgend-stmary").get("key").isNull()).isTrue();
        }

        @Test
        void complainsAboutOneItDoesNotHold() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> showTower(args("show --id nowhere"), context(collected)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void complainsWhenNoTowerIsNamed() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> showTower(args("show"), context(collected)))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
