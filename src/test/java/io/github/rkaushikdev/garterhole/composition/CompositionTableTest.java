package io.github.rkaushikdev.garterhole.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.CompositionTable.Course;
import io.github.rkaushikdev.garterhole.composition.CompositionTable.Table;
import io.github.rkaushikdev.garterhole.core.RowParse;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompositionTableTest {

    private static final Method PLAIN_BOB_MAJOR = Method.of("Plain", 8, "x18x18x18x18,12");

    private static Table table(String touch) {
        return CompositionTable.of(
                PLAIN_BOB_MAJOR, TouchExpander.expandTouch(PLAIN_BOB_MAJOR, TouchParser.parseTouch(touch)));
    }

    @Nested
    class Courses {

        @Test
        void givesEachBobAtHomeACourseOfItsOwn() {
            Table homes = table("3(H)");
            assertThat(homes.columns()).containsExactly("H");
            assertThat(homes.courses())
                    .containsExactly(
                            new Course("42356", Map.of("H", "-")),
                            new Course("34256", Map.of("H", "-")),
                            new Course("23456", Map.of("H", "-")));
        }

        @Test
        void putsTheColumnsFrontToBack() {
            Table table = table("WWH WWH WWH");
            assertThat(table.columns()).containsExactly("W", "H");
            assertThat(table.courses()).hasSize(6);
            assertThat(table.courses().get(1)).isEqualTo(new Course("26354", Map.of("W", "-", "H", "-")));
        }

        @Test
        void marksASingleWithItsOwnSymbol() {
            assertThat(table("sH sH").courses())
                    .extracting(course -> course.calls().get("H"))
                    .containsExactly("s", "s");
        }

        @Test
        void writesTheWholeRowWhenTheTenorsAreNotHome() {
            assertThat(table("3(M)").courses().get(0).courseHead()).isEqualTo("6472538");
        }

        @Test
        void keepsALastCourseThatNeverCameHome() {
            Table part = table("-.....");
            assertThat(part.courses()).hasSize(1);
            assertThat(part.courses().get(0).calls()).hasSize(1);
        }

        @Test
        void hasNoCoursesForNoLeads() {
            Table none = CompositionTable.of(
                    PLAIN_BOB_MAJOR, new TouchExpander.Rung(PLAIN_BOB_MAJOR.plainLead(), List.of(), false));
            assertThat(none.courses()).isEmpty();
            assertThat(CompositionTable.lines(none)).containsExactly("", "0 courses");
        }
    }

    @Nested
    class Lines {

        @Test
        void printsTheTableUnderItsPositions() {
            assertThat(CompositionTable.lines(table("3(H)")))
                    .containsExactly("       H", "42356  -", "34256  -", "23456  -", "3 courses");
        }

        @Test
        void writesTwoBobsAtOnePositionAsATwo() {
            Course course = CompositionTable.of(
                            PLAIN_BOB_MAJOR,
                            new TouchExpander.Rung(
                                    PLAIN_BOB_MAJOR.plainLead(),
                                    List.of(
                                            new TouchExpander.RungLead(
                                                    "-",
                                                    java.util.Optional.of("W"),
                                                    RowParse.parseRow("13527486"),
                                                    List.of()),
                                            new TouchExpander.RungLead(
                                                    "-",
                                                    java.util.Optional.of("W"),
                                                    RowParse.parseRow("12345678"),
                                                    List.of())),
                                    true))
                    .courses()
                    .get(0);
            assertThat(course.calls()).containsEntry("W", "2");
        }
    }

    @Nested
    class CourseHead {

        @Test
        void leavesOffTheTrebleAndTheTenorsWhenTheyAreHome() {
            assertThat(CompositionTable.courseHead(RowParse.parseRow("13425678")))
                    .isEqualTo("34256");
            assertThat(CompositionTable.courseHead(RowParse.parseRow("12354"))).isEqualTo("2354");
        }
    }

    @Test
    void refusesSomethingItCannotWriteOut() {
        assertThatThrownBy(() -> CompositionTable.of(null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CompositionTable.of(
                        PLAIN_BOB_MAJOR, TouchExpander.expandTouch(PLAIN_BOB_MAJOR, TouchParser.parseTouch("3(H)")), 9))
                .hasMessageContaining("bell 9 is not on 8 bells");
    }
}
