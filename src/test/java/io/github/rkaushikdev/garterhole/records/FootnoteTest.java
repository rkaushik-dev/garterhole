package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.records.Footnote.KINDS;
import static io.github.rkaushikdev.garterhole.records.Footnote.Kind.BIRTHDAY;
import static io.github.rkaushikdev.garterhole.records.Footnote.Kind.FIRST;
import static io.github.rkaushikdev.garterhole.records.Footnote.Kind.MEMORIAL;
import static io.github.rkaushikdev.garterhole.records.Footnote.Kind.OTHER;
import static io.github.rkaushikdev.garterhole.records.Footnote.Kind.WEDDING;
import static io.github.rkaushikdev.garterhole.records.Footnote.firstFootnote;
import static io.github.rkaushikdev.garterhole.records.Footnote.groupByKind;
import static io.github.rkaushikdev.garterhole.records.Footnote.kindOf;
import static io.github.rkaushikdev.garterhole.records.Footnote.namesIn;
import static io.github.rkaushikdev.garterhole.records.Footnote.readAll;
import static io.github.rkaushikdev.garterhole.records.Footnote.readFootnote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Footnote.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FootnoteTest {

    @Nested
    class KindOf {

        @Test
        void recognisesAFirst() {
            assertThat(kindOf("First quarter peal of Minor for Alice Vane.")).isEqualTo(FIRST);
            assertThat(kindOf("1st on eight for Bob Reed.")).isEqualTo(FIRST);
        }

        @Test
        void recognisesABirthday() {
            assertThat(kindOf("Rung for the 80th birthday of Carol Sims.")).isEqualTo(BIRTHDAY);
        }

        @Test
        void recognisesAMemorial() {
            assertThat(kindOf("Rung in memory of Dan Ellis.")).isEqualTo(MEMORIAL);
            assertThat(kindOf("In memoriam.")).isEqualTo(MEMORIAL);
        }

        @Test
        void recognisesAWedding() {
            assertThat(kindOf("Rung for the wedding of Erin and Frank.")).isEqualTo(WEDDING);
        }

        @Test
        void callsAnythingElseOther() {
            assertThat(kindOf("Rung half muffled.")).isEqualTo(OTHER);
        }

        @Test
        void recognisesAMilestone() {
            assertThat(kindOf("100th peal for Bob Hart.")).isEqualTo(Footnote.Kind.MILESTONE);
            assertThat(kindOf("21st Quarter peal together")).isEqualTo(Footnote.Kind.MILESTONE);
            assertThat(kindOf("100 years since the bells were rehung")).isEqualTo(OTHER);
        }

        @Test
        void listsTheKindsItKnows() {
            assertThat(KINDS).contains(FIRST);
            assertThat(FIRST).hasToString("first");
            assertThatThrownBy(() -> KINDS.add(OTHER)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class ReadFootnote {

        @Test
        void keepsTheWordsAndAddsTheStructure() {
            Footnote read = readFootnote("  First quarter peal for Alice Vane.  ");
            assertThat(read.text()).isEqualTo("First quarter peal for Alice Vane.");
            assertThat(read.kind()).isEqualTo(FIRST);
            assertThat(read.names()).contains("Alice Vane");
        }

        @Test
        void refusesABlankFootnote() {
            assertThatThrownBy(() -> readFootnote("")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> readFootnote("   ")).hasMessageContaining("cannot be blank");
        }
    }

    @Nested
    class NamesIn {

        @Test
        void findsANameInTheMiddleOfASentence() {
            assertThat(namesIn("Rung in memory of Dan Ellis.")).containsExactly("Dan Ellis");
        }

        @Test
        void findsTwoNames() {
            assertThat(namesIn("Rung for the wedding of Erin Fox and Frank Gale."))
                    .containsExactly("Erin Fox", "Frank Gale");
        }

        @Test
        void doesNotTakeTheFirstWordOfASentenceForAName() {
            assertThat(namesIn("Rung half muffled.")).isEmpty();
        }

        @Test
        void findsNothingWhereThereIsNothing() {
            assertThat(namesIn("")).isEmpty();
        }

        @Test
        void keepsAnApostropheInAName() {
            assertThat(namesIn("Rung for Sarah O'Brien.")).containsExactly("Sarah O'Brien");
        }
    }

    @Nested
    class ReadAll {

        @Test
        void readsEveryFootnote() {
            List<Footnote> read = readAll(List.of("First quarter for Alice.", "Rung half muffled."));
            assertThat(read).hasSize(2);
            assertThat(read).extracting(Footnote::kind).containsExactly(FIRST, OTHER);
        }

        @Test
        void readsNothingFromNothing() {
            assertThat(readAll(List.of())).isEmpty();
        }
    }

    @Nested
    class FirstFootnote {

        @Test
        void writesTheSentenceAFormWouldOffer() {
            assertThat(firstFootnote("Alice Vane", "quarter peal of Minor"))
                    .isEqualTo("First quarter peal of Minor for Alice Vane.");
        }

        @Test
        void isRecognisedByTheReaderItWasWrittenFor() {
            assertThat(kindOf(firstFootnote("Alice", "peal"))).isEqualTo(FIRST);
        }

        @Test
        void refusesAFirstWithSomethingMissing() {
            assertThatThrownBy(() -> firstFootnote("", "peal")).hasMessageContaining("needs somebody and something");
            assertThatThrownBy(() -> firstFootnote("Alice", "")).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class GroupByKind {

        @Test
        void writesAMilestoneTheWayABoardDoes() {
            assertThat(Footnote.milestoneFootnote("Bob Hart", 100, "peal")).isEqualTo("100th peal for Bob Hart.");
            assertThat(kindOf(Footnote.milestoneFootnote("Bob", 250, "quarter peal")))
                    .isEqualTo(Footnote.Kind.MILESTONE);
            assertThatThrownBy(() -> Footnote.milestoneFootnote("Bob", 0, "peal"))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Footnote.milestoneFootnote(" ", 5, "peal"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void writesOrdinalsWithTheTeensLeftAlone() {
            assertThat(List.of(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111, 112))
                    .extracting(Footnote::ordinal)
                    .containsExactly(
                            "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "101st",
                            "111th", "112th");
        }

        @Test
        void putsTheFirstsTogether() {
            Map<Kind, List<String>> groups =
                    groupByKind(List.of("First quarter for Alice.", "First peal for Bob.", "Rung half muffled."));
            assertThat(groups.get(FIRST)).hasSize(2);
            assertThat(groups.get(OTHER)).hasSize(1);
        }

        @Test
        void givesAnEmptyMapForNothing() {
            assertThat(groupByKind(List.of())).isEmpty();
        }
    }
}
