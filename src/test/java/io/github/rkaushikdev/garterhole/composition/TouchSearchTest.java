package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.TouchSearch.MAX_DEPTH;
import static io.github.rkaushikdev.garterhole.composition.TouchSearch.searchTouches;
import static io.github.rkaushikdev.garterhole.composition.TouchSearch.shortest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TouchSearch.FoundTouch;
import io.github.rkaushikdev.garterhole.composition.TouchSearch.Request;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TouchSearchTest {

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static List<FoundTouch> search(Method method, int maxLeads, int limit) {
        return searchTouches(Request.of(method).withMaxLeads(maxLeads).withLimit(limit));
    }

    @Nested
    class KeepingTheTenorsTogether {

        private List<FoundTouch> together(Method method, int maxLeads, int limit) {
            return searchTouches(
                    Request.of(method).withMaxLeads(maxLeads).withLimit(limit).keepingTenorsTogether());
        }

        @Test
        void findsOnlyTouchesWhoseEveryLeadHeadHasTheTenorsCoursing() {
            Method bob = plainBobMajor();
            List<FoundTouch> found = together(bob, 14, 10);
            assertThat(found).isNotEmpty();
            for (FoundTouch touch : found) {
                TouchAnalysis analysis = Analysis.analyseTouch(bob, touch.calling());
                assertThat(analysis.tenors()).hasValueSatisfying(tenors -> assertThat(tenors.together())
                        .isTrue());
                assertThat(analysis.proof().isTrue()).isTrue();
            }
        }

        @Test
        void findsTouchesAnUnrestrictedSearchAlsoFindsButFewerOfThem() {
            List<FoundTouch> any = search(plainBobMinor(), 10, 200);
            List<FoundTouch> together = together(plainBobMinor(), 10, 200);
            assertThat(any).containsAll(together);
            assertThat(together.size()).isLessThan(any.size());
        }

        @Test
        void startsFromTheDefaultsWithTheTenorsAllowedToPart() {
            assertThat(Request.of(plainBobMinor()).tenorsTogether()).isFalse();
            assertThat(Request.of(plainBobMinor()).keepingTenorsTogether().tenorsTogether())
                    .isTrue();
        }

        @Test
        void refusesAMethodWithoutTheTrebleHunting() {
            Method stedman = Method.of("Stedman", 7, "3.1.7.3.1.3,1");
            assertThatThrownBy(() -> together(stedman, 4, 1)).hasMessageContaining("treble dominated");
            assertThatThrownBy(() -> together(Method.of("Plain", 5, "5.1.5.1.5,125"), 4, 1))
                    .hasMessageContaining("on six or more");
        }
    }

    @Nested
    class SearchTouches {

        @Test
        void findsTouchesOfPlainBobMinor() {
            assertThat(search(plainBobMinor(), 8, 8)).isNotEmpty();
        }

        @Test
        void findsThePlainCourseFirstSinceItIsTheShortest() {
            FoundTouch best = shortest(search(plainBobMinor(), 8, 8)).orElseThrow();
            assertThat(best.calling()).isEqualTo(".....");
            assertThat(best.changes()).isEqualTo(60);
        }

        @Test
        void reportsTheLengthInChangesAndInLeads() {
            for (FoundTouch touch : search(plainBobMinor(), 8, 4)) {
                assertThat(touch.changes()).isEqualTo(touch.leads() * 12);
            }
        }

        @Test
        void onlyReportsTouchesThatComeRoundAndAreTrue() {
            Method bob = plainBobMinor();
            for (FoundTouch touch : search(bob, 8, 8)) {
                Rung rung = TouchExpander.expandTouch(bob, TouchParser.parseTouch(touch.calling()));
                assertThat(rung.comesRound()).isTrue();
                assertThat(Prover.prove(rung.block()).isTrue()).isTrue();
            }
        }

        @Test
        void stopsAtTheNumberOfTouchesItWasAskedFor() {
            assertThat(search(plainBobMinor(), 8, 3)).hasSize(3);
        }

        @Test
        void keepsOnlyTheLengthsItWasAskedFor() {
            List<FoundTouch> found = searchTouches(
                    Request.of(plainBobMinor()).withMaxLeads(8).withLimit(10).withLengths(List.of(72)));
            assertThat(found).isNotEmpty();
            for (FoundTouch touch : found) {
                assertThat(touch.changes()).isEqualTo(72);
            }
        }

        @Test
        void findsNothingWhenTheSearchIsTooShallow() {
            assertThat(search(plainBobMajor(), 3, 5)).isEmpty();
        }

        @Test
        void carriesTheNotationWithEachTouch() {
            assertThat(search(plainBobMinor(), 6, 1).get(0).notation()).isEqualTo("x16x16x16x16x16x12");
        }

        @Test
        void refusesASearchItCannotRun() {
            assertThatThrownBy(() -> searchTouches(Request.of(null))).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> searchTouches(Request.of(plainBobMinor()).withMaxLeads(0)))
                    .hasMessageContaining("outside 1..60");
            assertThatThrownBy(() -> searchTouches(Request.of(plainBobMinor()).withMaxLeads(MAX_DEPTH + 1)))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> searchTouches(Request.of(plainBobMinor()).withLimit(0)))
                    .hasMessageContaining("is not a search");
        }
    }

    @Nested
    class Shortest {

        @Test
        void findsTheShortestOfSeveral() {
            List<FoundTouch> found = List.of(
                    new FoundTouch("a", 120, 10, "x"),
                    new FoundTouch("b", 60, 5, "x"),
                    new FoundTouch("c", 180, 15, "x"));
            assertThat(shortest(found).orElseThrow().calling()).isEqualTo("b");
        }

        @Test
        void givesNothingWhenNothingWasFound() {
            assertThat(shortest(List.of())).isEmpty();
        }
    }
}
