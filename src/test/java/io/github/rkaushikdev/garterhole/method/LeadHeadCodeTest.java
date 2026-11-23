package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.LeadHeadCode.codeOf;
import static io.github.rkaushikdev.garterhole.method.LeadHeadCode.firstLeadHead;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowParse;
import io.github.rkaushikdev.garterhole.core.Transposition;
import io.github.rkaushikdev.garterhole.method.LeadHeadCode.Family;
import io.github.rkaushikdev.garterhole.notation.NotationParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeadHeadCodeTest {

    @Nested
    class TheMethodsEveryBandKnows {

        @Test
        void givesPlainBobTheFirstLetter() {
            assertThat(codeOf(Method.of("Plain Bob", 6, "x16x16x16,12"))).contains("a");
            assertThat(codeOf(Method.of("Plain Bob", 8, "x18x18x18x18,12"))).contains("a");
        }

        @Test
        void givesCambridgeAndYorkshireMajorTheSecond() {
            assertThat(codeOf(Method.of("Cambridge Surprise", 8, "x38x14x1258x36x14x58x16x78,12")))
                    .contains("b");
            assertThat(codeOf(Method.of("Yorkshire Surprise", 8, "x38x14x58x16x12x38x14x78,12")))
                    .contains("b");
        }

        @Test
        void putsBristolInTheEighthsPlaceGroup() {
            assertThat(codeOf(Method.of("Bristol Surprise", 8, "x58x14.58x58.36.14x14.58x14x18,18")))
                    .contains("m");
        }

        @Test
        void readsAnOddStageByItsLeadEnd() {
            assertThat(codeOf(Method.of("Plain Bob", 5, "5.1.5.1.5,125"))).contains("p");
            assertThat(codeOf(Method.of("Plain Bob", 7, "7.1.7.1.7.1.7,127"))).contains("p");
        }

        @Test
        void givesGrandsireItsOwnFamily() {
            assertThat(codeOf(Method.of("Grandsire", 5, "3,1.5.1.5.1"))).contains("a");
            assertThat(codeOf(Method.of("Grandsire", 7, "3,1.7.1.7.1.7.1"))).contains("a");
        }
    }

    @Nested
    class MethodsWithNoCode {

        @Test
        void leavesAPrincipleOut() {
            assertThat(codeOf(Method.of("Stedman", 7, "3.1.7.3.1.3,1"))).isEmpty();
        }

        @Test
        void leavesOutALeadEndChangeTheCodesDoNotCover() {
            assertThat(codeOf(Method.of("Odd End", 6, "x16x16x16,14"))).isEmpty();
        }

        @Test
        void leavesOutTheFourBellStage() {
            assertThat(codeOf(Method.of("Plain Bob", 4, "x14x14,12"))).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> codeOf(null)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class TheTables {

        /** Every Plain Bob lead head at a stage, in course order, rung by repeated leads. */
        private List<Row> course(Family family, int stage) {
            Row first = firstLeadHead(family, stage);
            List<Row> heads = new ArrayList<>();
            for (int power = 1; power < Transposition.order(first); power += 1) {
                heads.add(Transposition.power(first, power));
            }
            return heads;
        }

        @Test
        void startsThePlainBobCourseWhereALeadOfPlainBobFinishes() {
            assertThat(firstLeadHead(Family.PLAIN_BOB, 8)).hasToString("13527486");
            assertThat(firstLeadHead(Family.PLAIN_BOB, 5)).hasToString("13524");
            assertThat(firstLeadHead(Family.GRANDSIRE, 8)).hasToString("12537486");
        }

        @Test
        void refusesAStageWithNoCodes() {
            assertThatThrownBy(() -> firstLeadHead(Family.PLAIN_BOB, 4)).hasMessageContaining("five bells");
            assertThatThrownBy(() -> firstLeadHead(Family.PLAIN_BOB, 17)).hasMessageContaining("five bells");
        }

        @ParameterizedTest(name = "{0} lead heads at stage {1}, lead end {2}: {3}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "PLAIN_BOB|6|12|a b e f",
                    "PLAIN_BOB|6|16|g h l m",
                    "PLAIN_BOB|8|12|a b c d e f",
                    "PLAIN_BOB|8|18|g h j k l m",
                    "PLAIN_BOB|10|12|a b c c1 d1 d e f",
                    "PLAIN_BOB|10|10|g h j j1 k1 k l m",
                    "PLAIN_BOB|5|125|p p1 q",
                    "PLAIN_BOB|5|1|r r1 s",
                    "PLAIN_BOB|7|127|p p1 p2 q1 q",
                    "PLAIN_BOB|9|129|p p1 p2 p3 q2 q1 q",
                    "GRANDSIRE|7|3|a b e f",
                    "GRANDSIRE|7|7|g h l m",
                    "GRANDSIRE|5|3|a f",
                    "GRANDSIRE|6|36|p q1 q",
                    "GRANDSIRE|6|x|r s1 s",
                    "GRANDSIRE|8|38|p p1 q2 q1 q",
                })
        void lettersEveryLeadHeadOfTheCourseInTurn(Family family, int stage, String change, String codes) {
            List<String> found = new ArrayList<>();
            for (Row head : course(family, stage)) {
                found.add(LeadHeadCode.codeFor(
                                family,
                                head,
                                NotationParser.parseChanges(change, stage).get(0))
                        .orElse("none"));
            }
            assertThat(String.join(" ", found)).isEqualTo(codes);
        }

        @Test
        void agreesWithThePrintedRoyalRows() {
            assertThat(LeadHeadCode.codeFor(Family.PLAIN_BOB, RowParse.parseRow("1907856342"), Change.of(10, 1, 2)))
                    .contains("c1");
            assertThat(LeadHeadCode.codeFor(Family.PLAIN_BOB, RowParse.parseRow("1089674523"), Change.of(10, 1, 10)))
                    .contains("k1");
        }

        @Test
        void hasNoCodeForALeadHeadOutsideTheCourse() {
            assertThat(LeadHeadCode.codeFor(Family.PLAIN_BOB, RowParse.parseRow("12345678"), Change.of(8, 1, 2)))
                    .isEmpty();
            assertThat(LeadHeadCode.codeFor(Family.PLAIN_BOB, RowParse.parseRow("13254768"), Change.of(8, 1, 2)))
                    .isEmpty();
        }

        @Test
        void refusesAChangeAtAnotherStage() {
            assertThatThrownBy(() ->
                            LeadHeadCode.codeFor(Family.PLAIN_BOB, RowParse.parseRow("13527486"), Change.of(6, 1, 2)))
                    .hasMessageContaining("cannot decide");
        }
    }
}
