package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.Analysis.analyseTouch;
import static io.github.rkaushikdev.garterhole.composition.Analysis.callCounts;
import static io.github.rkaushikdev.garterhole.composition.Analysis.checkClaim;
import static io.github.rkaushikdev.garterhole.composition.Analysis.reportLines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Analysis.Claim;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.CallCount;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.PartsFound;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.TenorsFound;
import io.github.rkaushikdev.garterhole.core.RowParse;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnalysisTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    @Nested
    class AnalyseTouch {

        @Test
        void reportsTheLengthAndWhatItCountsAs() {
            TouchAnalysis analysis = analyseTouch(plainBobMajor(), "3(H)");
            assertThat(analysis.length()).isEqualTo(336);
            assertThat(analysis.kind()).isEqualTo(Lengths.Kind.TOUCH);
            assertThat(analysis.comesRound()).isTrue();
        }

        @Test
        void titlesTheMethodFromItsChanges() {
            assertThat(analyseTouch(plainBobMajor(), "3(H)").title()).isEqualTo("Plain Bob Major");
        }

        @Test
        void provesTheTouch() {
            TouchAnalysis analysis = analyseTouch(plainBobMajor(), "3(H)");
            assertThat(analysis.proof().isTrue()).isTrue();
            assertThat(analysis.summary()).isEqualTo("true, 336 rows");
        }

        @Test
        void countsTheCalls() {
            assertThat(analyseTouch(plainBobMajor(), "3(H)").calls()).containsExactly(new CallCount("-", 3));
        }

        @Test
        void findsThePartStructure() {
            TouchAnalysis analysis = analyseTouch(plainBobMajor(), "3(H)");
            assertThat(analysis.parts()).hasValue(new PartsFound(3, 7, "14235678"));
            assertThat(analysis.partsJson())
                    .isEqualTo(Json.parse("{\"parts\":3,\"leadsPerPart\":7,\"partEnd\":\"14235678\"}"));
        }

        @Test
        void summarisesTheMusic() {
            TouchAnalysis analysis = analyseTouch(plainBobMajor(), "3(H)");
            assertThat(analysis.music().score()).isGreaterThan(0);
            assertThat(analysis.music().runs().get(0).length()).isEqualTo(4);
        }

        @Test
        void reportsATouchThatDoesNotComeRound() {
            assertThat(analyseTouch(plainBobMinor(), "-.-.-.").comesRound()).isFalse();
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> analyseTouch(null, "3(H)")).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesATouchThatIsNotWrittenOut() {
            assertThatThrownBy(() -> analyseTouch(plainBobMinor(), null))
                    .hasMessageContaining("must be written out as a string");
        }
    }

    @Nested
    class CheckClaim {

        private TouchAnalysis analysis() {
            return analyseTouch(plainBobMajor(), "3(H)");
        }

        @Test
        void findsNothingWrongWithAnHonestClaim() {
            assertThat(checkClaim(analysis(), Claim.ofLength(336, 8))).isEmpty();
        }

        @Test
        void catchesALengthThatDoesNotMatch() {
            assertThat(checkClaim(analysis(), Claim.ofLength(1280, 8)))
                    .containsExactly("the calling gives 336 changes, not the 1280 claimed");
        }

        @Test
        void catchesATouchClaimedAsAQuarterThatIsTooShort() {
            assertThat(checkClaim(analysis(), Claim.ofKind(Lengths.Kind.QUARTER_PEAL, 8))
                            .get(0))
                    .containsPattern("needs at least 1250 changes");
        }

        @Test
        void catchesATouchThatDoesNotComeRound() {
            TouchAnalysis tooShort = analyseTouch(plainBobMinor(), "-.-.-.");
            assertThat(checkClaim(tooShort, Claim.atStage(6))).contains("the touch does not come round");
        }
    }

    @Nested
    class CallCounts {

        @Test
        void countsEachSymbolAndLeavesThePlainsOut() {
            assertThat(callCounts(List.of("-", ".", "s", "-")))
                    .containsExactly(new CallCount("-", 2), new CallCount("s", 1));
        }

        @Test
        void countsNothingForAPlainCourse() {
            assertThat(callCounts(List.of(".", "."))).isEmpty();
        }
    }

    @Nested
    class TenorsTogether {

        @Test
        void watchesTheBackTwoUpToMajorAndTheBellsAboveTheSixthBeyond() {
            assertThat(Analysis.tenorCount(6)).isEqualTo(2);
            assertThat(Analysis.tenorCount(8)).isEqualTo(2);
            assertThat(Analysis.tenorCount(10)).isEqualTo(4);
            assertThat(Analysis.tenorCount(12)).isEqualTo(6);
        }

        @Test
        void findsThemTogetherThroughABobCourse() {
            assertThat(analyseTouch(plainBobMajor(), "3(H)").tenors()).contains(new TenorsFound(2, true, List.of()));
        }

        @Test
        void namesTheLeadWhoseLeadHeadPartedThem() {
            assertThat(analyseTouch(plainBobMajor(), "..-").tenors()).contains(new TenorsFound(2, false, List.of(3)));
            assertThat(reportLines(analyseTouch(plainBobMajor(), "..-"))).contains("tenors parted at 1 lead head");
        }

        @Test
        void readsNothingWhenATrebleIsNotLeadingOrTheStageIsSmall() {
            assertThat(Analysis.tenorsFound(List.of(RowParse.parseRow("21345678")), 8))
                    .isEmpty();
            assertThat(Analysis.tenorsFound(List.of(RowParse.parseRow("13524")), 5))
                    .isEmpty();
        }
    }

    @Nested
    class ReportLines {

        @Test
        void writesTheReportOut() {
            List<String> lines = reportLines(analyseTouch(plainBobMajor(), "3(H)"));
            assertThat(lines.get(0)).isEqualTo("Plain Bob Major");
            assertThat(lines.get(1)).isEqualTo("336 changes, touch");
            assertThat(lines.get(2)).isEqualTo("true, 336 rows");
            assertThat(lines).contains("3 parts of 7 leads, part end 14235678");
            assertThat(lines).contains("calls: 3-");
            assertThat(lines).contains("tenors together throughout");
        }

        @Test
        void saysSoWhenThereAreNoCalls() {
            assertThat(reportLines(analyseTouch(plainBobMinor(), "....."))).contains("no calls");
        }
    }
}
