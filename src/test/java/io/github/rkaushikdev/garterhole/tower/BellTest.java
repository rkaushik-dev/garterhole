package io.github.rkaushikdev.garterhole.tower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.Bell.Details;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BellTest {

    @Nested
    class Construction {

        @Test
        void keepsWhatItWasGiven() {
            Bell bell = new Bell(8, 2643, Details.NONE.withNote("D").withFounder("Taylor"));
            assertThat(bell.number()).isEqualTo(8);
            assertThat(bell.weightPounds()).isEqualTo(2643);
            assertThat(bell.note()).hasValue("D");
            assertThat(bell.founder()).hasValue("Taylor");
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(Bell.class.getModifiers())).isTrue();
            assertThat(Arrays.stream(Bell.class.getDeclaredFields())
                            .filter(field -> !Modifier.isStatic(field.getModifiers()))
                            .map(Field::getModifiers))
                    .allMatch(Modifier::isFinal);
        }

        @Test
        void defaultsTheDetailsItWasNotGiven() {
            Bell bell = new Bell(1, 400);
            assertThat(bell.note()).isEmpty();
            assertThat(bell.founder()).isEmpty();
            assertThat(bell.cast()).isEmpty();
        }

        @Test
        void refusesADefinitionThatIsNotAnObject() {
            assertThatThrownBy(() -> new Bell(1, 400, null)).hasMessageContaining("needs a definition object");
        }

        @Test
        void refusesABellNumberThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> new Bell(0, 400)).hasMessageContaining("is not a bell number");
            assertThatThrownBy(() -> new Bell(-2, 400)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAWeightThatIsNotPositive() {
            assertThatThrownBy(() -> new Bell(1, 0)).hasMessageContaining("cannot weigh 0 pounds");
        }

        @Test
        void refusesANoteThatIsNotANote() {
            assertThatThrownBy(() -> new Bell(1, 400, Details.NONE.withNote("H")))
                    .hasMessageContaining("is not a note");
        }

        @Test
        void refusesACastingYearOutsideTheRangeBellsWereCastIn() {
            assertThatThrownBy(() -> new Bell(1, 400, Details.NONE.withCast(1000)))
                    .hasMessageContaining("is not a year a bell was cast in");
        }
    }

    @Nested
    class Weight {

        @Test
        void breaksPoundsIntoHundredweightQuartersAndPounds() {
            assertThat(new Bell(8, 2643).weight()).isEqualTo(new Bell.Weight(23, 2, 11));
        }

        @Test
        void writesTheWeightTheWayATowerBoardDoes() {
            assertThat(new Bell(8, 2643).weightText()).isEqualTo("23-2-11");
        }

        @Test
        void handlesAnExactHundredweight() {
            assertThat(new Bell(1, Bell.POUNDS_PER_HUNDREDWEIGHT * 4).weightText())
                    .isEqualTo("4-0-0");
        }

        @Test
        void handlesAWeightUnderAHundredweight() {
            assertThat(new Bell(1, Bell.POUNDS_PER_QUARTER * 3).weightText()).isEqualTo("0-3-0");
        }

        @Test
        void printsItself() {
            assertThat(new Bell(8, 2643, Details.NONE.withNote("D"))).hasToString("bell 8, 23-2-11 in D");
            assertThat(new Bell(1, 448)).hasToString("bell 1, 4-0-0");
        }
    }

    @Nested
    class Weighing {

        @Test
        void readsAWeightOffATowerBoard() {
            Bell bell = Bell.weighing(8, "23-2-11");
            assertThat(bell.weightPounds()).isEqualTo(2643);
            assertThat(bell.weightText()).isEqualTo("23-2-11");
        }

        @Test
        void takesTheOtherDetailsAsWell() {
            Bell bell = Bell.weighing(8, "23-2-11", Details.NONE.withNote("D").withCast(1901));
            assertThat(bell.note()).hasValue("D");
            assertThat(bell.cast()).hasValue(1901);
        }

        @Test
        void roundTripsEveryWeightItCanRead() {
            for (String weight : List.of("0-1-1", "4-0-0", "12-3-27", "23-2-11")) {
                assertThat(Bell.weighing(1, weight).weightText()).isEqualTo(weight);
            }
        }

        @Test
        void refusesAWeightWithTheWrongNumberOfParts() {
            assertThatThrownBy(() -> Bell.weighing(1, "23-2")).hasMessageContaining("is not a weight in hundredweight");
            assertThatThrownBy(() -> Bell.weighing(1, ""))
                    .hasMessageContaining("a bell weight must be a non empty string");
        }

        @Test
        void refusesAPartThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> Bell.weighing(1, "23-2-x"))
                    .hasMessageContaining("has a part that is not a whole number");
            assertThatThrownBy(() -> Bell.weighing(1, "23--1-2")).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesMoreQuartersOrPoundsThanFit() {
            assertThatThrownBy(() -> Bell.weighing(1, "23-4-0")).hasMessageContaining("is more than a hundredweight");
            assertThatThrownBy(() -> Bell.weighing(1, "23-2-28")).hasMessageContaining("is more than a quarter");
        }
    }

    @Nested
    class Kilograms {

        @Test
        void readsAWeightInKilogramsAndGivesItBackInBoth() {
            Bell tenor = Bell.weighingKilograms(8, 1199, Details.NONE);
            assertThat(tenor.weightKilograms()).isEqualTo(1199);
            assertThat(tenor.weightText()).isEqualTo("23-2-11");
        }

        @Test
        void convertsABoardWeightToTheNearestKilogram() {
            assertThat(Bell.weighing(1, "4-0-0").weightKilograms()).isEqualTo(203);
        }

        @Test
        void refusesAWeightThatIsNotOne() {
            assertThatThrownBy(() -> Bell.weighingKilograms(1, 0, Details.NONE))
                    .hasMessageContaining("cannot weigh 0 kilograms");
            assertThatThrownBy(() -> Bell.weighingKilograms(1, Double.NaN, Details.NONE))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Notes {

        @Test
        void listsTheTwelveNotes() {
            assertThat(Bell.notes()).hasSize(12);
            assertThat(Bell.notes()).contains("C");
            assertThat(Bell.notes()).contains("A#");
        }

        @Test
        void readsAFlatAsTheSameNoteAsTheSharp() {
            Bell flat = new Bell(8, 2643, Details.NONE.withNote("Eb"));
            Bell sharp = new Bell(8, 2643, Details.NONE.withNote("D#"));
            assertThat(flat.pitch()).isEqualTo(sharp.pitch()).contains(Note.D_SHARP);
        }

        @Test
        void keepsTheSpellingTheBoardUses() {
            Bell bell = new Bell(1, 400, Details.NONE.withNote("bb"));
            assertThat(bell.note()).hasValue("Bb");
            assertThat(bell).hasToString("bell 1, 3-2-8 in Bb");
            assertThat(new Bell(1, 400).pitch()).isEmpty();
        }

        @Test
        void handsBackACopy() {
            List<String> first = Bell.notes();
            first.add("H");
            assertThat(Bell.notes()).hasSize(12);
        }
    }
}
