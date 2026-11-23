package io.github.rkaushikdev.garterhole.composition.music;

import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.compareMusic;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.countRule;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.defaultScheme;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.musicSummary;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.scoreMusic;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.MusicSummary;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.NamedCount;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.Score;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.ScoreLine;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stroke;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MusicScorerTest {

    private static List<Row> musical() {
        return parseRows("42135678 12346857 13527468 87654321 13572468");
    }

    private static List<Row> dull() {
        return parseRows("13527468 24681357");
    }

    @Nested
    class DefaultScheme {

        @Test
        void weightsLongerRunsMore() {
            List<MusicRule> scheme = defaultScheme(8);
            MusicRule four = scheme.stream()
                    .filter(rule -> rule.runLength().equals(OptionalInt.of(4)))
                    .findFirst()
                    .orElseThrow();
            MusicRule six = scheme.stream()
                    .filter(rule -> rule.runLength().equals(OptionalInt.of(6)))
                    .findFirst()
                    .orElseThrow();
            assertThat(six.weight().getAsDouble()).isGreaterThan(four.weight().getAsDouble());
        }

        @Test
        void leavesOutRunsLongerThanTheStage() {
            assertThat(defaultScheme(5)).noneMatch(rule -> rule.runLength().equals(OptionalInt.of(6)));
        }

        @Test
        void refusesAStageWithNoScheme() {
            assertThatThrownBy(() -> defaultScheme(3)).hasMessageContaining("no default music scheme on 3");
        }
    }

    @Nested
    class ScoreMusic {

        @Test
        void addsTheWeightedCountsUp() {
            Score scored = scoreMusic(musical(), 8);
            assertThat(scored.total()).isGreaterThan(0);
            assertThat(scored.lines()).extracting(ScoreLine::name).contains("four bell runs");
        }

        @Test
        void givesEveryLineItsCountWeightAndScore() {
            ScoreLine line = scoreMusic(musical(), 8).lines().stream()
                    .filter(entry -> entry.name().equals("four bell runs"))
                    .findFirst()
                    .orElseThrow();
            assertThat(line.score()).isEqualTo(line.count() * line.weight());
        }

        @Test
        void scoresAnUnmusicalBlockLow() {
            assertThat(scoreMusic(dull(), 8).total()).isZero();
        }

        @Test
        void scoresAnEmptyBlockZero() {
            assertThat(scoreMusic(List.of(), 8).total()).isZero();
        }

        @Test
        void takesASchemeOfItsOwn() {
            List<MusicRule> scheme = List.of(MusicRule.pattern("tenors together", "*78", 10));
            Score scored = scoreMusic(musical(), 8, scheme);
            assertThat(scored.lines()).hasSize(1);
            assertThat(scored.total()).isEqualTo(scored.lines().get(0).count() * 10.0);
        }
    }

    @Nested
    class CountingByStroke {

        /** Rounds at backstroke, then a handstroke and a backstroke with the tenors reversed. */
        private final List<Row> rows = parseRows("12345678 12345687 21436587 12345678");

        @Test
        void splitsTheRowsByStrokeFromTheBackstrokeRounds() {
            assertThat(MusicScorer.atStroke(rows, Stroke.HANDSTROKE))
                    .containsExactlyElementsOf(parseRows("12345687 12345678"));
            assertThat(MusicScorer.atStroke(rows, Stroke.BACKSTROKE))
                    .containsExactlyElementsOf(parseRows("12345678 21436587"));
        }

        @Test
        void countsARuleOnlyAtItsStroke() {
            MusicRule reversed = MusicRule.pattern("87s", "*87", 1);
            assertThat(countRule(rows, reversed)).isEqualTo(2);
            assertThat(countRule(rows, reversed.atStroke(Stroke.BACKSTROKE))).isEqualTo(1);
            assertThat(countRule(rows, reversed.atStroke(Stroke.HANDSTROKE))).isEqualTo(1);
            assertThat(reversed.atStroke(Stroke.BACKSTROKE).stroke()).contains(Stroke.BACKSTROKE);
            assertThat(reversed.stroke()).isEmpty();
        }
    }

    @Nested
    class CompositionScheme {

        @Test
        void addsTheRollUpsOnMajorAndThePenaltyForTheTenorsReversed() {
            List<MusicRule> scheme = MusicScorer.compositionScheme(8);
            assertThat(scheme).containsAll(defaultScheme(8));
            assertThat(scheme)
                    .extracting(MusicRule::name)
                    .contains("roll up 4578", "roll up 5478", "roll up 4678", "roll up 6478", "87 at backstroke");
        }

        @Test
        void leavesTheRollUpsOffAnyOtherStage() {
            assertThat(MusicScorer.compositionScheme(10))
                    .extracting(MusicRule::name)
                    .noneMatch(name -> name.startsWith("roll up"))
                    .contains("09 at backstroke");
        }

        @Test
        void scoresARollUpAndTakesOffForTheTenorsReversedAtBackstroke() {
            List<Row> rows = parseRows("12345678 31264578 13246587 12345678");
            Score scored = scoreMusic(rows, 8, MusicScorer.compositionScheme(8));
            assertThat(scored.lines())
                    .filteredOn(line -> line.name().equals("roll up 4578"))
                    .extracting(ScoreLine::count)
                    .containsExactly(1);
            assertThat(scored.lines())
                    .filteredOn(line -> line.name().equals("87 at backstroke"))
                    .extracting(ScoreLine::score)
                    .containsExactly(-1.0);
        }

        @Test
        void refusesAStageTooSmallForIt() {
            assertThatThrownBy(() -> MusicScorer.compositionScheme(5)).hasMessageContaining("no composition scheme");
        }
    }

    @Nested
    class CountRule {

        private final List<Row> rows = musical();

        @Test
        void countsAPattern() {
            assertThat(countRule(rows, MusicRule.pattern("back", "*5678", 1))).isEqualTo(1);
        }

        @Test
        void countsARunLength() {
            assertThat(countRule(rows, MusicRule.runs("runs", 4, 1))).isGreaterThan(0);
        }

        @Test
        void countsANamedRow() {
            assertThat(countRule(rows, MusicRule.namedRow("queens", "Queens", 1)))
                    .isEqualTo(1);
            assertThat(countRule(rows, MusicRule.namedRow("backrounds", "Backrounds", 1)))
                    .isEqualTo(1);
        }

        @Test
        void refusesARuleWithNothingToCount() {
            MusicRule empty = new MusicRule(
                    "empty", OptionalDouble.of(1), Optional.empty(), OptionalInt.empty(), Optional.empty());
            assertThatThrownBy(() -> countRule(rows, empty)).hasMessageContaining("has nothing to count");
        }

        @Test
        void refusesARuleWithNoWeight() {
            MusicRule unweighted = new MusicRule(
                    "unweighted", OptionalDouble.empty(), Optional.of("*"), OptionalInt.empty(), Optional.empty());
            assertThatThrownBy(() -> countRule(rows, unweighted)).hasMessageContaining("has no weight");
        }

        @Test
        void refusesSomethingThatIsNotARule() {
            assertThatThrownBy(() -> countRule(rows, null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class MusicSummaryOfABlock {

        @Test
        void reportsTheScoreTheRunsAndTheNamedRows() {
            MusicSummary summary = musicSummary(musical(), 8);
            assertThat(summary.score()).isGreaterThan(0);
            assertThat(summary.runs().get(0).length()).isEqualTo(4);
            assertThat(summary.named().stream().map(NamedCount::name).sorted()).containsExactly("Backrounds", "Queens");
        }

        @Test
        void leavesRoundsOutOfTheNamedRows() {
            assertThat(musicSummary(parseRows("12345678"), 8).named()).isEmpty();
        }

        @Test
        void summarisesAWholePlainCourse() {
            MusicSummary summary = musicSummary(PlainCourse.plainCourse(Method.of("Plain", 8, "x18x18x18x18,12")), 8);
            assertThat(summary.runs()).hasSize(5);
            assertThat(summary.score()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class CompareMusic {

        @Test
        void isPositiveWhenTheFirstBlockIsMoreMusical() {
            assertThat(compareMusic(musical(), dull(), 8)).isGreaterThan(0);
        }

        @Test
        void isZeroForTwoBlocksThatScoreTheSame() {
            assertThat(compareMusic(dull(), dull(), 8)).isZero();
        }
    }
}
